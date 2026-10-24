# TukTukBuddy 🛺

> **Campus Auto-Rickshaw Ride Sharing for Students**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Java-orange.svg)](https://java.com)
[![Backend](https://img.shields.io/badge/Backend-Firebase-yellow.svg)](https://firebase.google.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)](https://developer.android.com)

TukTukBuddy is an Android app that helps college students coordinate shared auto-rickshaw rides around campus. Students can post trips they're planning, browse what others have posted, and send join requests — all synced in real time through Firebase, with no GPS, no map SDK, and no server code required.

---

## Problem It Solves

Auto fares are expensive for one person but affordable split two or three ways. There's no existing tool for students to quickly find others heading the same direction at the same time. TukTukBuddy solves this by acting as a lightweight, real-time bulletin board for campus trips.

---

## Key Features

| Feature | Description |
|---|---|
| 🚀 **Instant Onboarding** | Enter name + roll number — no email, no password, no OTP |
| 🛺 **Post a Trip** | Pick from/to campus location from a dropdown, set date, time, and available seats |
| 🔍 **Browse & Filter Trips** | Filter open trips by From and To location in real time |
| ✉️ **Join Requests** | Send a request to join any open trip with one tap |
| ✅ **Accept / Reject** | Trip creator reviews requests and accepts or rejects each one |
| 🔄 **Live Sync** | Both devices update instantly via Firestore — no refresh needed |
| 🔒 **Smart Seat Management** | Seat count decrements on each accept; trip auto-closes at 0 seats |
| 🛡️ **Security Rules** | Firestore rules ensure users can only read/write their own data |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java |
| Platform | Android (min SDK 24, target SDK 36) |
| UI | Material Design 3 — AppCompat + Material Components |
| Authentication | Firebase Anonymous Auth |
| Database | Cloud Firestore (NoSQL, real-time) |
| Architecture | Activity-based MVC with Repository pattern |
| Local State | SharedPreferences (UserPreferenceManager) |

---

## App Screens

| Screen | Purpose |
|---|---|
| **Onboarding** (`MainActivity`) | First-launch profile setup — name & roll number |
| **Home** (`HomeActivity`) | Dashboard showing user info and navigation to all features |
| **Post Trip** (`PostTripActivity`) | Form to create a new trip with location spinners + date/time pickers |
| **Browse Trips** (`BrowseTripsActivity`) | List of open trips with From/To filter bar |
| **My Requests** (`MyRequestsActivity`) | View all join requests you've sent and their status |
| **Manage Requests** (`ManageRequestsActivity`) | Accept or reject join requests for trips you posted |

---

## Project Structure

```
app/src/main/java/com/example/tuktukbuddy/
│
├── MainActivity.java            # Onboarding — name + roll number
├── HomeActivity.java            # Dashboard
├── PostTripActivity.java        # Post a new trip (Spinner dropdowns)
├── BrowseTripsActivity.java     # Browse + filter open trips
├── MyRequestsActivity.java      # Your sent join requests
├── ManageRequestsActivity.java  # Accept / reject incoming requests
│
├── model/
│   ├── User.java                # uid, name, rollNo, avgRating
│   ├── Trip.java                # tripId, pickup, destination, date, time, availableSeats, status
│   └── RideRequest.java         # requestId, tripId, requesterUid, creatorUid, status
│
├── repository/
│   ├── UserRepository.java           # Firestore CRUD for users + Firebase Auth wrapper
│   ├── TripRepository.java           # Firestore CRUD for trips
│   └── RideRequestRepository.java    # Accept/reject logic using Firestore transactions
│
├── adapter/
│   ├── TripAdapter.java              # RecyclerView adapter for Browse Trips
│   ├── MyRequestsAdapter.java        # RecyclerView adapter for My Requests
│   └── ManageRequestsAdapter.java    # RecyclerView adapter for Manage Requests
│
└── util/
    └── UserPreferenceManager.java    # SharedPreferences wrapper for cached user session
```

---

## Firebase Data Model

### `users` collection
```
{
  uid:        string   // Firebase Auth UID
  name:       string   // Student's full name
  rollNo:     string   // Roll number / student ID
  avgRating:  double   // Average rating (default 0.0)
}
```

### `trips` collection
```
{
  tripId:         string   // Auto-generated Firestore document ID
  creatorUid:     string   // UID of student who posted the trip
  creatorName:    string   // Display name of creator
  pickup:         string   // From location (campus location name)
  destination:    string   // To location (campus location name)
  date:           string   // "YYYY-MM-DD"
  time:           string   // "HH:MM AM/PM"
  availableSeats: int      // Decrements on each accept; trip closes at 0
  notes:          string   // Optional notes
  status:         string   // "OPEN" | "CLOSED"
  createdAt:      long     // Unix timestamp
}
```

### `rideRequests` collection
```
{
  requestId:     string   // Auto-generated Firestore document ID
  tripId:        string   // ID of the trip being requested
  route:         string   // "Pickup ➔ Destination" (denormalized for display)
  dateTime:      string   // "Date Time" (denormalized for display)
  creatorUid:    string   // UID of trip creator (for security rules)
  requesterUid:  string   // UID of student who sent the request
  requesterName: string   // Display name of requester
  status:        string   // "PENDING" | "ACCEPTED" | "REJECTED"
  createdAt:     long     // Unix timestamp
}
```

---

## Security Rules

Firestore rules (`firestore.rules`) enforce:
- **Users** — authenticated users can read all profiles; only write their own
- **Trips** — authenticated users can read all open trips; only creator can update/delete
- **Ride Requests** — only the requester and the trip creator can read a request; only the creator can flip status to ACCEPTED/REJECTED

---

## Campus Locations

Pickup and destination are selected from a fixed dropdown list — no free text, no GPS required:

`Main Gate` · `Hostel Block A` · `Hostel Block B` · `Hostel Block C` · `Academic Block` · `Library` · `Canteen` · `Sports Complex` · `Railway Station` · `Bus Stand` · `Metro Station` · `City Mall` · `Airport`

---

## Live Demo Flow (Two Emulators)

```
Emulator A (Student 1)                     Emulator B (Student 2)
──────────────────────                     ──────────────────────
1. Open app → enter name + roll no
2. Post Trip:
   From: Hostel Block A
   To:   Railway Station
   Time: 6:00 PM
                                           3. Open app → enter name + roll no
                                           4. Browse Trips → sees Student 1's trip
                                           5. Tap "Request to Join" → request sent

6. Manage Requests → sees request
7. Tap Accept ✅
   └─ Seat count: 3 → 2
   └─ Request status: PENDING → ACCEPTED
                                           8. My Requests → status shows ACCEPTED ✅
```

**The "wow" moment**: both screens reflect the same state update — live — with no server code written.

---

## Setup & Running

```bash
# 1. Clone the repo
git clone <repo-url>
cd TukTukBuddy

# 2. Open in Android Studio
#    File → Open → select the TukTukBuddy folder
#    Wait for Gradle sync

# 3. Deploy Firestore security rules
#    Go to Firebase Console → Firestore → Rules
#    Paste contents of firestore.rules → Publish

# 4. Run on device or emulator
#    For the live demo: start two AVDs from Tools → Device Manager
```

> `google-services.json` is already included — the Firebase project is pre-configured and ready to run.

---

## What's Still Planned

- **Rating system** — rate each other after a trip completes
- **Push notifications** — FCM alerts when a request is accepted or rejected
- **Trip history** — view past completed trips

---

## Contributing

This is a college project. To suggest changes, open an issue or fork and submit a PR.
