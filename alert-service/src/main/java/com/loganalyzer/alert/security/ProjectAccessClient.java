package com.loganalyzer.alert.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProjectAccessClient {

    private final RestClient restClient;

    public ProjectAccessClient(
            @Value("${auth.service.url:http://localhost:8091}") String authServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(authServiceUrl).build();
    }

    public boolean hasAccess(Long projectId, String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        try {
            restClient.get()
                    .uri("/api/projects/{projectId}/access", projectId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }
}
