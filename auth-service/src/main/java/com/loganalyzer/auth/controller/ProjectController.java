package com.loganalyzer.auth.controller;

import com.loganalyzer.auth.model.Project;
import com.loganalyzer.auth.model.User;
import com.loganalyzer.auth.repository.ProjectRepository;
import com.loganalyzer.auth.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Optional<User> ownerOpt = userRepository.findByEmail(email);
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Project project = new Project();
        project.setName(name);
        project.setOwner(ownerOpt.get());
        projectRepository.save(project);

        return ResponseEntity.ok(Map.of(
                "projectId", project.getId(),
                "name", project.getName(),
                "apiKey", project.getApiKey()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> ownerOpt = userRepository.findByEmail(email);
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        List<Project> projects = projectRepository.findAllByOwnerId(ownerOpt.get().getId());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/key/{apiKey}")
    public ResponseEntity<?> validateKey(@PathVariable String apiKey) {
        return projectRepository.findByApiKey(apiKey)
                .map(project -> ResponseEntity.ok(Map.of(
                        "projectId", project.getId(),
                        "name", project.getName(),
                        "ownerEmail", project.getOwner().getEmail()
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Invalid API Key")));
    }
}
