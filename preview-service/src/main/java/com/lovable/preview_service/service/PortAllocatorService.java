package com.lovable.preview_service.service;

import com.lovable.preview_service.Repository.PreviewInstanceRepository;
import com.lovable.preview_service.entity.PreviewInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortAllocatorService {

    private final PreviewInstanceRepository repository;

    private static final int START_PORT = 30000;
    private static final int END_PORT = 40000;

    public int allocatePort() {

        Set<Integer> usedPorts = repository.findAll()
                .stream()
                .map(PreviewInstance::getPort)
                .collect(Collectors.toSet());

        for (int port = START_PORT; port <= END_PORT; port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }

        throw new RuntimeException("No free ports available");
    }
}