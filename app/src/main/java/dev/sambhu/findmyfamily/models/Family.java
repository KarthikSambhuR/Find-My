package dev.sambhu.findmyfamily.models;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;

public class Family {
    private String familyId;
    private String name;
    private String adminId;
    private List<String> members;
    private String code;
    private Date createdAt;

    public Family() {
        // Required for Firebase
    }

    public Family(String familyId, String name, String adminId, List<String> members, String code) {
        this.familyId = familyId;
        this.name = name;
        this.adminId = adminId;
        this.members = members;
        this.code = code;
    }

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @ServerTimestamp
    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
