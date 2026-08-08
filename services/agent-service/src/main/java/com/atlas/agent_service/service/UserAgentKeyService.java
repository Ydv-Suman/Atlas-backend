package com.atlas.agent_service.service;

import com.atlas.agent_service.dto.KeyResponse;
import com.atlas.agent_service.dto.SaveKeyRequest;
import com.atlas.agent_service.entity.UserAgentKey;
import com.atlas.agent_service.repository.UserAgentKeyRepository;
import com.atlas.agent_service.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAgentKeyService {

    private final UserAgentKeyRepository keyRepository;
    private final EncryptionUtil encryptionUtil;

    @Transactional
    public KeyResponse saveKey(UUID userId, SaveKeyRequest request) {
        String provider = request.provider().toLowerCase();
        String encrypted = encryptionUtil.encrypt(request.apiKey());
        String hint = buildHint(request.apiKey());

        UserAgentKey key = keyRepository.findByUserIdAndProvider(userId, provider)
                .orElse(new UserAgentKey());

        key.setUserId(userId);
        key.setProvider(provider);
        key.setEncryptedKey(encrypted);
        key.setKeyHint(hint);

        key = keyRepository.save(key);
        return toResponse(key);
    }

    public List<KeyResponse> listKeys(UUID userId) {
        return keyRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteKey(UUID userId, String provider) {
        keyRepository.deleteByUserIdAndProvider(userId, provider.toLowerCase());
    }

    public String decryptKey(UUID userId, String provider) {
        UserAgentKey key = keyRepository.findByUserIdAndProvider(userId, provider.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No API key for provider: " + provider));
        return encryptionUtil.decrypt(key.getEncryptedKey());
    }

    private String buildHint(String apiKey) {
        if (apiKey.length() <= 6) return "***";
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 3);
    }

    private KeyResponse toResponse(UserAgentKey key) {
        return new KeyResponse(key.getId(), key.getProvider(), key.getKeyHint(), key.getCreatedAt());
    }
}
