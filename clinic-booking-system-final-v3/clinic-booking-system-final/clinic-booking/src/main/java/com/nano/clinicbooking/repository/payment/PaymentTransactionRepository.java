package com.nano.clinicbooking.repository.payment;

import com.nano.clinicbooking.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
//    boolean existsByTransactionNo(String transactionNo);
}
