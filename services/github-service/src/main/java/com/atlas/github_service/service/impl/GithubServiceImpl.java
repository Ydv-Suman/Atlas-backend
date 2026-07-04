package com.atlas.github_service.service.impl;

import com.atlas.github_service.config.GithubOAuthConfig;
import com.atlas.github_service.dto.AuthorizeResponseDto;
import com.atlas.github_service.dto.GithubTokenResponse;
import com.atlas.github_service.dto.GithubUserResponse;
import com.atlas.github_service.entity.GithubConnections;
import com.atlas.github_service.repository.GithubConnectionRepository;
import com.atlas.github_service.service.IGithubService;
import com.atlas.github_service.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GithubServiceImpl implements IGithubService {

    private final GithubOAuthConfig oauthConfig;
    private final GithubConnectionRepository connectionRepository;
    private final EncryptionUtil encryptionUtil;

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final long STATE_EXPIRY_SECONDS = 600; // 10 minutes

    /**
     * Step 1: Build GitHub authorization URL.
     * Encodes email into the state parameter with HMAC signature for CSRF protection.
     */
    @Override
    public AuthorizeResponseDto buildAuthorizationUrl(String email) {
        String state = generateState(email);

        String url = GITHUB_AUTHORIZE_URL
                + "?client_id=" + oauthConfig.getClientId()
                + "&redirect_uri=" + oauthConfig.getRedirectUri()
                + "&scope=" + oauthConfig.getScopes()
                + "&state=" + state;

        return new AuthorizeResponseDto(url);
    }

    /**
     * Step 2: Handle OAuth callback.
     * Verifies state, exchanges code for token, fetches GitHub username,
     * encrypts token, stores connection, returns GitHub username.
     */
    @Override
    public String handleCallback(String code, String state) {
        // Verify state and extract email
        String email = verifyAndExtractEmail(state);

        // Exchange authorization code for access token
        GithubTokenResponse tokenResponse = exchangeCodeForToken(code);

        // Fetch GitHub username using the access token
        GithubUserResponse userResponse = fetchGithubUser(tokenResponse.accessToken());

        // Encrypt and store the connection
        saveConnection(email, userResponse.login(), tokenResponse.accessToken(), tokenResponse.scope());

        return userResponse.login();
    }

    private GithubTokenResponse exchangeCodeForToken(String code) {
        RestClient restClient = RestClient.create();

        GithubTokenResponse response = restClient.post()
                .uri(GITHUB_TOKEN_URL)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("client_id=" + oauthConfig.getClientId()
                        + "&client_secret=" + oauthConfig.getClientSecret()
                        + "&code=" + code)
                .retrieve()
                .body(GithubTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new RuntimeException("Failed to exchange code for access token");
        }

        return response;
    }

    private GithubUserResponse fetchGithubUser(String accessToken) {
        RestClient restClient = RestClient.create();

        GithubUserResponse response = restClient.get()
                .uri(GITHUB_API_URL + "/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(GithubUserResponse.class);

        if (response == null || response.login() == null) {
            throw new RuntimeException("Failed to fetch GitHub user info");
        }

        return response;
    }

    private void saveConnection(String userId, String githubUsername, String accessToken, String scope) {
        // Remove existing connection if re-authorizing
        connectionRepository.findByUserId(userId)
                .ifPresent(connectionRepository::delete);

        GithubConnections connection = new GithubConnections();
        connection.setId(UUID.randomUUID());
        connection.setUserId(userId);
        connection.setGithubUsername(githubUsername);
        connection.setEncryptedAccessToken(encryptionUtil.encrypt(accessToken));
        connection.setScope(scope != null ? scope : oauthConfig.getScopes());
        connection.setAuthorizedAt(Instant.now());

        connectionRepository.save(connection);
    }

    /**
     * Extracts email from the encoded state parameter.
     */
    @Override
    public String getEmailFromState(String encodedState) {
        String decoded = new String(Base64.getUrlDecoder().decode(encodedState), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        return parts[0]; // email is first field
    }

    /**
     * State format: base64url(email|timestamp|hmac)
     * HMAC prevents tampering. Timestamp prevents replay.
     */
    private String generateState(String email) {
        long timestamp = Instant.now().getEpochSecond();
        String payload = email + "|" + timestamp;
        String signature = hmacSha256(payload);
        String state = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(state.getBytes(StandardCharsets.UTF_8));
    }

    private String verifyAndExtractEmail(String encodedState) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(encodedState), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Invalid state parameter");
        }

        String[] parts = decoded.split("\\|");
        if (parts.length != 3) {
            throw new RuntimeException("Invalid state format");
        }

        String email = parts[0];
        long timestamp = Long.parseLong(parts[1]);
        String signature = parts[2];

        // Verify HMAC
        String payload = email + "|" + timestamp;
        String expectedSignature = hmacSha256(payload);
        if (!expectedSignature.equals(signature)) {
            throw new RuntimeException("State signature verification failed");
        }

        // Verify not expired
        if (Instant.now().getEpochSecond() - timestamp > STATE_EXPIRY_SECONDS) {
            throw new RuntimeException("State parameter expired");
        }

        return email;
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    oauthConfig.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
