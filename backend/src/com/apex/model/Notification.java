package com.apex.model;

/** In-app notification for a customer or for the admin desk. */
public class Notification {

    private String id;
    private String userId;
    private String title;
    private String msg;
    private String time;
    private boolean read;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
