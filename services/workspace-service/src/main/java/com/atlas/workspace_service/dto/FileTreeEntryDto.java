package com.atlas.workspace_service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileTreeEntryDto {
    private String name;
    private String path;
    private String type;
    private Long size;
}
