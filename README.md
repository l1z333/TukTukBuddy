# TukTukBuddy

> **Campus Auto-Rickshaw Ride Sharing for Students**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://java.com)
[![Backend](https://img.shields.io/badge/Backend-Firebase-yellow.svg)](https://firebase.google.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)](https://developer.android.com)

TukTukBuddy is an Android application that helps college students coordinate shared auto-rickshaw rides around campus. Students can post planned trips, browse available rides, and send join requests to other students. The application uses Firebase Cloud Firestore for real-time synchronization, without requiring GPS, a mapping SDK, or a custom backend server.

---

## Problem Statement

Auto-rickshaw fares can be expensive for individual students but more affordable when shared among two or three passengers. However, students often lack a convenient way to find others travelling in the same direction at a similar time.

TukTukBuddy addresses this problem by providing a lightweight, real-time platform where students can publish planned trips, discover compatible rides, and coordinate shared transportation.

---

## Key Features

| Feature | Description |
|---|---|
| **Simple Onboarding** | Register a profile using a name and roll number, without email, password, or OTP verification. |
| **Post a Trip** | Select pickup and destination locations from predefined dropdowns and specify the date, time, and available seats. |
| **Browse and Filter Trips** | View open trips and filter them by pickup and destination. |
| **Join Requests** | Request to join an available trip with a single action. |
| **Request Management** | Trip creators can accept or reject incoming join requests. |
| **Real-Time Synchronization** | Changes are reflected across devices using Cloud Firestore listeners. |
| **Seat Management** | Available seats decrease when requests are accepted, and trips automatically close when no seats remain. |
| **Firestore Security Rules** | Access controls restrict database operations according to user identity and ownership. |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Programming Language | Java |
| Platform | Android |
| Minimum SDK | API 24 |
| Target SDK | API 36 |
| User Interface | AppCompat, Material Components, RecyclerView |
| Authentication | Firebase Anonymous Authentication |
| Database | Cloud Firestore |
| Architecture | Activity-based MVC with Repository pattern |
| Local State Management | SharedPreferences |
| Build System | Gradle |

---

## Application Screens

| Screen | Purpose |
|---|---|
| **Onboarding (`MainActivity`)** | Collects the student's name and roll number during initial setup. |
| **Home (`HomeActivity`)** | Displays user information and provides navigation to application features. |
| **Post Trip (`PostTripActivity`)** | Allows students to create trips using location dropdowns and date and time pickers. |
| **Browse Trips (`BrowseTripsActivity`)** | Displays open trips and provides pickup and destination filters. |
| **My Requests (`MyRequestsActivity`)** | Displays join requests submitted by the current user and their statuses. |
| **Manage Requests (`ManageRequestsActivity`)** | Allows trip creators to accept or reject incoming requests. |

---

## Project Structure

```text
app/src/main/java/com/example/tuktukbuddy/
|
├── MainActivity.java
├── HomeActivity.java
├── PostTripActivity.java
├── BrowseTripsActivity.java
├── MyRequestsActivity.java
├── ManageRequestsActivity.java
|
├── model/
│   ├── User.java
│   ├── Trip.java
│   └── RideRequest.java
|
├── repository/
│   ├── UserRepository.java
│   ├── TripRepository.java
│   └── RideRequestRepository.java
|
├── adapter/
│   ├── TripAdapter.java
│   ├── MyRequestsAdapter.java
│   └── ManageRequestsAdapter.java
|
└── util/
    └── UserPreferenceManager.java
```

### Component Responsibilities

- **Activities:** Handle application screens and user interactions.
- **Models:** Represent users, trips, and ride requests.
- **Repositories:** Encapsulate Firebase authentication and Firestore database operations.
- **Adapters:** Populate RecyclerViews with trip and request data.
- **UserPreferenceManager:** Stores cached profile and session information using SharedPreferences.

---

## Firebase Data Model

### 1. Users Collection

Collection: `users`

```json
{
  "uid": "string",
  "name": "string",
  "rollNo": "string",
  "avgRating": 0.0
}
```

| Field | Description |
|---|---|
| `uid` | Firebase Authentication user identifier. |
| `name` | Student's display name. |
| `rollNo` | Student's roll number or institutional identifier. |
| `avgRating` | Average user rating, initialized to `0.0`. |

### 2. Trips Collection

Collection: `trips`

```json
{
  "tripId": "string",
  "creatorUid": "string",
  "creatorName": "string",
  "pickup": "string",
  "destination": "string",
  "date": "YYYY-MM-DD",
  "time": "HH:MM AM/PM",
  "availableSeats": 3,
  "notes": "string",
  "status": "OPEN",
  "createdAt": 0
}
```

| Field | Description |
|---|---|
| `tripId` | Unique identifier for the trip. |
| `creatorUid` | Firebase UID of the student who created the trip. |
| `creatorName` | Display name of the trip creator. |
| `pickup` | Selected pickup location. |
| `destination` | Selected destination. |
| `date` | Trip date in `YYYY-MM-DD` format. |
| `time` | Scheduled departure time. |
| `availableSeats` | Number of seats currently available. |
| `notes` | Optional information about the trip. |
| `status` | Trip status: `OPEN` or `CLOSED`. |
| `createdAt` | Creation timestamp in Unix milliseconds, if implemented accordingly. |

### 3. Ride Requests Collection

Collection: `rideRequests`

```json
{
  "requestId": "string",
  "tripId": "string",
  "route": "Pickup -> Destination",
  "dateTime": "Date Time",
  "creatorUid": "string",
  "requesterUid": "string",
  "requesterName": "string",
  "status": "PENDING",
  "createdAt": 0
}
```

| Field | Description |
|---|---|
| `requestId` | Unique identifier for the join request. |
| `tripId` | Identifier of the requested trip. |
| `route` | Route information stored for display. |
| `dateTime` | Trip date and time stored for display. |
| `creatorUid` | Firebase UID of the trip creator. |
| `requesterUid` | Firebase UID of the requesting student. |
| `requesterName` | Display name of the requesting student. |
| `status` | Request status: `PENDING`, `ACCEPTED`, or `REJECTED`. |
| `createdAt` | Request creation timestamp. |

---

## Security and Access Control

Firestore security rules are used to restrict access to application data.

- **Users:** Authenticated users can read profiles, while profile writes are restricted to the corresponding user.
- **Trips:** Authenticated users can read eligible open trips. Trip modifications and deletions are restricted according to ownership and the configured rules.
- **Ride Requests:** Access is limited to the requester and trip creator. Request status changes are restricted to authorized trip creators.

Security rules must validate the relevant fields and permitted state transitions. In particular, accepting a request should not allow unauthorized changes to seat counts, trip ownership, or request identities.

Firebase Anonymous Authentication identifies application users but does not independently verify student identity. Institutional identity verification would be required for stronger campus-specific access control.

---

## Supported Campus Locations

Pickup and destination locations are selected from a predefined list, avoiding the need for free-text addresses, GPS permissions, or mapping services.

- Main Gate
- Hostel Block A
- Hostel Block B
- Hostel Block C
- Academic Block
- Library
- Canteen
- Sports Complex
- Railway Station
- Bus Stand
- Metro Station
- City Mall
- Airport

---

## Demonstration Workflow

The application can be demonstrated using two Android emulators representing different students.

### Student 1: Create a Trip

1. Open the application and enter a name and roll number.
2. Navigate to the Post Trip screen.
3. Select `Hostel Block A` as the pickup location.
4. Select `Railway Station` as the destination.
5. Specify the departure time and available seats.
6. Publish the trip.

### Student 2: Request to Join

1. Open the application on a second emulator.
2. Complete the initial profile setup.
3. Navigate to Browse Trips.
4. Locate the trip published by Student 1.
5. Submit a join request.

### Student 1: Manage the Request

1. Open Manage Requests.
2. Review the incoming join request.
3. Accept the request.
4. Verify that the available seat count decreases and the request status changes to `ACCEPTED`.

### Student 2: Verify the Result

1. Open My Requests.
2. Verify that the request status is updated to `ACCEPTED`.

**Expected outcome:** Both devices reflect the updated request status and seat availability through Firestore synchronization, without requiring manual refresh or a custom backend server.

---

## Setup and Execution

### Prerequisites

- Android Studio
- JDK version compatible with the project's Gradle configuration
- Android SDK and platform tools
- An Android emulator or physical device
- Access to the Firebase project associated with the application

### Installation

```bash
# Clone the repository
git clone <repo-url>

# Navigate to the project directory
cd TukTukBuddy
```

Open the project directory in Android Studio and allow Gradle synchronization to complete.

### Firebase Configuration

1. Open the Firebase Console associated with the application.
2. Verify that Firebase Anonymous Authentication is enabled.
3. Confirm that Cloud Firestore has been created and configured.
4. Deploy the contents of `firestore.rules` through the Firestore Rules interface.
5. Verify that `google-services.json` corresponds to the intended Firebase project.

### Run the Application

1. Start an Android emulator through Android Studio's Device Manager, or connect a physical device.
2. Select the application run configuration.
3. Build and launch the application.
4. For the demonstration, start two emulators and complete the workflow using separate anonymous user accounts.

The repository includes `google-services.json` according to the project documentation. Its presence does not guarantee that the Firebase project remains active or that its authentication, database, and security configurations are correct. These settings should be verified before running the application.

---

## Future Enhancements

### 1. User Rating System

Allow students to rate fellow passengers after a completed trip. Display average ratings on user profiles to support informed ride-sharing decisions.

### 2. Push Notifications

Integrate Firebase Cloud Messaging to notify students when their join requests are accepted or rejected.

### 3. Trip History

Maintain a history of completed, cancelled, and previously joined trips, allowing students to review their past activity.

---

## Conclusion

TukTukBuddy provides a lightweight Android-based solution for coordinating shared auto-rickshaw rides within a campus community. By combining Java, Firebase Anonymous Authentication, and Cloud Firestore, the application supports trip creation, ride discovery, join-request management, and real-time synchronization.

Its predefined locations and serverless Firebase architecture reduce implementation complexity while providing a foundation for future enhancements, including notifications, user ratings, and trip history.
