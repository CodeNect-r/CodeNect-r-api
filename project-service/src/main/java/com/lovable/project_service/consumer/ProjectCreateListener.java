//package com.lovable.project_service.consumer;
//
//import com.lovable.project_service.dto.AiRequestEvent;
//import com.lovable.project_service.dto.CreateProjectEvent;
//import com.lovable.project_service.dto.OperationType;
//import com.lovable.project_service.dto.ProjectCreatedEvent;
//import com.lovable.project_service.entity.Project;
//import com.lovable.project_service.entity.ProjectCreateRequestRecord;
//import com.lovable.project_service.repository.ProjectCreateRequestRecordRepository;
//import com.lovable.project_service.repository.ProjectRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Component
//@RequiredArgsConstructor
//public class ProjectCreateListener {
//
//    private final ProjectRepository projectRepository;
//    private final ProjectCreateRequestRecordRepository requestRecordRepository;
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//
//    @Transactional
//    @KafkaListener(topics = "project.create.request", groupId = "project-service")
//    public void consume(CreateProjectEvent event) {
//        System.out.println("request received");
//        // 1. IDEMPOTENCY CHECK
//        ProjectCreateRequestRecord existingRecord =
//                requestRecordRepository.findByRequestId(event.getRequestId()).orElse(null);
//
//        if (existingRecord != null) {
//            // Request already handled -> just return same projectId, do NOT create again,
//            // do NOT publish ai.request again
//            ProjectCreatedEvent response = ProjectCreatedEvent.builder()
//                    .requestId(existingRecord.getRequestId())
//                    .projectId(existingRecord.getProjectId())
//                    .userEmail(existingRecord.getUserEmail())
//                    .build();
//
//            kafkaTemplate.send("project.create.response", response.getRequestId(), response);
//            return;
//        }
//
//        // 2. CREATE PROJECT
//        Project project = Project.builder()
//                .name(event.getName())
//                .description(event.getDescription())
//                .ownerEmail(event.getUserEmail())
//                .status("PROCESSING")
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//        System.out.println("project created:"+ project);
//        projectRepository.save(project);
//
//        // 3. STORE REQUEST MAPPING
//        ProjectCreateRequestRecord record = ProjectCreateRequestRecord.builder()
//                .requestId(event.getRequestId())
//                .userEmail(event.getUserEmail())
//                .projectId(project.getId())
//                .status("CREATED")
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//        System.out.println("store request mapping :"+ record);
//        requestRecordRepository.save(record);
//
//        // 4. RESPOND TO CHAT-SERVICE
//        ProjectCreatedEvent response = ProjectCreatedEvent.builder()
//                .requestId(event.getRequestId())
//                .projectId(project.getId())
//                .userEmail(event.getUserEmail())
//                .build();
//
//        kafkaTemplate.send("project.create.response", response.getRequestId(), response);
//
//        // 5. ONLY PROJECT-SERVICE SENDS INITIAL AI REQUEST
//        AiRequestEvent aiRequest = AiRequestEvent.builder()
//                .eventId(UUID.randomUUID().toString())
//                .eventVersion("v1")
//                .projectId(project.getId())
//                .userEmail(event.getUserEmail())
//                .sessionId(null)
//                .prompt(event.getDescription())
//                .operationType(OperationType.INITIAL_PROJECT)
//                .build();
//        System.out.println("ai request:"+ aiRequest);
//
//        kafkaTemplate.send("ai.request", project.getId(), aiRequest);
//
//        // 6. MARK AI REQUEST SENT
//        record.setStatus("AI_REQUEST_SENT");
//        record.setUpdatedAt(LocalDateTime.now());
//        requestRecordRepository.save(record);
//    }
//}