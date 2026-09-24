package com.apex.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A support chat thread (one per customer) plus its messages. */
public final class ChatThread {
    public final String userId, userName;
    public final String lastTime;
    public final int unreadAdmin, unreadUser;
    public final JsonArray messages;

    public ChatThread(String userId, String userName, String lastTime, int unreadAdmin, int unreadUser, JsonArray messages) {
        this.userId = userId; this.userName = userName; this.lastTime = lastTime;
        this.unreadAdmin = unreadAdmin; this.unreadUser = unreadUser;
        this.messages = messages == null ? new JsonArray() : messages;
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("userId", userId);
        j.addProperty("userName", userName == null ? "" : userName);
        j.add("messages", messages);
        j.addProperty("unreadAdmin", unreadAdmin);
        j.addProperty("unreadUser", unreadUser);
        j.addProperty("lastTime", lastTime == null ? "" : lastTime);
        return j;
    }
}
