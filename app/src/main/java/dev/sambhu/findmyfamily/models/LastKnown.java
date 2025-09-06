package dev.sambhu.findmyfamily.models;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

public class LastKnown implements Serializable {
    // Renamed from 'UpdatedAt' to 'updatedAt'
    private Date updatedAt;
    private long battery;
    private double latitude;
    private double longitude;

    public LastKnown() {
        // Required for Firebase
    }

    @ServerTimestamp
    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getBattery() {
        return battery;
    }

    public void setBattery(long battery) {
        this.battery = battery;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}