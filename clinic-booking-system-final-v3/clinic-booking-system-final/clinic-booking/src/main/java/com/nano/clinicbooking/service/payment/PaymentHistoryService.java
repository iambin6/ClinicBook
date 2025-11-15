package com.nano.clinicbooking.service.payment;

import com.nano.clinicbooking.model.PaymentTransaction;
import com.nano.clinicbooking.repository.payment.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {
    private final PaymentTransactionRepository repo;

    public List<PaymentTransaction> getAll() {
        return repo.findAll();
    }


}
