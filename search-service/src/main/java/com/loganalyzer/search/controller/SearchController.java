package com.loganalyzer.search.controller;

import com.loganalyzer.search.model.LogDocument;
import com.loganalyzer.search.repository.LogSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final LogSearchRepository logSearchRepository;

    @GetMapping
    public List<LogDocument> searchLogs(@RequestParam Long projectId, @RequestParam String query) {
        return logSearchRepository.findByProjectIdAndMessageContaining(projectId, query);
    }
    
    @GetMapping("/service")
    public List<LogDocument> searchByService(@RequestParam Long projectId, @RequestParam String serviceId) {
        return logSearchRepository.findByProjectIdAndServiceId(projectId, serviceId);
    }
    
    @GetMapping("/level")
    public List<LogDocument> searchByLevel(@RequestParam Long projectId, @RequestParam String level) {
        return logSearchRepository.findByProjectIdAndLevel(projectId, level);
    }
}
