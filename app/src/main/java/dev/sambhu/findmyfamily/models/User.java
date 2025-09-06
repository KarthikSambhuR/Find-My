package dev.sambhu.findmyfamily.models;

import java.io.Serializable;
import java.util.List;

public class User implements Serializable {
    private String uid;
    private String familyId;
    private String displayName;
    private String fcmToken;
    private LastKnown lastKnown;
    private List<PhoneNumber> phoneNumbers;
    private String photoUrl;

    public User() {
        // Required for Firebase
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public LastKnown getLastKnown() {
        return lastKnown;
    }

    public void setLastKnown(LastKnown lastKnown) {
        this.lastKnown = lastKnown;
    }

    public List<PhoneNumber> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<PhoneNumber> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
