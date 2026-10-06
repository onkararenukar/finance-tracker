package com.financetracker.gateway.model;

import lombok.Data;

@Data
public class UserCreateRequest {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String role = "USER";
}
