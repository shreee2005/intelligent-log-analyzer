package com.loganalyzer.search.controller;

import com.loganalyzer.search.model.LogDocument;
import com.loganalyzer.search.repository.LogSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final LogSearchRepository logSearchRepository;

    @GetMapping
    public List<LogDocument> searchLogs(@RequestParam String query) {
        return logSearchRepository.findByMessageContaining(query);
    }
    
    @GetMapping("/service")
    public List<LogDocument> searchByService(@RequestParam String serviceId) {
        return logSearchRepository.findByServiceId(serviceId);
    }
    
    @GetMapping("/level")
    public List<LogDocument> searchByLevel(@RequestParam String level) {
        return logSearchRepository.findByLevel(level);
    }
}
