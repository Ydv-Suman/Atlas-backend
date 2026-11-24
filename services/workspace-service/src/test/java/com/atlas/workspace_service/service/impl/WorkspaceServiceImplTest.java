package com.atlas.workspace_service.service.impl;

import com.atlas.workspace_service.entity.WorkspaceEntity;
import com.atlas.workspace_service.exception.WorkspaceNotFoundException;
import com.atlas.workspace_service.feign.AuthFeignClient;
import com.atlas.workspace_service.mapper.WorkspaceMapper;
import com.atlas.workspace_service.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceServiceImplTest {
    @Test
    void decodesUtf8IncludingGithubLineBreaksAndEmptyFiles() {
        String text = "const greeting = 'Hello 世界';\n";
        String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        assertEquals(text, WorkspaceServiceImpl.decodeText(encoded.substring(0, 8) + "\n" + encoded.substring(8)));
        assertEquals("", WorkspaceServiceImpl.decodeText(""));
    }

    @Test
    void rejectsBinaryInvalidEncodingAndOversizedFiles() {
        for (String input : new String[] { "AA==", "/w==", "not base64!",
                Base64.getEncoder().encodeToString(new byte[262145]) }) {
            assertThrows(ResponseStatusException.class, () -> WorkspaceServiceImpl.decodeText(input));
        }
    }

    @Test
    void checksOwnershipBeforeFetchingGithubCredentials() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        AuthFeignClient auth = mock(AuthFeignClient.class);
        when(repository.findByIdAndUserId(1L, "other-user")).thenReturn(Optional.empty());
        var service = new WorkspaceServiceImpl(auth, repository, mock(WorkspaceMapper.class));
        assertThrows(WorkspaceNotFoundException.class, () -> service.getFileContent(1L, "README.md", "other-user"));
        verifyNoInteractions(auth);
    }

    @Test
    void rejectsPathsOutsideProjectBeforeFetchingGithubCredentials() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        AuthFeignClient auth = mock(AuthFeignClient.class);
        when(repository.findByIdAndUserId(1L, "owner")).thenReturn(Optional.of(new WorkspaceEntity()));
        var service = new WorkspaceServiceImpl(auth, repository, mock(WorkspaceMapper.class));
        for (String path : new String[] { "../secret", "/etc/passwd", "src/../secret", "src\\secret", "" }) {
            assertThrows(ResponseStatusException.class, () -> service.getFileContent(1L, path, "owner"));
        }
        verifyNoInteractions(auth);
    }
}
