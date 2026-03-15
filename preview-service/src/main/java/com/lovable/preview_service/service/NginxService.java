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

    public NginxService(@Value("${nginx.dynamic.conf.dir}") String nginxConfDir) {
        this.nginxConfDir = nginxConfDir;
    }

    public void createDomainRouting(String domain, int port) {
        try {
            String config = "server {\n" +
                    "    listen 80;\n" +
                    "    server_name " + domain + ";\n" +
                    "\n" +
                    "    location / {\n" +
                    "        proxy_pass http://host.docker.internal:" + port + ";\n" +
                    "        proxy_set_header Host $host;\n" +
                    "        proxy_set_header X-Real-IP $remote_addr;\n" +
                    "    }\n" +
                    "}";

            // Ensure the directory exists
            Path dir = Path.of(nginxConfDir);
            Files.createDirectories(dir);
            Path confPath = dir.resolve(domain + ".conf");
            Files.writeString(confPath, config);

            reloadNginx();

            log.info("Nginx routing created for {} -> {}", domain, port);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create nginx config", e);
        }
    }

    private void reloadNginx() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", "nginx", "nginx", "-s", "reload"
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
                throw new RuntimeException("nginx reload failed with exit code " + exitCode + ":\n" + output);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload nginx", e);
        }
    }
}