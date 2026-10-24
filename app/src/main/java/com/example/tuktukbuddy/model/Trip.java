package com.example.tuktukbuddy.model;

import java.util.HashMap;
import java.util.Map;

public class Trip {
    private String tripId;
    private String creatorUid;
    private String creatorName;
    private String pickup;
    private String destination;
    private String date;
    private String time;
    private int availableSeats;
    private String notes;
    private String status;
    private long createdAt;

    // Required empty constructor for Firestore deserialization
    public Trip() {
    }

    public Trip(String tripId, String creatorUid, String creatorName, String pickup,
                String destination, String date, String time, int availableSeats,
                String notes, String status, long createdAt) {
        this.tripId = tripId;
        this.creatorUid = creatorUid;
        this.creatorName = creatorName;
        this.pickup = pickup;
        this.destination = destination;
        this.date = date;
        this.time = time;
        this.availableSeats = availableSeats;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getCreatorUid() {
        return creatorUid;
    }

    public void setCreatorUid(String creatorUid) {
        this.creatorUid = creatorUid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getPickup() {
        return pickup;
    }

    public void setPickup(String pickup) {
        this.pickup = pickup;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        map.put("tripId", tripId);
        map.put("creatorUid", creatorUid);
        map.put("creatorName", creatorName);
        map.put("pickup", pickup);
        map.put("destination", destination);
        map.put("date", date);
        map.put("time", time);
        map.put("availableSeats", availableSeats);
        map.put("notes", notes != null ? notes : "");
        map.put("status", status != null ? status : "OPEN");
        map.put("createdAt", createdAt);
        return map;
    }
}