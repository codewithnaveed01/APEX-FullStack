package com.apex.model;

/** A "List your car" partner onboarding application. */
public class OwnerApplication {

    private String id;
    private String userId;
    private String email;
    private String brand;
    private String model;
    private String year;
    private String registration;
    private String condition;
    private Integer photoCount;
    private String status;
    private String note;
    private String created;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getRegistration() { return registration; }
    public void setRegistration(String registration) { this.registration = registration; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
}
