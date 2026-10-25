'use strict';

const assert = require('assert');
const {
  TripCancellationError,
  cancelTripCore,
  notificationIdFor,
} = require('../src/tripCancellationCore');

const TRIP_ID = 'trip-cancel-test';
const CREATOR_UID = 'creator-uid';
const NOW = 1_800_000_000_000;

class FakeDocumentReference {
  constructor(firestore, collection, id) {
    this.firestore = firestore;
    this.collection = collection;
    this.id = id;
  }
}

class FakeCollectionReference {
  constructor(firestore, name) {
    this.firestore = firestore;
    this.name = name;
  }

  doc(id) {
    return new FakeDocumentReference(this.firestore, this.name, id);
  }

  where(field, operator, value) {
    return { collection: this.name, field, operator, value };
  }
}

class FakeSnapshot {
  constructor(id, ref, data) {
    this.id = id;
    this.ref = ref;
    this.value = data;
    this.exists = data !== undefined;
  }

  data() { return this.value; }
  get(field) { return this.value && this.value[field]; }
}

class FakeFirestore {
  constructor(trip, requests) {
    this.rows = {
      trips: new Map([[TRIP_ID, { ...trip }]]),
      rideRequests: new Map(requests.map((request) => [request.id, { ...request.data }])),
      tripNotifications: new Map(),
    };
  }

  collection(name) { return new FakeCollectionReference(this, name); }

  async runTransaction(callback) {
    const writes = [];
    const transaction = {
      get: async (target) => {
        if (target.collection && target.field) {
          const docs = [];
          for (const [id, data] of this.rows[target.collection]) {
            if (data[target.field] === target.value) {
              docs.push(new FakeSnapshot(id, this.collection(target.collection).doc(id), data));
            }
          }
          return { docs };
        }
        const data = this.rows[target.collection].get(target.id);
        return new FakeSnapshot(target.id, target, data);
      },
      update: (ref, value) => writes.push({ kind: 'update', ref, value }),
      create: (ref, value) => writes.push({ kind: 'create', ref, value }),
    };
    const result = await callback(transaction);
    for (const write of writes) {
      const rows = this.rows[write.ref.collection];
      if (write.kind === 'create' && rows.has(write.ref.id)) {
        throw new Error('already exists');
      }
      const current = rows.get(write.ref.id) || {};
      rows.set(write.ref.id, { ...current, ...write.value });
    }
    return result;
  }

  get(collection, id) { return this.rows[collection].get(id); }
}

function setup({ lifecycleStatus = 'SCHEDULED', departureAt = new Date(NOW + 60_000), seats = 0 } = {}) {
  const trip = {
    tripId: TRIP_ID,
    creatorUid: CREATOR_UID,
    creatorName: 'Trip Poster',
    status: seats === 0 ? 'CLOSED' : 'OPEN',
    lifecycleStatus,
    availableSeats: seats,
    departureAt,
  };
  const requests = [
    { id: 'accepted-request', data: { tripId: TRIP_ID, creatorUid: CREATOR_UID,
      requesterUid: 'accepted-user', status: 'ACCEPTED' } },
    { id: 'accepted-duplicate-request', data: { tripId: TRIP_ID, creatorUid: CREATOR_UID,
      requesterUid: 'accepted-user', status: 'ACCEPTED' } },
    { id: 'pending-request', data: { tripId: TRIP_ID, creatorUid: CREATOR_UID,
      requesterUid: 'pending-user', status: 'PENDING' } },
    { id: 'rejected-request', data: { tripId: TRIP_ID, creatorUid: CREATOR_UID,
      requesterUid: 'rejected-user', status: 'REJECTED' } },
  ];
  return new FakeFirestore(trip, requests);
}

function cancel(firestore, callerUid = CREATOR_UID) {
  return cancelTripCore({
    firestore,
    tripId: TRIP_ID,
    callerUid,
    nowMillis: NOW,
    serverTimestamp: 'server-timestamp',
  });
}

describe('trusted whole-trip cancellation', () => {
  it('allows only the creator and leaves state unchanged for a requester', async () => {
    const firestore = setup();
    await assert.rejects(cancel(firestore, 'accepted-user'), (error) =>
      error instanceof TripCancellationError && error.code === 'permission-denied');
    assert.equal(firestore.get('trips', TRIP_ID).lifecycleStatus, 'SCHEDULED');
    assert.equal(firestore.get('rideRequests', 'accepted-request').status, 'ACCEPTED');
  });

  it('cancels pending and accepted requests, closes new joins, notifies each affected requester, and preserves seats', async () => {
    const firestore = setup({ seats: 2 });
    const result = await cancel(firestore);
    assert.equal(result.cancelled, true);
    assert.equal(result.notifiedCount, 2);
    assert.equal(firestore.get('trips', TRIP_ID).status, 'CLOSED');
    assert.equal(firestore.get('trips', TRIP_ID).lifecycleStatus, 'CANCELLED');
    assert.equal(firestore.get('trips', TRIP_ID).availableSeats, 2);
    assert.equal(firestore.get('rideRequests', 'accepted-request').status, 'TRIP_CANCELLED');
    assert.equal(firestore.get('rideRequests', 'accepted-duplicate-request').status, 'TRIP_CANCELLED');
    assert.equal(firestore.get('rideRequests', 'pending-request').status, 'TRIP_CANCELLED');
    assert.equal(firestore.get('rideRequests', 'rejected-request').status, 'REJECTED');
    const acceptedNotice = firestore.get('tripNotifications', notificationIdFor(TRIP_ID, 'accepted-user'));
    const pendingNotice = firestore.get('tripNotifications', notificationIdFor(TRIP_ID, 'pending-user'));
    assert.equal(acceptedNotice.recipientUid, 'accepted-user');
    assert.equal(acceptedNotice.requestStatusAtCancellation, 'ACCEPTED');
    assert.equal(acceptedNotice.requestIds.length, 2);
    assert.equal(pendingNotice.recipientUid, 'pending-user');
    assert.equal(pendingNotice.requestStatusAtCancellation, 'PENDING');
  });

  it('is idempotent and does not create duplicate notifications', async () => {
    const firestore = setup();
    const first = await cancel(firestore);
    const second = await cancel(firestore);
    assert.equal(first.alreadyCancelled, false);
    assert.equal(second.alreadyCancelled, true);
    assert.equal(firestore.rows.tripNotifications.size, 2);
  });

  it('rejects completed, departed, and legacy trips with no canonical departure time', async () => {
    await assert.rejects(cancel(setup({ lifecycleStatus: 'COMPLETED' })),
      (error) => error.code === 'failed-precondition');
    await assert.rejects(cancel(setup({ departureAt: new Date(NOW - 1) })),
      (error) => error.code === 'failed-precondition');
    await assert.rejects(cancel(setup({ departureAt: null })),
      (error) => error.code === 'failed-precondition');
  });
});
