package com.apex.model;

/** A customer (or partner-owner) account created from the website. */
public class User {

    private String id;
    private String username;
    private String email;
    private String phone;
    private String name;
    private String cnic;
    private String role;
    private String created;

    public User() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
}
