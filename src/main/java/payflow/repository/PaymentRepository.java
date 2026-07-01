package payflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import payflow.entity.Payment;
import payflow.enums.PaymentStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByPayerId(String payerId);
    List<Payment> findByStatus(PaymentStatus status);
}