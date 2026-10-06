package com.financetracker.gateway.controller;

import com.financetracker.gateway.model.User;
import com.financetracker.gateway.model.UserCreateRequest;
import com.financetracker.gateway.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Admin-only user management endpoints")
public class AdminController {

    private static final Logger logger = Logger.getLogger(AdminController.class.getName());
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Retrieve all users in the system (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    public Mono<ResponseEntity<List<User>>> getAllUsers() {
        return Mono.fromCallable(() -> userRepository.findAll())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/users")
    @Operation(summary = "Create user", description = "Create a new user account (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created successfully")
    })
    public Mono<ResponseEntity<User>> createUser(
            @Parameter(description = "User creation data", required = true)
            @RequestBody UserCreateRequest request) {
        return Mono.fromCallable(() -> {
            User newUser = new User();
            newUser.setUsername(request.getUsername());
            newUser.setPassword(passwordEncoder.encode(request.getPassword()));
            newUser.setEmail(request.getEmail());
            newUser.setFullName(request.getFullName());
            newUser.setRole(request.getRole());
            newUser.setActive(true);
            newUser.setVerified(true);
            
            User savedUser = userRepository.save(newUser);
            logger.info("User created: " + savedUser.getUsername() + " with role: " + savedUser.getRole());
            return savedUser;
        }).map(ResponseEntity::ok);
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Update user role", description = "Update a user's role (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully")
    })
    public Mono<ResponseEntity<User>> updateUserRole(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "New role", required = true)
            @RequestBody Map<String, String> roleUpdate) {
        return Mono.fromCallable(() -> userRepository.findById(userId))
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty()) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    
                    User user = userOpt.get();
                    user.setRole(roleUpdate.get("role"));
                    User updatedUser = userRepository.save(user);
                    logger.info("User role updated: " + user.getUsername() + " to role: " + user.getRole());
                    return Mono.just(ResponseEntity.ok(updatedUser));
                });
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete user", description = "Delete a user account (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public Mono<ResponseEntity<Void>> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId) {
        return Mono.fromCallable(() -> userRepository.existsById(userId))
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    
                    userRepository.deleteById(userId);
                    logger.info("User deleted with ID: " + userId);
                    return Mono.just(ResponseEntity.ok().build());
                });
    }
}
