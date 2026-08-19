package com.loganalyzer.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuth2ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ClientConfig.class);
    private final Environment env;

    public OAuth2ClientConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registrations = new ArrayList<>();

        String googleId = env.getProperty("oauth2.google.client-id");
        String googleSecret = env.getProperty("oauth2.google.client-secret");
        
        log.info("OAuth2 Resolution - Google Client ID: '{}'", googleId);
        
        if (googleId != null && !googleId.trim().isEmpty() && 
            !googleId.equals("google-dummy-client-id") && !googleId.equals("google-dummy")) {
            log.info("Registering Google OAuth2 client...");
            registrations.add(ClientRegistration.withRegistrationId("google")
                    .clientId(googleId)
                    .clientSecret(googleSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("email", "profile")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("Google")
                    .build());
        }

        String githubId = env.getProperty("oauth2.github.client-id");
        String githubSecret = env.getProperty("oauth2.github.client-secret");
        
        log.info("OAuth2 Resolution - GitHub Client ID: '{}'", githubId);
        
        if (githubId != null && !githubId.trim().isEmpty() && 
            !githubId.equals("github-dummy-client-id") && !githubId.equals("github-dummy")) {
            log.info("Registering GitHub OAuth2 client...");
            registrations.add(ClientRegistration.withRegistrationId("github")
                    .clientId(githubId)
                    .clientSecret(githubSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("read:user", "user:email")
                    .authorizationUri("https://github.com/login/oauth/authorize")
                    .tokenUri("https://github.com/login/oauth/access_token")
                    .userInfoUri("https://api.github.com/user")
                    .userNameAttributeName("id")
                    .clientName("GitHub")
                    .build());
        }

        if (registrations.isEmpty()) {
            log.warn("No real OAuth2 client registrations found. Registering dummy fallback...");
            registrations.add(ClientRegistration.withRegistrationId("dummy")
                    .clientId("dummy-client-id")
                    .clientSecret("dummy-client-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("https://example.com/oauth/authorize")
                    .tokenUri("https://example.com/oauth/token")
                    .build());
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }
}
