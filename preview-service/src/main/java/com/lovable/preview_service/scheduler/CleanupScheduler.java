package com.lovable.preview_service.scheduler;

import com.lovable.preview_service.entity.PreviewInstance;
import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import com.lovable.preview_service.service.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final PreviewInstanceRepository repository;
    private final DockerService dockerService;

    @Scheduled(fixedRate = 600000)
    public void cleanupIdleContainers() {

        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(30);

        List<PreviewInstance> instances =
                repository.findAll()
                        .stream()
                        .filter(i ->
                                "RUNNING".equals(i.getStatus()) &&
                                        i.getUpdatedAt().isBefore(cutoff)
                        )
                        .toList();

        for (PreviewInstance instance : instances) {
            try {
                dockerService.stopContainer(instance.getContainerId());
                dockerService.removeContainer(instance.getContainerId());

                instance.setStatus("STOPPED");
                instance.setUpdatedAt(LocalDateTime.now());

                repository.save(instance);

            } catch (Exception ignored) {}
        }
    }
}