package com.lovable.project_service.listener;

import com.lovable.project_service.dto.PreviewTriggerEvent;
import com.lovable.project_service.event.PreviewTriggerRequestedEvent;
import com.lovable.project_service.producer.PreviewTriggerProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class PreviewTriggerEventHandler {

    private final PreviewTriggerProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(PreviewTriggerRequestedEvent event) {
        producer.send(PreviewTriggerEvent.builder()
                .projectId(event.getProjectId())
                .snapshotId(event.getSnapshotId())
                .snapshotTime(event.getSnapshotTime())
                .build());
    }
}