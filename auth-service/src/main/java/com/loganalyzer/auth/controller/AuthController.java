package com.loganalyzer.auth.controller;

import com.loganalyzer.auth.model.User;
import com.loganalyzer.auth.model.Role;
import com.loganalyzer.auth.repository.UserRepository;
import com.loganalyzer.auth.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Email already exists"));
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(Set.of(Role.ROLE_OWNER));

        userRepository.save(user);
        return ResponseEntity.ok(Collections.singletonMap("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    String token = jwtUtil.generateToken(user);
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "email", user.getEmail(),
                            "roles", user.getRoles()
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Collections.singletonMap("error", "Invalid credentials")));
    }
}
