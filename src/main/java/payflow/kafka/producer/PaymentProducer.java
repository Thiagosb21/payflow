package payflow.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import payflow.dto.PaymentEventMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProducer {

    private static final String TOPIC = "payments";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(PaymentEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC, message.getPaymentId().toString(), json);
            log.info("Evento publicado no Kafka: {}", message.getPaymentId());
        } catch (Exception e) {
            log.error("Erro ao publicar evento no Kafka", e);
        }
    }
}