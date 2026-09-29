# Intelligent Log Analyzer - OAuth2 Flow Documentation

## Overview

This document details the OAuth2 authentication flow for GitHub and Google providers, including the bug fix for GitHub private email handling.

---

## OAuth2 Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Browser   │────▶│ API Gateway │────▶│ Auth Service│────▶│  Provider   │
│  (Frontend) │     │   :8080     │     │   :8091     │     │ (GitHub/    │
└─────────────┘     └─────────────┘     └─────────────┘     │  Google)    │
       ▲                                           │         └─────────────┘
       │                                           │                │
       │         ┌─────────────┐                   │                │
       └────────▶│  Frontend   │◀──────────────────┘                │
                 │  (localStorage)                              │
                 └─────────────┘                                │
                      │                                         │
                      ▼                                         │
               ┌─────────────┐                                  │
               │   Dashboard │                                  │
               │   APIs      │                                  │
               └─────────────┘                                  │
```

---

## GitHub OAuth2 Flow (Detailed)

### 1. Initiation (Frontend)
```javascript
// User clicks "Continue with GitHub"
window.location.href = `${AUTH_BASE_URL}/oauth2/authorization/github`;

// Redirects to GitHub:
// https://github.com/login/oauth/authorize?
//   client_id=YOUR_CLIENT_ID&
//   redirect_uri=http://localhost:8091/login/oauth2/code/github&
//   scope=read:user user:email&
//   response_type=code&
//   state=RANDOM_STATE
```

### 2. User Consent (GitHub)
- User authorizes application
- GitHub redirects back with authorization code

### 3. Callback (Auth Service)
```
GET /login/oauth2/code/github?code=AUTH_CODE&state=STATE
```

Spring Security handles:
- Exchange code for access token
- Call GitHub `/user` endpoint
- Create `OAuth2AuthenticationToken`

### 4. Success Handler (Custom - OAuth2SuccessHandler)

```java
// Our custom logic in OAuth2SuccessHandler.onAuthenticationSuccess()

// Step 1: Extract registration ID
String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "github"

// Step 2: Try direct email from /user endpoint
String email = oauthUser.getAttribute("email");
// ⚠️ This is NULL if user's email is PRIVATE on GitHub!

// Step 3: If null & github, fetch from /user/emails
if (email == null && "github".equals(registrationId)) {
    email = fetchGitHubEmail(authentication);
}

// Step 4: fetchGitHubEmail() implementation:
private String fetchGitHubEmail(Authentication authentication) {
    // 1. Get authorized client
    OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
        registrationId, oauthToken.getName());
    
    // 2. Get access token
    String accessToken = client.getAccessToken().getTokenValue();
    
    // 3. Call GitHub /user/emails with Bearer token
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    ResponseEntity<List> response = restTemplate.exchange(
        "https://api.github.com/user/emails",
        HttpMethod.GET, new HttpEntity<>(headers), List.class);
    
    // 4. Parse emails array
    List<Map<String, Object>> emails = response.getBody();
    
    // 5. Priority order:
    //    a. primary=true AND verified=true
    //    b. verified=true (any)
    //    c. first email
    for (Map e : emails) {
        if (Boolean.TRUE.equals(e.get("primary")) 
            && Boolean.TRUE.equals(e.get("verified"))) {
            return (String) e.get("email");
        }
    }
    for (Map e : emails) {
        if (Boolean.TRUE.equals(e.get("verified"))) {
            return (String) e.get("email");
        }
    }
    return null;
}
```

### 5. User Lookup/Create
```java
// Find or create user by email (case-insensitive)
User user = userRepository.findByEmail(email.toLowerCase())
    .orElseGet(() -> {
        User newUser = new User();
        newUser.setEmail(email.toLowerCase());
        newUser.setPasswordHash("OAUTH_USER_NO_PASSWORD"); // Marker
        newUser.setRoles(Set.of(Role.ROLE_OWNER));
        return userRepository.save(newUser);
    });
```

### 6. JWT Generation
```java
String token = jwtUtil.generateToken(user);
// Payload: { sub: email, roles: [ROLE_OWNER], iat, exp }
```

### 7. Redirect to Frontend
```java
String targetUrl = frontendUrl + "/#token=" 
    + URLEncoder.encode(token, UTF_8)
    + "&email=" + URLEncoder.encode(user.getEmail(), UTF_8);

getRedirectStrategy().sendRedirect(request, response, targetUrl);
// Redirects to: http://localhost:5173/#token=JWT&email=user%40domain.com
```

### 8. Frontend Token Handling (App.jsx)
```javascript
useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const urlToken = params.get('token');
    const urlEmail = params.get('email');
    if (urlToken && urlEmail) {
        saveSession(urlToken, urlEmail);
        window.history.replaceState({}, document.title, window.location.pathname);
    }
}, []);

const saveSession = (jwtToken, userEmail) => {
    localStorage.setItem('token', jwtToken);
    localStorage.setItem('email', userEmail);
    setToken(jwtToken);
    setEmail(userEmail);
    setCurrentView('projects');
};
```

---

## Google OAuth2 Flow

Similar to GitHub but simpler - Google always returns email in `/userinfo`:

```java
// Google user info from: https://www.googleapis.com/oauth2/v3/userinfo
// Always contains: { "sub": "...", "email": "user@gmail.com", "email_verified": true, ... }

// Our handler:
String email = oauthUser.getAttribute("email"); // Always present
// No secondary API call needed
```

---

## Email Handling Logic Summary

| Provider | Email Source | Private Email Handling |
|----------|-------------|----------------------|
| **GitHub** | 1. `/user` (if public) 2. `/user/emails` (Bearer token) | ✅ Fixed - fetches private emails |
| **Google** | `/userinfo` (always includes email) | N/A - always available |

---

## Fallback Email Generation

If all else fails (no email from provider):

```java
// GitHub fallback
String login = oauthUser.getAttribute("login"); // GitHub username
if (login != null) {
    return login + "@github.oauth.local"; // Clearly marked as OAuth-generated
}

// Generic fallback
String subject = oauthUser.getName(); // Provider's unique ID
return "oauth_" + registrationId + "_" + subject + "@oauth.local";
```

---

## Database Impact

### User Record Created
```sql
INSERT INTO users (email, password_hash) 
VALUES ('user@domain.com', 'OAUTH_USER_NO_PASSWORD');

INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_OWNER');
```

### Key Points
- `password_hash = 'OAUTH_USER_NO_PASSWORD'` marks OAuth users
- Cannot login with password (no password set)
- Email is unique - subsequent OAuth logins link to same account
- Roles: Only `ROLE_OWNER` assigned initially

---

## Testing the Flow

### 1. Clear Existing Test Data
```bash
# Run the SQL script
psql -h localhost -U admin -d loganalyzer -f CLEAR_TEST_DATA.sql

# Clear browser localStorage
# In browser console:
localStorage.clear();
```

### 2. Test GitHub Login
1. Open http://localhost:5173
2. Click "Continue with GitHub"
3. Authorize on GitHub
4. Should redirect to dashboard with your GitHub email

### 3. Verify in Database
```sql
SELECT id, email, password_hash, roles FROM users;
-- Should show your GitHub email with OAUTH_USER_NO_PASSWORD
```

### 4. Test Account Linking
1. Logout
2. Login with email/password (if you created account first)
3. Then try GitHub OAuth with same email
4. Should link to existing account (not create duplicate)

---

## Troubleshooting

### "Could not extract email from OAuth2 provider"
- Check auth-service logs for `fetchGitHubEmail` errors
- Verify GitHub OAuth app has `user:email` scope
- Verify callback URL matches exactly

### "redirect_uri_mismatch"
- GitHub: Settings → OAuth Apps → Your App → Authorization callback URL
- Must be: `http://localhost:8091/login/oauth2/code/github`

### "Invalid API Key" after OAuth login
- OAuth creates/links user account
- User must create a project to get API key
- API key is per-project, not per-user

### Duplicate accounts
- Check if GitHub email is public vs private
- Old code created fake emails like `username@github.com`
- New code fetches real email via `/user/emails`
- Clear test data and re-test

---

## Security Considerations

1. **State Parameter** - Spring Security handles CSRF protection via `state`
2. **PKCE** - Not implemented (server-side confidential client)
3. **Token Storage** - Access tokens stored in Spring's `OAuth2AuthorizedClientService` (in-memory by default)
4. **Scope** - GitHub: `read:user user:email` | Google: `email profile`
5. **Email Verification** - Only `verified=true` emails used
6. **Case Sensitivity** - Emails normalized to lowercase for lookup

---

## Configuration Reference

### application.yml
```yaml
oauth2:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
  google:
    client-id: ${GOOGLE_CLIENT_ID:}
    client-secret: ${GOOGLE_CLIENT_SECRET:}

app:
  frontend-url: ${FRONTEND_URL:http://localhost:5173}
```

### Required Scopes
| Provider | Scopes | Purpose |
|----------|--------|---------|
| GitHub | `read:user`, `user:email` | Get profile + private emails |
| Google | `email`, `profile` | Get email + basic profile |

---

## Code Locations

| Component | File |
|-----------|------|
| OAuth2 Client Registration | `auth-service/.../security/OAuth2ClientConfig.java` |
| Success Handler | `auth-service/.../security/OAuth2SuccessHandler.java` |
| Security Config | `auth-service/.../security/SecurityConfig.java` |
| Frontend OAuth Buttons | `frontend-dashboard/src/App.jsx` (lines 218-231) |
| Frontend Token Parsing | `frontend-dashboard/src/App.jsx` (lines 37-45) |

---

## Migration Notes (Bug Fix)

### Before Fix (v1)
```java
String email = oauthUser.getAttribute("email");
if (email == null) {
    String login = oauthUser.getAttribute("login");
    email = (login != null ? login : "oauth_user_" + oauthUser.getName()) + "@github.com";
}
```
**Issues:** Fake emails, silent account linking, no private email support

### After Fix (v2)
```java
// 1. Try direct email
// 2. If GitHub & null → call /user/emails with access token
// 3. Pick primary+verified email
// 4. Fallback: login@github.oauth.local (clearly marked)
// 5. Comprehensive logging
```
**Benefits:** Real emails, proper account linking, debuggable, secure

---

## Future Enhancements

- [ ] PKCE support for public clients (mobile/SPA)
- [ ] Multiple email support (user chooses primary)
- [ ] OAuth2 for GitLab, Bitbucket, Azure AD
- [ ] Account linking UI (link multiple providers to one account)
- [ ] Refresh token handling for long-lived sessions