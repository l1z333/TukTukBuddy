'use strict';

const crypto = require('crypto');

const MAX_AFFECTED_REQUESTS = 249; // 1 trip + 2 writes per request stays under Firestore's 500-write limit.

class TripCancellationError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'TripCancellationError';
    this.code = code;
  }
}

function notificationIdFor(tripId, recipientUid) {
  return `trip_cancel_${crypto.createHash('sha256')
    .update(`${tripId}/${recipientUid}`)
    .digest('hex')}`;
}

function timestampMillis(value) {
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return null;
}

async function cancelTripCore({ firestore, tripId, callerUid, nowMillis = Date.now(), serverTimestamp }) {
  if (!callerUid) throw new TripCancellationError('unauthenticated', 'Sign in before cancelling a trip.');
  if (typeof tripId !== 'string' || !tripId.trim()) {
    throw new TripCancellationError('invalid-argument', 'A valid trip ID is required.');
  }

  const tripRef = firestore.collection('trips').doc(tripId);
  return firestore.runTransaction(async (transaction) => {
    const tripSnapshot = await transaction.get(tripRef);
    if (!tripSnapshot.exists) {
      throw new TripCancellationError('not-found', 'This trip no longer exists.');
    }
    const trip = tripSnapshot.data();
    if (trip.creatorUid !== callerUid) {
      throw new TripCancellationError('permission-denied', 'Only the trip creator can cancel it.');
    }
    if (trip.lifecycleStatus === 'COMPLETED') {
      throw new TripCancellationError('failed-precondition', 'A completed trip cannot be cancelled.');
    }
    if (trip.lifecycleStatus === 'CANCELLED') {
      return { cancelled: true, alreadyCancelled: true, notifiedCount: 0 };
    }
    if (trip.lifecycleStatus && trip.lifecycleStatus !== 'SCHEDULED') {
      throw new TripCancellationError('failed-precondition', 'Only a scheduled trip can be cancelled.');
    }
    const departureMillis = timestampMillis(trip.departureAt);
    const trustedNow = typeof nowMillis === 'function' ? nowMillis() : nowMillis;
    if (departureMillis === null || trustedNow >= departureMillis) {
      throw new TripCancellationError(
        'failed-precondition',
        'This trip has departed or has no verified departure time and cannot be cancelled as upcoming.'
      );
    }
    if (trip.status !== 'OPEN' && trip.status !== 'CLOSED') {
      throw new TripCancellationError('failed-precondition', 'This trip is not in a cancellable seat state.');
    }

    const requestsQuery = firestore.collection('rideRequests').where('tripId', '==', tripId);
    const requestsSnapshot = await transaction.get(requestsQuery);
    const affected = requestsSnapshot.docs.filter((document) => {
      const status = document.get('status');
      return status === 'PENDING' || status === 'ACCEPTED';
    });
    if (affected.length > MAX_AFFECTED_REQUESTS) {
      throw new TripCancellationError(
        'resource-exhausted',
        `This trip has too many active requests to cancel atomically (maximum ${MAX_AFFECTED_REQUESTS}).`
      );
    }

    const noticesByRecipient = new Map();
    for (const requestSnapshot of affected) {
      const request = requestSnapshot.data();
      const requestId = requestSnapshot.id;
      if (request.tripId !== tripId || request.creatorUid !== callerUid
          || !request.requesterUid || request.requesterUid === callerUid) {
        throw new TripCancellationError(
          'failed-precondition',
          'An active ride request has inconsistent ownership data; no records were changed.'
        );
      }
      let notice = noticesByRecipient.get(request.requesterUid);
      if (!notice) {
        const notificationId = notificationIdFor(tripId, request.requesterUid);
        notice = {
          request,
          requestId,
          requestIds: [],
          notificationId,
          notificationRef: firestore.collection('tripNotifications').doc(notificationId),
        };
        noticesByRecipient.set(request.requesterUid, notice);
      } else if (request.status === 'ACCEPTED' && notice.request.status !== 'ACCEPTED') {
        notice.request = request;
        notice.requestId = requestId;
      }
      notice.requestIds.push(requestId);
    }
    const notices = Array.from(noticesByRecipient.values());

    // Read all deterministic notification documents before staging any writes.
    const existingNotices = [];
    for (const notice of notices) {
      existingNotices.push(await transaction.get(notice.notificationRef));
    }
    notices.forEach((notice, index) => {
      const existing = existingNotices[index];
      if (!existing.exists) return;
      const data = existing.data();
      if (data.type !== 'TRIP_CANCELLED'
          || data.tripId !== tripId
          || data.recipientUid !== notice.request.requesterUid) {
        throw new TripCancellationError(
          'already-exists',
          'A notification ID collision prevented trip cancellation; no records were changed.'
        );
      }
    });

    transaction.update(tripRef, {
      status: 'CLOSED',
      lifecycleStatus: 'CANCELLED',
    });
    affected.forEach((requestSnapshot) => {
      transaction.update(requestSnapshot.ref, { status: 'TRIP_CANCELLED' });
    });
    notices.forEach((notice, index) => {
      if (!existingNotices[index].exists) {
        transaction.create(notice.notificationRef, {
          notificationId: notice.notificationId,
          type: 'TRIP_CANCELLED',
          tripId,
          requestId: notice.requestId,
          requestIds: notice.requestIds,
          requestStatusAtCancellation: notice.request.status,
          senderUid: callerUid,
          senderName: trip.creatorName || 'Trip creator',
          recipientUid: notice.request.requesterUid,
          message: notice.request.status === 'ACCEPTED'
            ? 'The trip creator cancelled this ride.'
            : 'The trip you requested has been cancelled by its creator.',
          createdAt: serverTimestamp,
        });
      }
    });

    return { cancelled: true, alreadyCancelled: false, notifiedCount: notices.length };
  });
}

module.exports = {
  MAX_AFFECTED_REQUESTS,
  TripCancellationError,
  cancelTripCore,
  notificationIdFor,
};
