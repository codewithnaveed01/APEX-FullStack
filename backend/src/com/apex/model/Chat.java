package com.apex.model;

import com.google.gson.JsonObject;

import java.util.List;

/** A support chat thread between one customer and the admin desk. */
public class Chat {

    private String userId;
    private String userName;
    private List<JsonObject> messages;
    private int unreadAdmin;
    private int unreadUser;
    private String lastTime;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public List<JsonObject> getMessages() { return messages; }
    public void setMessages(List<JsonObject> messages) { this.messages = messages; }
    public int getUnreadAdmin() { return unreadAdmin; }
    public void setUnreadAdmin(int unreadAdmin) { this.unreadAdmin = unreadAdmin; }
    public int getUnreadUser() { return unreadUser; }
    public void setUnreadUser(int unreadUser) { this.unreadUser = unreadUser; }
    public String getLastTime() { return lastTime; }
    public void setLastTime(String lastTime) { this.lastTime = lastTime; }
}
