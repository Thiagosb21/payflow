package payflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import payflow.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventMessage {
    private UUID paymentId;
    private String payerId;
    private String payeeId;
    private BigDecimal amount;
    private PaymentStatus status;
}