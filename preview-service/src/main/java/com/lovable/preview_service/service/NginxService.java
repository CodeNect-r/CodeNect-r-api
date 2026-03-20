package com.lovable.preview_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class NginxService {

    private final String nginxConfDir;
    private final String nginxContainerName;

    public NginxService(
            @Value("${nginx.dynamic.conf.dir}") String nginxConfDir,
            @Value("${nginx.container.name:nginx}") String nginxContainerName
    ) {
        this.nginxConfDir = nginxConfDir;
        this.nginxContainerName = nginxContainerName;
    }

    public void createDomainRouting(String domain, String containerName) {
        try {
            String config = """
                    server {
                        listen 80;
                        server_name %s;

                        resolver 127.0.0.11 ipv6=off valid=10s;

                        location / {
                            set $upstream %s;
                            proxy_pass http://$upstream:80;
                            proxy_http_version 1.1;
                            proxy_set_header Host $host;
                            proxy_set_header X-Real-IP $remote_addr;
                            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                            proxy_set_header X-Forwarded-Proto $scheme;
                            proxy_set_header Upgrade $http_upgrade;
                            proxy_set_header Connection "upgrade";
                        }
                    }
                    """.formatted(domain, containerName);

            Path dir = Path.of(nginxConfDir);
            Files.createDirectories(dir);

            Path confPath = dir.resolve(domain + ".conf");
            Files.writeString(confPath, config);

            reloadNginx();

            log.info("Nginx routing created for domain={} -> container={}", domain, containerName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create nginx config", e);
        }
    }

    public void removeDomainRouting(String domain) {
        try {
            Path confPath = Path.of(nginxConfDir).resolve(domain + ".conf");
            Files.deleteIfExists(confPath);
            reloadNginx();
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove nginx config for domain: " + domain, e);
        }
    }

    private void reloadNginx() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", nginxContainerName, "nginx", "-s", "reload"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                throw new RuntimeException("nginx reload failed: " + output);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload nginx", e);
        }
    }
}