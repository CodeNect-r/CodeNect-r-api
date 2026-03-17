package com.lovable.project_service.service;

import com.lovable.project_service.entity.ProjectFile;
import com.lovable.project_service.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ZipDownloadService {

    private final ProjectFileRepository projectFileRepository;

    public byte[] buildProjectZip(String projectId) {
        List<ProjectFile> files = projectFileRepository.findByProjectId(projectId);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                for (ProjectFile file : files) {
                    ZipEntry entry = new ZipEntry(file.getFilePath());
                    zos.putNextEntry(entry);
                    zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Unable to create ZIP", ex);
        }
    }
}
