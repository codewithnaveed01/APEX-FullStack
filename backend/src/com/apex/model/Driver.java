package com.apex.model;

/** A chauffeur on the driver roster. */
public class Driver {

    private long id;
    private String name;
    private String phone;
    private String city;
    private Integer experience;
    private String license;
    private boolean active;

    public Driver() { }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Integer getExperience() { return experience; }
    public void setExperience(Integer experience) { this.experience = experience; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public void validate() {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Driver name is required");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Driver phone is required");
    }
}
