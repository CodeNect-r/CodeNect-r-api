package com.lovable.project_service.repository;


import com.lovable.project_service.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByOwnerEmail(String ownerEmail);
}