package com.financetracker.gateway.security;

import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String SECRET_KEY = "finance-tracker-secret-key-change-in-production-min-32-chars-for-security";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    public String generateToken(String username, String role) {
        // Simple base64 encoded token for testing
        String tokenData = username + ":" + role + ":" + new Date().getTime() + ":" + (new Date().getTime() + EXPIRATION_TIME);
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
    }

    public String extractUsername(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            return parts[0];
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            return parts[1];
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            long expiryTime = Long.parseLong(parts[3]);
            return new Date().getTime() > expiryTime;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token) && extractUsername(token) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
