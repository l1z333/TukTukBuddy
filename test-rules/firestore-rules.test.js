const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const assert = require('assert').strict;
const { serverTimestamp } = require('firebase/firestore');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'tuktukbuddy-test-project';
let testEnv;

before(async () => {
  const rulesPath = path.resolve(__dirname, '../firestore.rules');
  const rules = fs.readFileSync(rulesPath, 'utf8');

  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

describe('Firestore Security Rules Tests for TukTukBuddy', () => {
  const alexUid = 'alex_uid';
  const jordanUid = 'jordan_uid';
  const strangerUid = 'stranger_uid';

  const tripId = 'trip_101';
  const requestId = 'req_202';

  const setupBaseData = async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();

      // User profiles
      await db.collection('users').doc(alexUid).set({
        uid: alexUid,
        name: 'Alex Driver',
        rollNo: 'CS001',
        avgRating: 5.0,
      });

      await db.collection('users').doc(jordanUid).set({
        uid: jordanUid,
        name: 'Jordan Passenger',
        rollNo: 'CS002',
        avgRating: 4.8,
      });

      // Base OPEN trip created by Alex with 2 available seats
      await db.collection('trips').doc(tripId).set({
        tripId: tripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-05-01',
        time: '10:00 AM',
        availableSeats: 2,
        notes: 'Going to metro',
        status: 'OPEN',
        createdAt: 1000000,
      });

      // Base PENDING ride request from Jordan for Alex's trip
      await db.collection('rideRequests').doc(requestId).set({
        requestId: requestId,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1000100,
      });
    });
  };

  const seedAcceptedCancellation = async ({
    tripId: cancellationTripId,
    requestIds,
    tripStatus = 'OPEN',
    availableSeats = 2,
    departureAt = new Date(Date.now() + 60 * 60 * 1000),
  }) => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(cancellationTripId).set({
        tripId: cancellationTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-10-25',
        time: '10:30 AM',
        departureAt,
        availableSeats,
        notes: '',
        status: tripStatus,
        createdAt: Date.now(),
      });

      for (const cancellationRequestId of requestIds) {
        await db.collection('rideRequests').doc(cancellationRequestId).set({
          requestId: cancellationRequestId,
          tripId: cancellationTripId,
          tripRoute: 'Campus Gate ➔ Metro Station',
          tripDateTime: '2026-10-25 10:30 AM',
          creatorUid: alexUid,
          requesterUid: jordanUid,
          requesterName: 'Jordan Passenger',
          status: 'ACCEPTED',
          createdAt: Date.now(),
        });
      }
    });
  };

  const cancelWithClient = async (uid, cancellationTripId, cancellationRequestId) => {
    const db = testEnv.authenticatedContext(uid).firestore();
    const tripRef = db.collection('trips').doc(cancellationTripId);
    const requestRef = db.collection('rideRequests').doc(cancellationRequestId);
    const notificationRef = db.collection('tripNotifications').doc(cancellationRequestId);

    return db.runTransaction(async (transaction) => {
      const tripSnapshot = await transaction.get(tripRef);
      const requestSnapshot = await transaction.get(requestRef);
      const trip = tripSnapshot.data();
      const request = requestSnapshot.data();

      if (!tripSnapshot.exists || !requestSnapshot.exists || request.status !== 'ACCEPTED') {
        throw new Error('Request is not cancellable');
      }

      if (trip.status === 'OPEN' || trip.status === 'CLOSED') {
        transaction.update(tripRef, {
          availableSeats: trip.availableSeats + 1,
          lastCancelledRequestId: cancellationRequestId,
        });
      } else {
        throw new Error('Trip is not active');
      }

      transaction.update(requestRef, { status: 'CANCELLED' });
      transaction.set(notificationRef, {
        notificationId: cancellationRequestId,
        type: 'REQUEST_CANCELLED',
        tripId: cancellationTripId,
        requestId: cancellationRequestId,
        senderUid: uid,
        senderName: request.requesterName,
        recipientUid: trip.creatorUid,
        message: `${request.requesterName} cancelled their accepted request for ${trip.pickup} → ${trip.destination}.`,
        createdAt: serverTimestamp(),
      });
    });
  };

  // Test 1: Trip creator accepts PENDING request with 1 seat removed
  it('allows trip creator to accept a PENDING request and decrement seats atomically', async () => {
    await setupBaseData();

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: requestId,
    });
    batch.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED',
    });

    await assertSucceeds(batch.commit());
  });

  // Test 2: Rejecting a request without changing any trip field
  it('allows trip creator to reject a request without changing trip fields', async () => {
    await setupBaseData();

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'REJECTED',
    });

    await assertSucceeds(batch.commit());
  });

  // Test 3: Denying seat decrement without matching accepted request
  it('denies trip seat decrement without a matching accepted request in transaction', async () => {
    await setupBaseData();

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    // Trying to decrement seats without accepting a request
    await assertFails(
      alexDb.collection('trips').doc(tripId).update({
        availableSeats: 1,
      })
    );
  });

  // Test 4: Denying acceptance of a request belonging to a different trip
  it('denies acceptance when request tripId does not match tripId', async () => {
    await setupBaseData();

    const unlinkedReqId = 'req_unlinked';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(unlinkedReqId).set({
        requestId: unlinkedReqId,
        tripId: 'other_trip_999',
        tripRoute: 'A ➔ B',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1000200,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: unlinkedReqId,
    });
    batch.update(alexDb.collection('rideRequests').doc(unlinkedReqId), {
      status: 'ACCEPTED',
    });

    await assertFails(batch.commit());
  });

  // Test 5: Denying acceptance by someone other than the trip creator
  it('denies acceptance attempt by someone other than the trip creator', async () => {
    await setupBaseData();

    const strangerDb = testEnv.authenticatedContext(strangerUid).firestore();

    const batch = strangerDb.batch();
    batch.update(strangerDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: requestId,
    });
    batch.update(strangerDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED',
    });

    await assertFails(batch.commit());
  });

  // Test 6: Denying changes to immutable trip and request fields
  it('denies changes to immutable trip or request fields during update', async () => {
    await setupBaseData();

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    // Trying to change pickup location on trip while accepting
    const batch1 = alexDb.batch();
    batch1.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      pickup: 'Forged Location',
      lastAcceptedRequestId: requestId,
    });
    batch1.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED',
    });
    await assertFails(batch1.commit());

    // Trying to change requesterUid on request while accepting
    const batch2 = alexDb.batch();
    batch2.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      lastAcceptedRequestId: requestId,
    });
    batch2.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED',
      requesterUid: 'stolen_uid',
    });
    await assertFails(batch2.commit());
  });

  // Test 7: Denying duplicate processing of an already accepted or rejected request
  it('denies duplicate processing of an already accepted request', async () => {
    await setupBaseData();

    // Set request status to ACCEPTED beforehand
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(requestId).update({
        status: 'ACCEPTED',
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: requestId,
    });
    batch.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED',
    });

    await assertFails(batch.commit());
  });

  // Test 8: Request creation rules (valid PENDING vs self-request / closed trip)
  it('allows valid request creation and denies self-requests or closed trip requests', async () => {
    await setupBaseData();

    const newReqId = 'req_new_303';
    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();

    // Valid request creation by Jordan
    await assertSucceeds(
      jordanDb.collection('rideRequests').doc(newReqId).set({
        requestId: newReqId,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1000300,
      })
    );

    // Self-request by Alex (trip creator) -> Denied
    const selfReqId = 'req_self_404';
    const alexDb = testEnv.authenticatedContext(alexUid).firestore();
    await assertFails(
      alexDb.collection('rideRequests').doc(selfReqId).set({
        requestId: selfReqId,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: alexUid,
        requesterName: 'Alex Driver',
        status: 'PENDING',
        createdAt: 1000400,
      })
    );

    // Request on CLOSED trip -> Denied
    const closedTripId = 'trip_closed_505';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(closedTripId).set({
        tripId: closedTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-05-01',
        time: '10:00 AM',
        availableSeats: 0,
        notes: 'Full',
        status: 'CLOSED',
        createdAt: 1000500,
      });
    });

    const closedReqId = 'req_closed_606';
    await assertFails(
      jordanDb.collection('rideRequests').doc(closedReqId).set({
        requestId: closedReqId,
        tripId: closedTripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1000600,
      })
    );
  });

  it('blocks client-side whole-trip cancellation and prevents joins or acceptance after cancellation', async () => {
    const cancelledTripId = 'trip_lifecycle_cancelled';
    const pendingRequestId = 'req_pending_for_cancelled_trip';
    const acceptedRequestId = 'req_accepted_for_cancelled_trip';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(cancelledTripId).set({
        tripId: cancelledTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        availableSeats: 2,
        status: 'CLOSED',
        lifecycleStatus: 'CANCELLED',
        departureAt: new Date(Date.now() + 60 * 60 * 1000),
      });
      await db.collection('rideRequests').doc(pendingRequestId).set({
        requestId: pendingRequestId,
        tripId: cancelledTripId,
        creatorUid: alexUid,
        requesterUid: jordanUid,
        status: 'PENDING',
      });
      await db.collection('rideRequests').doc(acceptedRequestId).set({
        requestId: acceptedRequestId,
        tripId: cancelledTripId,
        creatorUid: alexUid,
        requesterUid: strangerUid,
        status: 'ACCEPTED',
      });
    });

    const creatorDb = testEnv.authenticatedContext(alexUid).firestore();
    const requesterDb = testEnv.authenticatedContext(jordanUid).firestore();
    const strangerDb = testEnv.authenticatedContext(strangerUid).firestore();

    await assertFails(creatorDb.collection('trips').doc(cancelledTripId)
      .update({ lifecycleStatus: 'CANCELLED', status: 'CLOSED' }));
    await assertFails(requesterDb.collection('trips').doc(cancelledTripId)
      .update({ lifecycleStatus: 'CANCELLED' }));
    await assertFails(requesterDb.collection('rideRequests').doc(pendingRequestId)
      .update({ status: 'TRIP_CANCELLED' }));
    await assertFails(creatorDb.collection('rideRequests').doc(pendingRequestId)
      .update({ status: 'ACCEPTED' }));
    await assertFails(strangerDb.collection('rideRequests').doc('req_after_cancellation').set({
      requestId: 'req_after_cancellation',
      tripId: cancelledTripId,
      creatorUid: alexUid,
      requesterUid: strangerUid,
      status: 'PENDING',
    }));
  });

  it('allows only the affected passenger to read a whole-trip cancellation notification', async () => {
    const notificationId = 'trip_cancel_notice_private';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('tripNotifications').doc(notificationId).set({
        notificationId,
        type: 'TRIP_CANCELLED',
        tripId: 'trip_cancelled_private',
        requestId: 'req_cancelled_private',
        senderUid: alexUid,
        recipientUid: jordanUid,
        message: 'The trip creator cancelled this ride.',
        createdAt: new Date(),
      });
    });

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const alexDb = testEnv.authenticatedContext(alexUid).firestore();
    await assertSucceeds(jordanDb.collection('tripNotifications').doc(notificationId).get());
    await assertFails(alexDb.collection('tripNotifications').doc(notificationId).get());
  });

  it('allows the recipient notification query and denies another user’s recipient query', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('tripNotifications').doc('notice_for_jordan').set({
        notificationId: 'notice_for_jordan',
        type: 'TRIP_CANCELLED',
        recipientUid: jordanUid,
      });
      await db.collection('tripNotifications').doc('notice_for_alex').set({
        notificationId: 'notice_for_alex',
        type: 'REQUEST_CANCELLED',
        recipientUid: alexUid,
      });
    });

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    await assertSucceeds(jordanDb.collection('tripNotifications')
      .where('recipientUid', '==', jordanUid).get());
    await assertFails(jordanDb.collection('tripNotifications')
      .where('recipientUid', '==', alexUid).get());
  });

  it('allows the hidden-trip owner query and denies access under another user path', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('users').doc(jordanUid).collection('hiddenTrips')
        .doc('trip_hidden_for_jordan').set({ tripId: 'trip_hidden_for_jordan' });
      await db.collection('users').doc(alexUid).collection('hiddenTrips')
        .doc('trip_hidden_for_alex').set({ tripId: 'trip_hidden_for_alex' });
    });

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    await assertSucceeds(jordanDb.collection('users').doc(jordanUid)
      .collection('hiddenTrips').get());
    await assertFails(jordanDb.collection('users').doc(alexUid)
      .collection('hiddenTrips').get());
  });

  // Test 9: Acceptance transitioning trip to CLOSED when final seat is taken
  it('allows trip creator to accept final seat, transitioning trip status to CLOSED', async () => {
    const singleSeatTripId = 'trip_single_seat';
    const singleSeatReqId = 'req_single_seat';

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(singleSeatTripId).set({
        tripId: singleSeatTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-05-01',
        time: '10:00 AM',
        availableSeats: 1,
        notes: '1 seat left',
        status: 'OPEN',
        createdAt: 1000700,
      });

      await db.collection('rideRequests').doc(singleSeatReqId).set({
        requestId: singleSeatReqId,
        tripId: singleSeatTripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1000800,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(singleSeatTripId), {
      availableSeats: 0,
      status: 'CLOSED',
      lastAcceptedRequestId: singleSeatReqId,
    });
    batch.update(alexDb.collection('rideRequests').doc(singleSeatReqId), {
      status: 'ACCEPTED',
    });

    await assertSucceeds(batch.commit());

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const tripSnapshot = await db.collection('trips').doc(singleSeatTripId).get();
      const requestSnapshot = await db.collection('rideRequests').doc(singleSeatReqId).get();

      assert.equal(tripSnapshot.get('availableSeats'), 0);
      assert.equal(tripSnapshot.get('status'), 'CLOSED');
      assert.equal(requestSnapshot.get('status'), 'ACCEPTED');
    });
  });

  // Test 10: Denying invalid status transition (keeping OPEN when seats reach 0)
  it('denies acceptance when seats reach 0 but status remains OPEN', async () => {
    const singleSeatTripId = 'trip_single_seat_2';
    const singleSeatReqId = 'req_single_seat_2';

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(singleSeatTripId).set({
        tripId: singleSeatTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-05-01',
        time: '10:00 AM',
        availableSeats: 1,
        notes: '1 seat left',
        status: 'OPEN',
        createdAt: 1000900,
      });

      await db.collection('rideRequests').doc(singleSeatReqId).set({
        requestId: singleSeatReqId,
        tripId: singleSeatTripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001000,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(singleSeatTripId), {
      availableSeats: 0,
      status: 'OPEN', // Invalid status transition
      lastAcceptedRequestId: singleSeatReqId,
    });
    batch.update(alexDb.collection('rideRequests').doc(singleSeatReqId), {
      status: 'ACCEPTED',
    });

    await assertFails(batch.commit());
  });

  // Test 11: User profile ownership rules
  it('enforces profile owner read and write restrictions strictly', async () => {
    await setupBaseData();

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const strangerDb = testEnv.authenticatedContext(strangerUid).firestore();

    // Jordan can read own profile
    await assertSucceeds(jordanDb.collection('users').doc(jordanUid).get());

    // Jordan can update own profile
    await assertSucceeds(
      jordanDb.collection('users').doc(jordanUid).update({ name: 'Jordan Updated' })
    );

    // Stranger cannot read Alex's profile
    await assertFails(strangerDb.collection('users').doc(alexUid).get());

    // Stranger cannot update Alex's profile
    await assertFails(
      strangerDb.collection('users').doc(alexUid).update({ name: 'Hacked Name' })
    );
  });

  // Test 12: Trip and request deletion denials
  it('denies trip and ride-request deletion even by the creator', async () => {
    await setupBaseData();

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    await assertFails(alexDb.collection('trips').doc(tripId).delete());
    await assertFails(alexDb.collection('rideRequests').doc(requestId).delete());
  });

  // Test 13: Denying request creation for a non-existent trip
  it('denies request creation for a non-existent trip', async () => {
    const nonExistentReqId = 'req_non_existent';
    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();

    await assertFails(
      jordanDb.collection('rideRequests').doc(nonExistentReqId).set({
        requestId: nonExistentReqId,
        tripId: 'non_existent_trip_id_999',
        tripRoute: 'A ➔ B',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001100,
      })
    );
  });

  // Test 14: Denying request creation when trip availableSeats is 0
  it('denies request creation when trip has zero available seats', async () => {
    const zeroSeatTripId = 'trip_zero_seats';
    const zeroSeatReqId = 'req_zero_seats';

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(zeroSeatTripId).set({
        tripId: zeroSeatTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-05-01',
        time: '10:00 AM',
        availableSeats: 0,
        notes: 'Zero seats',
        status: 'OPEN',
        createdAt: 1001200,
      });
    });

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();

    await assertFails(
      jordanDb.collection('rideRequests').doc(zeroSeatReqId).set({
        requestId: zeroSeatReqId,
        tripId: zeroSeatTripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001300,
      })
    );
  });

  // Test 15: Denying trip seat decrement if linked request was ALREADY ACCEPTED before transaction
  it('denies trip seat decrement if linked request was already ACCEPTED prior to transaction', async () => {
    await setupBaseData();

    const alreadyAcceptedReqId = 'req_already_accepted';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(alreadyAcceptedReqId).set({
        requestId: alreadyAcceptedReqId,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'ACCEPTED', // Pre-existing status is ACCEPTED (not PENDING)
        createdAt: 1001400,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: alreadyAcceptedReqId,
    });
    batch.update(alexDb.collection('rideRequests').doc(alreadyAcceptedReqId), {
      status: 'ACCEPTED',
    });

    // Expect transaction to FAIL because request pre-state status was ACCEPTED, not PENDING
    await assertFails(batch.commit());
  });

  // Test 16: Denying acceptance if request document ID and its stored requestId field do not match
  it('denies acceptance if request document ID and its stored requestId field do not match', async () => {
    await setupBaseData();

    const mismatchDocId = 'req_doc_mismatch_555';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(mismatchDocId).set({
        requestId: 'req_stored_different_777', // Mismatch between doc ID and stored requestId
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001500,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: mismatchDocId,
    });
    batch.update(alexDb.collection('rideRequests').doc(mismatchDocId), {
      status: 'ACCEPTED',
    });

    // Expect transaction to FAIL because stored requestId field does not match lastAcceptedRequestId (mismatchDocId)
    await assertFails(batch.commit());
  });

  // Test 17: Denying acceptance while changing trip's lastAcceptedRequestId to a different request ID
  it('denies acceptance while changing trip lastAcceptedRequestId to a different request ID', async () => {
    await setupBaseData();

    const otherReqId = 'req_other_888';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(otherReqId).set({
        requestId: otherReqId,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001600,
      });
    });

    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    // Accepting requestId ('req_202') but setting trip's lastAcceptedRequestId to 'req_other_888'
    const batch = alexDb.batch();
    batch.update(alexDb.collection('trips').doc(tripId), {
      availableSeats: 1,
      status: 'OPEN',
      lastAcceptedRequestId: otherReqId, // Mismatch with the request being accepted
    });
    batch.update(alexDb.collection('rideRequests').doc(requestId), {
      status: 'ACCEPTED', // Request being accepted is 'req_202'
    });

    // Expect transaction to FAIL because rideRequests update rule checks trip.lastAcceptedRequestId == 'req_202'
    await assertFails(batch.commit());
  });

  // Test 18: Resubmitting a request after a previous request was REJECTED
  it('allows requester to submit a new request if previous request was REJECTED', async () => {
    await setupBaseData();

    // Set existing request to REJECTED
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('rideRequests').doc(requestId).update({
        status: 'REJECTED',
      });
    });

    const newReqId2 = 'req_new_after_reject_999';
    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();

    await assertSucceeds(
      jordanDb.collection('rideRequests').doc(newReqId2).set({
        requestId: newReqId2,
        tripId: tripId,
        tripRoute: 'Campus Gate ➔ Metro Station',
        tripDateTime: '2026-05-01 10:00 AM',
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'PENDING',
        createdAt: 1001700,
      })
    );
  });

  // Test 19: My Requests and Manage Requests queries return actual stored documents
  it('allows requester and creator to query their relevant requests accurately', async () => {
    await setupBaseData();

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const alexDb = testEnv.authenticatedContext(alexUid).firestore();

    // Jordan queries my requests
    await assertSucceeds(
      jordanDb.collection('rideRequests').where('requesterUid', '==', jordanUid).get()
    );

    // Alex queries manage requests
    await assertSucceeds(
      alexDb.collection('rideRequests').where('creatorUid', '==', alexUid).get()
    );
  });

  it('allows only the requester to cancel an accepted request before the cutoff and restores one seat', async () => {
    const cancellationTripId = 'trip_cancel_allowed';
    const cancellationRequestId = 'req_cancel_allowed';
    await seedAcceptedCancellation({ tripId: cancellationTripId, requestIds: [cancellationRequestId] });

    await assertSucceeds(cancelWithClient(jordanUid, cancellationTripId, cancellationRequestId));

    const creatorDb = testEnv.authenticatedContext(alexUid).firestore();
    const tripSnapshot = await creatorDb.collection('trips').doc(cancellationTripId).get();
    const notificationSnapshot = await creatorDb.collection('tripNotifications').doc(cancellationRequestId).get();
    const requesterDb = testEnv.authenticatedContext(jordanUid).firestore();
    const requestSnapshot = await requesterDb.collection('rideRequests').doc(cancellationRequestId).get();

    assert.equal(tripSnapshot.get('availableSeats'), 3);
    assert.equal(tripSnapshot.get('status'), 'OPEN');
    assert.equal(tripSnapshot.get('lastCancelledRequestId'), cancellationRequestId);
    assert.equal(requestSnapshot.get('status'), 'CANCELLED');
    assert.equal(notificationSnapshot.get('type'), 'REQUEST_CANCELLED');
    await assertFails(requesterDb.collection('tripNotifications').doc(cancellationRequestId).get());
  });

  it('denies cancellation by a user who does not own the accepted request', async () => {
    const cancellationTripId = 'trip_cancel_unauthorized';
    const cancellationRequestId = 'req_cancel_unauthorized';
    await seedAcceptedCancellation({ tripId: cancellationTripId, requestIds: [cancellationRequestId] });

    await assertFails(cancelWithClient(strangerUid, cancellationTripId, cancellationRequestId));

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const tripSnapshot = await jordanDb.collection('trips').doc(cancellationTripId).get();
    const requestSnapshot = await jordanDb.collection('rideRequests').doc(cancellationRequestId).get();
    assert.equal(tripSnapshot.get('availableSeats'), 2);
    assert.equal(requestSnapshot.get('status'), 'ACCEPTED');
  });

  it('denies cancellation at or after the five-minute cutoff using request time', async () => {
    const cancellationTripId = 'trip_cancel_at_cutoff';
    const cancellationRequestId = 'req_cancel_at_cutoff';
    await seedAcceptedCancellation({
      tripId: cancellationTripId,
      requestIds: [cancellationRequestId],
      departureAt: new Date(Date.now() + 4 * 60 * 1000 + 59 * 1000),
    });

    await assertFails(cancelWithClient(jordanUid, cancellationTripId, cancellationRequestId));

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const tripSnapshot = await jordanDb.collection('trips').doc(cancellationTripId).get();
    const requestSnapshot = await jordanDb.collection('rideRequests').doc(cancellationRequestId).get();
    assert.equal(tripSnapshot.get('availableSeats'), 2);
    assert.equal(requestSnapshot.get('status'), 'ACCEPTED');
  });

  it('prevents duplicate cancellation and restores the seat only once', async () => {
    const cancellationTripId = 'trip_cancel_duplicate';
    const cancellationRequestId = 'req_cancel_duplicate';
    await seedAcceptedCancellation({ tripId: cancellationTripId, requestIds: [cancellationRequestId] });

    await assertSucceeds(cancelWithClient(jordanUid, cancellationTripId, cancellationRequestId));

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const batch = jordanDb.batch();
    batch.update(jordanDb.collection('trips').doc(cancellationTripId), {
      availableSeats: 4,
      lastCancelledRequestId: cancellationRequestId,
    });
    batch.update(jordanDb.collection('rideRequests').doc(cancellationRequestId), {
      status: 'CANCELLED',
    });
    await assertFails(batch.commit());

    const tripSnapshot = await jordanDb.collection('trips').doc(cancellationTripId).get();
    assert.equal(tripSnapshot.get('availableSeats'), 3);
  });

  it('cancels one accepted request without changing another passenger request', async () => {
    const cancellationTripId = 'trip_cancel_one_of_two';
    const firstRequestId = 'req_cancel_one_of_two_a';
    const secondRequestId = 'req_cancel_one_of_two_b';
    await seedAcceptedCancellation({
      tripId: cancellationTripId,
      requestIds: [firstRequestId, secondRequestId],
      availableSeats: 1,
    });

    await assertSucceeds(cancelWithClient(jordanUid, cancellationTripId, firstRequestId));

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const firstSnapshot = await jordanDb.collection('rideRequests').doc(firstRequestId).get();
    const secondSnapshot = await jordanDb.collection('rideRequests').doc(secondRequestId).get();
    const tripSnapshot = await jordanDb.collection('trips').doc(cancellationTripId).get();
    assert.equal(firstSnapshot.get('status'), 'CANCELLED');
    assert.equal(secondSnapshot.get('status'), 'ACCEPTED');
    assert.equal(tripSnapshot.get('availableSeats'), 2);
  });

  it('restores a seat but does not reopen a CLOSED trip when an accepted request is cancelled', async () => {
    const cancellationTripId = 'trip_cancel_closed';
    const cancellationRequestId = 'req_cancel_closed';
    await seedAcceptedCancellation({
      tripId: cancellationTripId,
      requestIds: [cancellationRequestId],
      tripStatus: 'CLOSED',
      availableSeats: 0,
    });

    await assertSucceeds(cancelWithClient(jordanUid, cancellationTripId, cancellationRequestId));

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const tripSnapshot = await jordanDb.collection('trips').doc(cancellationTripId).get();
    assert.equal(tripSnapshot.get('availableSeats'), 1);
    assert.equal(tripSnapshot.get('status'), 'CLOSED');
    assert.equal(tripSnapshot.get('lastCancelledRequestId'), cancellationRequestId);
    assert.equal(tripSnapshot.get('pickup'), 'Campus Gate');
  });

  it('allows only the creator to complete a departed trip without changing seats or requests', async () => {
    const lifecycleTripId = 'trip_lifecycle_complete';
    const lifecycleRequestId = 'req_lifecycle_complete';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(lifecycleTripId).set({
        tripId: lifecycleTripId,
        creatorUid: alexUid,
        creatorName: 'Alex Driver',
        pickup: 'Campus Gate',
        destination: 'Metro Station',
        date: '2026-10-25',
        time: '10:30 AM',
        departureAt: new Date(Date.now() - 60_000),
        availableSeats: 0,
        notes: '',
        status: 'CLOSED',
        lifecycleStatus: 'SCHEDULED',
        createdAt: Date.now(),
      });
      await db.collection('rideRequests').doc(lifecycleRequestId).set({
        requestId: lifecycleRequestId,
        tripId: lifecycleTripId,
        creatorUid: alexUid,
        requesterUid: jordanUid,
        requesterName: 'Jordan Passenger',
        status: 'ACCEPTED',
        createdAt: Date.now(),
      });
    });

    const creatorDb = testEnv.authenticatedContext(alexUid).firestore();
    const requesterDb = testEnv.authenticatedContext(jordanUid).firestore();
    const tripRef = creatorDb.collection('trips').doc(lifecycleTripId);
    await assertFails(requesterDb.collection('trips').doc(lifecycleTripId)
      .update({ lifecycleStatus: 'IN_PROGRESS' }));
    await assertFails(requesterDb.collection('trips').doc(lifecycleTripId)
      .update({ lifecycleStatus: 'COMPLETED' }));
    await assertSucceeds(tripRef.update({ lifecycleStatus: 'IN_PROGRESS' }));
    const inProgressSnapshot = await tripRef.get();
    assert.equal(inProgressSnapshot.get('lifecycleStatus'), 'IN_PROGRESS');
    assert.equal(inProgressSnapshot.get('status'), 'CLOSED');
    assert.equal(inProgressSnapshot.get('availableSeats'), 0);
    await assertSucceeds(tripRef.update({ lifecycleStatus: 'COMPLETED' }));

    const tripSnapshot = await tripRef.get();
    const requestSnapshot = await requesterDb.collection('rideRequests').doc(lifecycleRequestId).get();
    assert.equal(tripSnapshot.get('lifecycleStatus'), 'COMPLETED');
    assert.equal(tripSnapshot.get('status'), 'CLOSED');
    assert.equal(tripSnapshot.get('availableSeats'), 0);
    assert.equal(requestSnapshot.get('status'), 'ACCEPTED');
    await assertFails(tripRef.update({ lifecycleStatus: 'COMPLETED' }));
    await assertFails(tripRef.update({ lifecycleStatus: 'COMPLETED', availableSeats: 1 }));
  });

  it('denies completing before departure and denies completion without a canonical timestamp', async () => {
    const futureTripId = 'trip_lifecycle_future';
    const legacyTripId = 'trip_lifecycle_legacy';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const base = {
        creatorUid: alexUid,
        availableSeats: 1,
        status: 'OPEN',
        createdAt: Date.now(),
      };
      await db.collection('trips').doc(futureTripId).set({
        ...base,
        tripId: futureTripId,
        departureAt: new Date(Date.now() + 60 * 60 * 1000),
        lifecycleStatus: 'SCHEDULED',
      });
      await db.collection('trips').doc(legacyTripId).set({
        ...base,
        tripId: legacyTripId,
        date: '2020-01-01',
        time: '10:00 AM',
      });
    });

    const creatorDb = testEnv.authenticatedContext(alexUid).firestore();
    await assertFails(creatorDb.collection('trips').doc(futureTripId)
      .update({ lifecycleStatus: 'COMPLETED' }));
    await assertFails(creatorDb.collection('trips').doc(futureTripId)
      .update({ lifecycleStatus: 'IN_PROGRESS' }));
    // Firestore rules require the server timestamp for lifecycle completion.
    await assertFails(creatorDb.collection('trips').doc(legacyTripId)
      .update({ lifecycleStatus: 'COMPLETED' }));
    await assertFails(creatorDb.collection('trips').doc(legacyTripId)
      .update({ lifecycleStatus: 'IN_PROGRESS' }));
  });

  it('hides completed trips only in the requesting user history and never deletes shared data', async () => {
    const hiddenTripId = 'trip_personal_history_hide';
    const hiddenRequestId = 'req_personal_history_hide';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection('trips').doc(hiddenTripId).set({
        tripId: hiddenTripId,
        creatorUid: alexUid,
        status: 'CLOSED',
        availableSeats: 0,
        departureAt: new Date(Date.now() - 60_000),
        lifecycleStatus: 'COMPLETED',
      });
      await db.collection('rideRequests').doc(hiddenRequestId).set({
        requestId: hiddenRequestId,
        tripId: hiddenTripId,
        creatorUid: alexUid,
        requesterUid: jordanUid,
        status: 'ACCEPTED',
      });
      await db.collection('trips').doc('trip_still_active').set({
        tripId: 'trip_still_active',
        creatorUid: alexUid,
        status: 'OPEN',
        availableSeats: 1,
        departureAt: new Date(Date.now() + 60 * 60 * 1000),
        lifecycleStatus: 'SCHEDULED',
      });
    });

    const jordanDb = testEnv.authenticatedContext(jordanUid).firestore();
    const alexDb = testEnv.authenticatedContext(alexUid).firestore();
    const marker = { tripId: hiddenTripId, hiddenAt: serverTimestamp() };
    await assertSucceeds(jordanDb.collection('users').doc(jordanUid)
      .collection('hiddenTrips').doc(hiddenTripId).set(marker));
    await assertFails(alexDb.collection('users').doc(jordanUid)
      .collection('hiddenTrips').doc(hiddenTripId).get());
    await assertFails(jordanDb.collection('users').doc(jordanUid).collection('hiddenTrips')
      .doc('trip_still_active').set({ tripId: 'trip_still_active', hiddenAt: serverTimestamp() }));

    const tripSnapshot = await alexDb.collection('trips').doc(hiddenTripId).get();
    const requestSnapshot = await jordanDb.collection('rideRequests').doc(hiddenRequestId).get();
    assert.equal(tripSnapshot.get('lifecycleStatus'), 'COMPLETED');
    assert.equal(requestSnapshot.get('status'), 'ACCEPTED');
    await assertSucceeds(jordanDb.collection('users').doc(jordanUid)
      .collection('hiddenTrips').doc(hiddenTripId).delete());
    assert.equal((await alexDb.collection('trips').doc(hiddenTripId).get()).exists, true);
    assert.equal((await jordanDb.collection('rideRequests').doc(hiddenRequestId).get()).exists, true);
  });
});
