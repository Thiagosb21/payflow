package payflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import payflow.dto.request.PaymentRequest;
import payflow.dto.response.PaymentResponse;
import payflow.entity.Payment;
import payflow.entity.PaymentEvent;
import payflow.enums.PaymentStatus;
import payflow.exception.PaymentNotFoundException;
import payflow.repository.PaymentEventRepository;
import payflow.repository.PaymentRepository;
import payflow.dto.PaymentEventMessage;
import payflow.kafka.producer.PaymentProducer;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentProducer paymentProducer;

    public PaymentResponse create(PaymentRequest request) {
        Payment payment = createPaymentInDb(request);

        paymentProducer.send(PaymentEventMessage.builder()
                .paymentId(payment.getId())
                .payerId(payment.getPayerId())
                .payeeId(payment.getPayeeId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build());

        return toResponse(payment);
    }

    @Transactional
    public Payment createPaymentInDb(PaymentRequest request) {
        Payment payment = Payment.builder()
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Pagamento criado: {}", payment.getId());

        saveEvent(payment, null, PaymentStatus.PENDING, "PAYMENT_CREATED");

        return payment;
    }

    public PaymentResponse findById(UUID id) {
        return paymentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PaymentResponse> findByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateStatus(UUID id, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        PaymentStatus oldStatus = payment.getStatus();
        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        saveEvent(payment, oldStatus, newStatus, "STATUS_UPDATED");
        log.info("Status atualizado: {} → {}", oldStatus, newStatus);
    }

    private void saveEvent(Payment payment, PaymentStatus oldStatus,
                           PaymentStatus newStatus, String eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .eventType(eventType)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .build();
        paymentEventRepository.save(event);
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setPayerId(payment.getPayerId());
        response.setPayeeId(payment.getPayeeId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());
        response.setDescription(payment.getDescription());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}