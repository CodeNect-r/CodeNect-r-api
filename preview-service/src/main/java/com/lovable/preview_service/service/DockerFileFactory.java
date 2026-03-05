package com.lovable.preview_service.service;

import com.lovable.preview_service.entity.ProjectType;
import org.springframework.stereotype.Service;

@Service
public class DockerFileFactory {

    public String generate(ProjectType type, String packageManager) {

        String installCommand = switch (packageManager) {
            case "yarn" -> "yarn install";
            case "pnpm" -> "pnpm install";
            default -> "npm install";
        };

        String buildCommand = switch (packageManager) {
            case "yarn" -> "yarn build";
            case "pnpm" -> "pnpm build";
            default -> "npm run build";
        };

        StringBuilder df = new StringBuilder();

        switch (type) {

            // =========================
            // STATIC
            // =========================
            case STATIC -> {
                df.append("FROM nginx:alpine\n");
                df.append("COPY . /usr/share/nginx/html/\n\n");
                df.append("RUN printf 'server { listen 80; location / { root /usr/share/nginx/html; try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf\n\n");
                df.append("EXPOSE 80\n");
                df.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            }

            // =========================
            // VITE / REACT_VITE
            // =========================
            case VITE, REACT_VITE -> {
                df.append("FROM node:20-alpine AS build\n");
                df.append("WORKDIR /app\n");

                df.append("COPY package*.json ./\n");
                df.append("RUN ").append(installCommand).append("\n");
                df.append("COPY . .\n");
                df.append("RUN ").append(buildCommand).append("\n\n");

                df.append("FROM nginx:alpine\n");
                df.append("COPY --from=build /app/dist /usr/share/nginx/html\n\n");
                df.append("RUN printf 'server { listen 80; location / { root /usr/share/nginx/html; try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf\n\n");
                df.append("EXPOSE 80\n");
                df.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            }

            // =========================
            // REACT CRA
            // =========================
            case REACT_CRA -> {
                df.append("FROM node:20-alpine AS build\n");
                df.append("WORKDIR /app\n");

                df.append("COPY package*.json ./\n");
                df.append("RUN ").append(installCommand).append("\n");
                df.append("COPY . .\n");
                df.append("RUN ").append(buildCommand).append("\n\n");

                df.append("FROM nginx:alpine\n");
                df.append("COPY --from=build /app/build /usr/share/nginx/html\n\n");
                df.append("RUN printf 'server { listen 80; location / { root /usr/share/nginx/html; try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf\n\n");
                df.append("EXPOSE 80\n");
                df.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
            }

            // =========================
            // NODE
            // =========================
            case NODE -> {
                df.append("FROM node:20-alpine\n");
                df.append("WORKDIR /app\n");
                df.append("ENV PORT=80\n\n");

                df.append("COPY package*.json ./\n");
                df.append("RUN ").append(installCommand).append("\n");
                df.append("COPY . .\n\n");

                df.append("EXPOSE 80\n");
                df.append("CMD [\"npm\", \"start\"]\n");
            }

            // =========================
            // NEXT
            // =========================
            case NEXT -> {
                df.append("FROM node:20-alpine AS build\n");
                df.append("WORKDIR /app\n");

                df.append("COPY package*.json ./\n");
                df.append("RUN ").append(installCommand).append("\n");
                df.append("COPY . .\n");
                df.append("RUN ").append(buildCommand).append("\n\n");

                df.append("FROM node:20-alpine\n");
                df.append("WORKDIR /app\n");
                df.append("ENV PORT=80\n\n");

                df.append("COPY --from=build /app ./\n\n");

                df.append("EXPOSE 80\n");
                df.append("CMD [\"npm\", \"start\"]\n");
            }

            default -> throw new RuntimeException("Unsupported project type");
        }

        return df.toString();
    }
}