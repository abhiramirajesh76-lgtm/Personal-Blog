package com.blog;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory, cookie-based sessions. There's only one role here
 * (the site owner) — a valid session just means "this browser proved it
 * knows the site password."
 */
public final class SessionManager {

    public static final String COOKIE_NAME = "BLOG_OWNER_SESSION";

    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final SecureRandom random = new SecureRandom();

    public String create() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.add(id);
        return id;
    }

    public boolean isValid(String id) {
        return id != null && sessions.contains(id);
    }

    public void destroy(String id) {
        if (id != null) sessions.remove(id);
    }
}
