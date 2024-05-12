package com.dobie.backend.domain.project.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectWithFileUpdateRequestDto {
    private String projectId;
    private String projectName;

    private String projectDomain;
    private boolean usingHttps;

    private GitRequestDto git;
    private Map<String, BackendRequestDto> backendMap;
    private FrontendRequestDto frontend;
    private Map<String, DatabaseRequestDto> databaseMap;
    private List<FileUpdateDto> filePathList;
}
