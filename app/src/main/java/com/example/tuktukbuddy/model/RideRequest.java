package com.example.tuktukbuddy.model;

import java.util.HashMap;
import java.util.Map;

public class RideRequest {
    private String requestId;
    private String tripId;
    private String tripRoute;
    private String tripDateTime;
    private String creatorUid;
    private String requesterUid;
    private String requesterName;
    private String status; // "PENDING", "ACCEPTED", "REJECTED"
    private long createdAt;

    // Default constructor required for Firestore deserialization
    public RideRequest() {
    }

    public RideRequest(String requestId, String tripId, String tripRoute, String tripDateTime,
                       String creatorUid, String requesterUid, String requesterName,
                       String status, long createdAt) {
        this.requestId = requestId;
        this.tripId = tripId;
        this.tripRoute = tripRoute;
        this.tripDateTime = tripDateTime;
        this.creatorUid = creatorUid;
        this.requesterUid = requesterUid;
        this.requesterName = requesterName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTripRoute() {
        return tripRoute;
    }

    public void setTripRoute(String tripRoute) {
        this.tripRoute = tripRoute;
    }

    public String getTripDateTime() {
        return tripDateTime;
    }

    public void setTripDateTime(String tripDateTime) {
        this.tripDateTime = tripDateTime;
    }

    public String getCreatorUid() {
        return creatorUid;
    }

    public void setCreatorUid(String creatorUid) {
        this.creatorUid = creatorUid;
    }

    public String getRequesterUid() {
        return requesterUid;
    }

    public void setRequesterUid(String requesterUid) {
        this.requesterUid = requesterUid;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("requestId", requestId);
        map.put("tripId", tripId);
        map.put("tripRoute", tripRoute != null ? tripRoute : "");
        map.put("tripDateTime", tripDateTime != null ? tripDateTime : "");
        map.put("creatorUid", creatorUid);
        map.put("requesterUid", requesterUid);
        map.put("requesterName", requesterName);
        map.put("status", status != null ? status : "PENDING");
        map.put("createdAt", createdAt);
        return map;
    }
}