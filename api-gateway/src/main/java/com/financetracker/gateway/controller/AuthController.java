package com.financetracker.gateway.controller;

import com.financetracker.gateway.model.User;
import com.financetracker.gateway.repository.UserRepository;
import com.financetracker.gateway.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {

    private static final Logger logger = Logger.getLogger(AuthController.class.getName());
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with username and password and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public Mono<ResponseEntity<Map<String, Object>>> login(
            @Parameter(description = "User credentials", required = true)
            @RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        logger.info("Login attempt for user: " + username);

        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty()) {
                        logger.severe("Login failed for user: " + username + " - User not found");
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "Invalid credentials");
                        return Mono.just(ResponseEntity.status(401).body(error));
                    }

                    User user = userOpt.get();
                    // Temporarily allow plain text comparison for testing
                    boolean passwordMatches = passwordEncoder.matches(password, user.getPassword()) || password.equals(user.getPassword());
                    if (!passwordMatches) {
                        logger.severe("Login failed for user: " + username + " - Invalid password");
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "Invalid credentials");
                        return Mono.just(ResponseEntity.status(401).body(error));
                    }

                    String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                    logger.info("Login successful for user: " + username + " with role: " + user.getRole());

                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId());
                    userInfo.put("username", user.getUsername());
                    userInfo.put("email", user.getEmail());
                    userInfo.put("fullName", user.getFullName());
                    userInfo.put("role", user.getRole());
                    userInfo.put("isActive", user.isActive());
                    userInfo.put("isVerified", user.isVerified());
                    
                    response.put("user", userInfo);
                    return Mono.just(ResponseEntity.ok(response));
                });
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    })
    public Mono<ResponseEntity<Map<String, Object>>> register(
            @Parameter(description = "User registration data", required = true)
            @RequestBody Map<String, String> userData) {
        String username = userData.get("username");
        String password = userData.get("password");
        String email = userData.get("email");
        String fullName = userData.get("fullName");

        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .flatMap(userOpt -> {
                    if (userOpt.isPresent()) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "Username already exists");
                        return Mono.just(ResponseEntity.status(400).body(error));
                    }

                    User newUser = new User();
                    newUser.setUsername(username);
                    newUser.setPassword(passwordEncoder.encode(password));
                    newUser.setEmail(email);
                    newUser.setFullName(fullName);
                    newUser.setRole("USER");
                    newUser.setActive(true);
                    newUser.setVerified(false);

                    User savedUser = userRepository.save(newUser);
                    String token = jwtUtil.generateToken(savedUser.getUsername(), savedUser.getRole());

                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", savedUser.getId());
                    userInfo.put("username", savedUser.getUsername());
                    userInfo.put("email", savedUser.getEmail());
                    userInfo.put("fullName", savedUser.getFullName());
                    userInfo.put("role", savedUser.getRole());
                    userInfo.put("isActive", savedUser.isActive());
                    userInfo.put("isVerified", savedUser.isVerified());
                    
                    response.put("user", userInfo);

                    return Mono.just(ResponseEntity.ok(response));
                });
    }
}

@Schema(description = "Login response containing JWT token and user information")
class LoginResponse {
    @Schema(description = "JWT authentication token")
    private String token;
    
    @Schema(description = "User information")
    private UserInfo user;
    
    // Getters and setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}

@Schema(description = "User information")
class UserInfo {
    @Schema(description = "User ID")
    private Long id;
    
    @Schema(description = "Username")
    private String username;
    
    @Schema(description = "User email")
    private String email;
    
    @Schema(description = "Full name")
    private String fullName;
    
    @Schema(description = "User role (ADMIN or USER)")
    private String role;
    
    @Schema(description = "Account active status")
    private boolean isActive;
    
    @Schema(description = "Email verification status")
    private boolean isVerified;
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }
}
