package com.gymtracker.backend.service;

import com.gymtracker.backend.model.User;
import com.gymtracker.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(String username, String password, Integer height, Integer weight, String gender, Integer age, String email) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setHeight(height);
        user.setWeight(weight);
        user.setGender(gender);
        user.setAge(age);
        user.setEmail(email);
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserDetails(String username) {
        return userRepository.findByUsername(username);
    }

    public User updateUserDetails(String username, Integer height, Integer weight, String gender, Integer age, Integer goalWeight, String email) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        user.setHeight(height);
        user.setWeight(weight);
        user.setGender(gender);
        user.setAge(age);
        user.setGoalWeight(goalWeight);
        if (email != null) user.setEmail(email);
        return userRepository.save(user);
    }
}
