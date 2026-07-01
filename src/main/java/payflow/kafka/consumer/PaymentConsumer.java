package payflow.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import payflow.dto.PaymentEventMessage;
import payflow.enums.PaymentStatus;
import payflow.service.PaymentService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payments", groupId = "payflow-group")
    public void consume(String message) {
        try {
            PaymentEventMessage event = objectMapper.readValue(message, PaymentEventMessage.class);
            log.info("Evento recebido do Kafka: {}", event.getPaymentId());

            paymentService.updateStatus(event.getPaymentId(), PaymentStatus.PROCESSING);

            boolean approved = simulateProcessing(event);

            PaymentStatus finalStatus = approved ? PaymentStatus.APPROVED : PaymentStatus.FAILED;
            paymentService.updateStatus(event.getPaymentId(), finalStatus);

            log.info("Pagamento {} finalizado com status {}", event.getPaymentId(), finalStatus);

        } catch (Exception e) {
            log.error("Erro ao processar evento do Kafka", e);
        }
    }

    private boolean simulateProcessing(PaymentEventMessage event) {
        // Simula um gateway de pagamento: 90% de aprovação
        return Math.random() > 0.1;
    }
}