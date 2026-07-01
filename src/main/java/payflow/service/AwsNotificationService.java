package payflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsNotificationService {

    private final SnsClient snsClient;

    @Value("${aws.sns.topic-arn}")
    private String topicArn;

    public void notifyPaymentFinished(String paymentId, String status) {
        try {
            String message = String.format(
                    "{\"paymentId\": \"%s\", \"status\": \"%s\"}",
                    paymentId, status
            );

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .subject("Payment " + status)
                    .build();

            snsClient.publish(request);
            log.info("Notificação enviada ao SNS: paymentId={} status={}", paymentId, status);

        } catch (Exception e) {
            log.error("Erro ao enviar notificação ao SNS", e);
        }
    }
}