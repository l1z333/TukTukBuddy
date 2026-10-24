package com.example.tuktukbuddy.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String uid;
    private String name;
    private String rollNo;
    private double avgRating;

    // Default constructor required for calls to DataSnapshot.getValue(User.class)
    public User() {
    }

    public User(String uid, String name, String rollNo, double avgRating) {
        this.uid = uid;
        this.name = name;
        this.rollNo = rollNo;
        this.avgRating = avgRating;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRollNo() {
        return rollNo;
    }

    public void setRollNo(String rollNo) {
        this.rollNo = rollNo;
    }

    public double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(double avgRating) {
        this.avgRating = avgRating;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("name", name);
        map.put("rollNo", rollNo);
        map.put("avgRating", avgRating);
        return map;
    }
}