package com.loganalyzer.auth.controller;

import com.loganalyzer.auth.model.Project;
import com.loganalyzer.auth.model.User;
import com.loganalyzer.auth.repository.ProjectRepository;
import com.loganalyzer.auth.repository.UserRepository;
import com.loganalyzer.auth.security.ApiKeyHasher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ProjectController(ProjectRepository projectRepository,
                             UserRepository userRepository,
                             ApiKeyHasher apiKeyHasher) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = authenticatedEmail();
        if (email == null || name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name is required"));
        }

        Optional<User> ownerOpt = userRepository.findByEmail(email);
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Project project = new Project();
        project.setName(name);
        project.setOwner(ownerOpt.get());
        String rawApiKey = project.getApiKey();
        project.setApiKeyHash(apiKeyHasher.hash(rawApiKey));
        projectRepository.save(project);

        return ResponseEntity.ok(Map.of(
                "projectId", project.getId(),
                "name", project.getName(),
                "apiKey", rawApiKey
        ));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        String email = authenticatedEmail();
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Optional<User> ownerOpt = userRepository.findByEmail(email);
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        List<Project> projects = projectRepository.findAllByOwnerId(ownerOpt.get().getId());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/key/{apiKey}")
    public ResponseEntity<?> validateKey(@PathVariable String apiKey) {
        return findProjectByApiKey(apiKey)
                .map(project -> ResponseEntity.ok(Map.of(
                        "projectId", project.getId(),
                        "name", project.getName()
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Invalid API Key")));
    }

    @PostMapping("/key/validate")
    public ResponseEntity<?> validateKeyHeader(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {
        Optional<Project> project = findProjectByApiKey(apiKey);
        if (project.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Invalid API Key"));
        }
        return ResponseEntity.ok(Map.of("projectId", project.get().getId()));
    }

    @GetMapping("/{projectId}/access")
    public ResponseEntity<?> checkAccess(@PathVariable Long projectId) {
        String email = authenticatedEmail();
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Optional<Project> project = userRepository.findByEmail(email)
                .flatMap(user -> projectRepository.findById(projectId)
                        .filter(candidate -> candidate.getOwner().getId().equals(user.getId())));
        if (project.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        return ResponseEntity.ok(Map.of("projectId", project.get().getId()));
    }

    @PostMapping("/{projectId}/key/rotate")
    public ResponseEntity<?> rotateApiKey(@PathVariable Long projectId) {
        if (!ownsProject(projectId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        }

        Optional<Project> project = projectRepository.findById(projectId);
        if (project.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        }

        String rawApiKey = UUID.randomUUID().toString();
        project.get().setApiKey(rawApiKey);
        project.get().setApiKeyHash(apiKeyHasher.hash(rawApiKey));
        project.get().setApiKeyActive(true);
        projectRepository.save(project.get());
        return ResponseEntity.ok(Map.of("apiKey", rawApiKey));
    }

    @PostMapping("/{projectId}/key/revoke")
    public ResponseEntity<?> revokeApiKey(@PathVariable Long projectId) {
        if (!ownsProject(projectId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        }

        Optional<Project> project = projectRepository.findById(projectId);
        if (project.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        }

        project.get().setApiKeyActive(false);
        projectRepository.save(project.get());
        return ResponseEntity.ok(Map.of("message", "API key revoked"));
    }

    private String authenticatedEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String email)) {
            return null;
        }
        return email;
    }

    private Optional<Project> findProjectByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        Optional<Project> hashed = projectRepository.findByApiKeyHash(apiKeyHasher.hash(apiKey))
                .filter(Project::isApiKeyActive);
        if (hashed.isPresent()) {
            return hashed;
        }
        Optional<Project> legacy = projectRepository.findByApiKey(apiKey);
        if (legacy.isPresent() && legacy.get().isApiKeyActive()) {
            return legacy;
        }
        return Optional.empty();
    }

    private boolean ownsProject(Long projectId) {
        String email = authenticatedEmail();
        return email != null && userRepository.findByEmail(email)
                .flatMap(user -> projectRepository.findById(projectId)
                        .filter(project -> project.getOwner().getId().equals(user.getId())))
                .isPresent();
    }
}
