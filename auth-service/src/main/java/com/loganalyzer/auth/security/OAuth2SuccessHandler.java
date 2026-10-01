package com.loganalyzer.auth.security;

import com.loganalyzer.auth.model.User;
import com.loganalyzer.auth.model.Role;
import com.loganalyzer.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final String frontendUrl;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;

    public OAuth2SuccessHandler(JwtUtil jwtUtil,
                                UserRepository userRepository,
                                @Value("${app.frontend-url:http://localhost:3001}") String frontendUrl,
                                OAuth2AuthorizedClientService authorizedClientService,
                                RestTemplate restTemplate) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = restTemplate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        
        // Extract registration ID (google, github, etc.)
        String registrationId = "unknown";
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            registrationId = oauthToken.getAuthorizedClientRegistrationId();
        }

        // Extract email based on provider
        String email = extractEmail(oauthUser, registrationId, authentication);
        
        if (email == null || email.isBlank()) {
            log.error("Could not extract email from OAuth2 provider: {}", registrationId);
            String errorUrl = frontendUrl + "/login?error=oauth_email_missing";
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
            return;
        }

        final String finalEmail = email.toLowerCase();
        log.info("OAuth2 login successful for email: {} via provider: {}", finalEmail, registrationId);
        
        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            log.info("Creating new user for OAuth2 email: {}", finalEmail);
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setPasswordHash("OAUTH_USER_NO_PASSWORD");
            newUser.setRoles(Set.of(Role.ROLE_OWNER));
            return userRepository.save(newUser);
        });

        String token = jwtUtil.generateToken(user);
        
        String targetUrl = frontendUrl + "/#token="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8)
                + "&email="
                + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String extractEmail(OAuth2User oauthUser, String registrationId, Authentication authentication) {
        // First try direct email attribute (works for Google, and GitHub if email is public)
        String email = oauthUser.getAttribute("email");
        if (email != null && !email.isBlank()) {
            return email;
        }

        // For GitHub, if email is not in user info, fetch from /user/emails endpoint
        if ("github".equals(registrationId)) {
            email = fetchGitHubEmail(authentication);
            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        // Fallback: generate deterministic email from GitHub login/username
        String login = oauthUser.getAttribute("login");
        if (login != null && !login.isBlank()) {
            return login + "@github.oauth.local";
        }

        // Last resort: use OAuth2 subject (provider-specific unique ID)
        String subject = oauthUser.getName();
        return "oauth_" + registrationId + "_" + subject + "@oauth.local";
    }

    @SuppressWarnings("unchecked")
    private String fetchGitHubEmail(Authentication authentication) {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
                return null;
            }

            // Get the authorized client to access the access token
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), 
                    oauthToken.getName());
            
            if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                log.warn("No authorized client or access token found for GitHub");
                return null;
            }

            String accessToken = authorizedClient.getAccessToken().getTokenValue();

            // Call GitHub's /user/emails endpoint to get all emails
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map<String, Object>> emails = response.getBody();
            if (emails != null) {
                // Find primary verified email
                for (Map<String, Object> emailInfo : emails) {
                    Boolean primary = (Boolean) emailInfo.get("primary");
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    String email = (String) emailInfo.get("email");
                    
                    if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified) && email != null) {
                        log.debug("Found primary verified GitHub email: {}", email);
                        return email;
                    }
                }
                
                // Fallback: any verified email
                for (Map<String, Object> emailInfo : emails) {
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    String email = (String) emailInfo.get("email");
                    if (Boolean.TRUE.equals(verified) && email != null) {
                        log.debug("Found verified GitHub email: {}", email);
                        return email;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub emails: {}", e.getMessage());
        }
        return null;
    }
}
