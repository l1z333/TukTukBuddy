'use strict';

const { initializeApp } = require('firebase-admin/app');
const { FieldValue, getFirestore } = require('firebase-admin/firestore');
const { HttpsError, onCall } = require('firebase-functions/v2/https');
const { cancelTripCore, TripCancellationError } = require('./src/tripCancellationCore');

initializeApp();

exports.cancelTrip = onCall(async (request) => {
  try {
    return await cancelTripCore({
      firestore: getFirestore(),
      tripId: request.data && request.data.tripId,
      callerUid: request.auth && request.auth.uid,
      nowMillis: () => Date.now(),
      serverTimestamp: FieldValue.serverTimestamp(),
    });
  } catch (error) {
    if (error instanceof TripCancellationError) {
      throw new HttpsError(error.code, error.message);
    }
    throw new HttpsError('internal', 'The trip could not be cancelled. Please try again.');
  }
});
