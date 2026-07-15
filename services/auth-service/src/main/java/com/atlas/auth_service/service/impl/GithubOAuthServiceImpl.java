package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.config.GithubOAuthConfig;
import com.atlas.auth_service.dto.AuthorizeResponseDto;
import com.atlas.auth_service.dto.GithubTokenResponse;
import com.atlas.auth_service.dto.GithubUserResponse;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.entity.GithubConnections;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.repository.GithubConnectionRepository;
import com.atlas.auth_service.service.IGithubOAuthService;
import com.atlas.auth_service.util.EncryptionUtil;
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
public class GithubOAuthServiceImpl implements IGithubOAuthService {

    private final GithubOAuthConfig oauthConfig;
    private final GithubConnectionRepository connectionRepository;
    private final AtlasUserRespsitory userRepository;
    private final EncryptionUtil encryptionUtil;

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final long STATE_EXPIRY_SECONDS = 600; // 10 minutes

    @Override
    public AuthorizeResponseDto buildAuthorizationUrl(String username) {
        String state = generateState(username);

        String url = GITHUB_AUTHORIZE_URL
                + "?client_id=" + oauthConfig.getClientId()
                + "&redirect_uri=" + oauthConfig.getRedirectUri()
                + "&scope=" + oauthConfig.getScopes()
                + "&state=" + state
                + "&prompt=consent";

        return new AuthorizeResponseDto(url);
    }

    @Override
    public String handleCallback(String code, String state) {
        String username = verifyAndExtractUsername(state);

        GithubTokenResponse tokenResponse = exchangeCodeForToken(code);

        GithubUserResponse userResponse = fetchGithubUser(tokenResponse.accessToken());

        saveConnection(username, userResponse.login(), tokenResponse.accessToken(), tokenResponse.scope());

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

    private void saveConnection(String username, String githubUsername, String accessToken, String scope) {
        UUID userId = resolveUserId(username);

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

    @Override
    public String getDecryptedToken(String username) {
        UUID userId = resolveUserId(username);
        GithubConnections connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No GitHub connection found for user: " + username));
        return encryptionUtil.decrypt(connection.getEncryptedAccessToken());
    }

    private UUID resolveUserId(String username) {
        AtlasUsers user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getId();
    }

    @Override
    public String getUsernameFromState(String encodedState) {
        String decoded = new String(Base64.getUrlDecoder().decode(encodedState), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        return parts[0];
    }

    private String generateState(String username) {
        long timestamp = Instant.now().getEpochSecond();
        String payload = username + "|" + timestamp;
        String signature = hmacSha256(payload);
        String state = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(state.getBytes(StandardCharsets.UTF_8));
    }

    private String verifyAndExtractUsername(String encodedState) {
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

        String username = parts[0];
        long timestamp = Long.parseLong(parts[1]);
        String signature = parts[2];

        String payload = username + "|" + timestamp;
        String expectedSignature = hmacSha256(payload);
        if (!expectedSignature.equals(signature)) {
            throw new RuntimeException("State signature verification failed");
        }

        if (Instant.now().getEpochSecond() - timestamp > STATE_EXPIRY_SECONDS) {
            throw new RuntimeException("State parameter expired");
        }

        return username;
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
