package payflow.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import payflow.dto.request.PaymentRequest;
import payflow.dto.response.PaymentResponse;
import payflow.enums.PaymentStatus;
import payflow.service.PaymentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> findAll(
            @RequestParam(required = false) PaymentStatus status) {
        if (status != null) {
            return ResponseEntity.ok(paymentService.findByStatus(status));
        }
        return ResponseEntity.ok(paymentService.findAll());
    }
}