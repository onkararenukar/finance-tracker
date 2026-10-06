package com.financetracker.gateway.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    private String fullName;
    
    @Column(nullable = false)
    private String role = "USER";
    
    private boolean active = true;
    
    private boolean verified = false;
    
    @Column(name = "created_at")
    private java.time.Instant createdAt;
    
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.Instant.now();
        updatedAt = java.time.Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
    
    // Custom getters for consistent naming
    public boolean isActive() {
        return active;
    }
    
    public boolean isVerified() {
        return verified;
    }
}
