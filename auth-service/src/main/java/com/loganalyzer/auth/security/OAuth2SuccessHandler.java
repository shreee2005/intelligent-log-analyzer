package com.loganalyzer.auth.security;

import com.loganalyzer.auth.model.User;
import com.loganalyzer.auth.model.Role;
import com.loganalyzer.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        
        String email = oauthUser.getAttribute("email");
        if (email == null) {
            String login = oauthUser.getAttribute("login");
            email = (login != null ? login : "oauth_user_" + oauthUser.getName()) + "@github.com";
        }

        final String finalEmail = email;
        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setPasswordHash("OAUTH_USER_NO_PASSWORD");
            newUser.setRoles(Set.of(Role.ROLE_OWNER));
            return userRepository.save(newUser);
        });

        String token = jwtUtil.generateToken(user);
        
        String targetUrl = "http://localhost:5173/#token="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8)
                + "&email="
                + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
