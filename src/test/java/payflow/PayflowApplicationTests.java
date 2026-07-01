package payflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import payflow.dto.request.PaymentRequest;
import payflow.dto.response.PaymentResponse;
import payflow.entity.Payment;
import payflow.enums.PaymentStatus;
import payflow.exception.PaymentNotFoundException;
import payflow.kafka.producer.PaymentProducer;
import payflow.repository.PaymentEventRepository;
import payflow.repository.PaymentRepository;
import payflow.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayflowApplicationTests {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEventRepository paymentEventRepository;

	@Mock
	private PaymentProducer paymentProducer;

	@InjectMocks
	private PaymentService paymentService;

	@Test
	void shouldCreatePaymentWithPendingStatus() {
		PaymentRequest request = new PaymentRequest();
		request.setPayerId("user-123");
		request.setPayeeId("merchant-456");
		request.setAmount(new BigDecimal("150.00"));
		request.setCurrency("BRL");
		request.setDescription("Test payment");

		Payment savedPayment = Payment.builder()
				.id(UUID.randomUUID())
				.payerId(request.getPayerId())
				.payeeId(request.getPayeeId())
				.amount(request.getAmount())
				.currency(request.getCurrency())
				.status(PaymentStatus.PENDING)
				.description(request.getDescription())
				.build();

		when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
		when(paymentEventRepository.save(any())).thenReturn(null);
		doNothing().when(paymentProducer).send(any());

		PaymentResponse response = paymentService.create(request);

		assertNotNull(response);
		assertEquals(PaymentStatus.PENDING, response.getStatus());
		assertEquals("user-123", response.getPayerId());
		assertEquals("merchant-456", response.getPayeeId());
		assertEquals(new BigDecimal("150.00"), response.getAmount());
		verify(paymentRepository, times(1)).save(any(Payment.class));
		verify(paymentProducer, times(1)).send(any());
	}

	@Test
	void shouldFindPaymentById() {
		UUID id = UUID.randomUUID();
		Payment payment = Payment.builder()
				.id(id)
				.payerId("user-123")
				.payeeId("merchant-456")
				.amount(new BigDecimal("100.00"))
				.currency("BRL")
				.status(PaymentStatus.APPROVED)
				.build();

		when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

		PaymentResponse response = paymentService.findById(id);

		assertNotNull(response);
		assertEquals(id, response.getId());
		assertEquals(PaymentStatus.APPROVED, response.getStatus());
	}

	@Test
	void shouldThrowExceptionWhenPaymentNotFound() {
		UUID id = UUID.randomUUID();
		when(paymentRepository.findById(id)).thenReturn(Optional.empty());

		assertThrows(PaymentNotFoundException.class, () -> paymentService.findById(id));
	}

	@Test
	void shouldReturnAllPayments() {
		Payment p1 = Payment.builder()
				.id(UUID.randomUUID())
				.payerId("user-1")
				.payeeId("merchant-1")
				.amount(new BigDecimal("50.00"))
				.currency("BRL")
				.status(PaymentStatus.APPROVED)
				.build();

		Payment p2 = Payment.builder()
				.id(UUID.randomUUID())
				.payerId("user-2")
				.payeeId("merchant-2")
				.amount(new BigDecimal("200.00"))
				.currency("BRL")
				.status(PaymentStatus.PENDING)
				.build();

		when(paymentRepository.findAll()).thenReturn(List.of(p1, p2));

		List<PaymentResponse> responses = paymentService.findAll();

		assertEquals(2, responses.size());
	}

	@Test
	void shouldUpdatePaymentStatus() {
		UUID id = UUID.randomUUID();
		Payment payment = Payment.builder()
				.id(id)
				.payerId("user-123")
				.payeeId("merchant-456")
				.amount(new BigDecimal("100.00"))
				.currency("BRL")
				.status(PaymentStatus.PENDING)
				.build();

		when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
		when(paymentRepository.save(any())).thenReturn(payment);
		when(paymentEventRepository.save(any())).thenReturn(null);

		paymentService.updateStatus(id, PaymentStatus.APPROVED);

		verify(paymentRepository, times(1)).save(any());
		verify(paymentEventRepository, times(1)).save(any());
	}
}