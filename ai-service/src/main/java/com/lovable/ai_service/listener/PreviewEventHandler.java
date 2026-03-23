package com.lovable.ai_service.listener;

import com.lovable.ai_service.event.PreviewTriggerEvent;
import com.lovable.ai_service.producer.AiResponseProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class PreviewEventHandler {

    private final AiResponseProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(PreviewTriggerEvent event) {

        producer.sendResponse(
                event.getRequest(),
                event.getSession(),
                event.getFiles(),
                event.getFramework()
        );
    }
}