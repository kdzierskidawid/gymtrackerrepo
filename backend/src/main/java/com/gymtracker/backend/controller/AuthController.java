package com.gymtracker.backend.controller;

import com.gymtracker.backend.model.User;
import com.gymtracker.backend.security.JwtUtil;
import com.gymtracker.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> user) {
        try {
            Integer height = user.get("height") != null ? Integer.valueOf(user.get("height").toString()) : null;
            Integer weight = user.get("weight") != null ? Integer.valueOf(user.get("weight").toString()) : null;
            String gender = user.get("gender") != null ? user.get("gender").toString() : null;
            Integer age = user.get("age") != null ? Integer.valueOf(user.get("age").toString()) : null;
            String email = user.get("email") != null ? user.get("email").toString() : null;
            userService.registerUser(
                user.get("username").toString(),
                user.get("password").toString(),
                height,
                weight,
                gender,
                age,
                email
            );
            return ResponseEntity.ok(Collections.singletonMap("message", "User registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> user) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.get("username"), user.get("password")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtUtil.generateToken(user.get("username"));
            return ResponseEntity.ok(Collections.singletonMap("token", token));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getAccountDetails(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        return userService.getUserDetails(username)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Collections.singletonMap("error", "User not found")));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateAccountDetails(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, String> details) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        Integer height = details.get("height") != null ? Integer.valueOf(details.get("height")) : null;
        Integer weight = details.get("weight") != null ? Integer.valueOf(details.get("weight")) : null;
        String gender = details.get("gender");
        Integer age = details.get("age") != null ? Integer.valueOf(details.get("age")) : null;
        Integer goalWeight = details.get("goalWeight") != null ? Integer.valueOf(details.get("goalWeight")) : null;
        String email = details.get("email");
        try {
            User updated = userService.updateUserDetails(username, height, weight, gender, age, goalWeight, email);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}
