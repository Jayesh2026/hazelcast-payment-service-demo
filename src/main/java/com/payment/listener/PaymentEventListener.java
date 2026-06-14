package com.payment.listener;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import com.payment.model.PaymentEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the "payment-events" ITopic (PDF Chapter 5.2 — ITopic).
 *
 * In a multi-node cluster, EVERY member running this listener receives
 * every published PaymentEvent — useful for audit logging, notifications,
 * analytics, etc. Here we just log it for demonstration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener implements MessageListener<PaymentEvent> {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final HazelcastInstance hazelcastInstance;

    @PostConstruct
    public void subscribe() {
        ITopic<PaymentEvent> topic = hazelcastInstance.getTopic(PAYMENT_EVENTS_TOPIC);
        topic.addMessageListener(this);
        log.info("Subscribed to '{}' topic", PAYMENT_EVENTS_TOPIC);
    }

    @Override
    public void onMessage(Message<PaymentEvent> message) {
        PaymentEvent event = message.getMessageObject();
        log.info("[payment-events] txnId={}, eventType={}, status={}, amount={}, timestamp={}",
                event.getTxnId(), event.getEventType(), event.getStatus(),
                event.getAmount(), event.getTimestamp());
    }
}
