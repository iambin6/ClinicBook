package com.nano.clinicbooking.controller.payment;

import com.nano.clinicbooking.model.PaymentTransaction;
import com.nano.clinicbooking.service.payment.PaymentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // ⚠️ cho phép frontend React truy cập
public class PaymentHistory {

    private final PaymentHistoryService paymentService;

    // 🟩 Lấy tất cả giao dịch
    @GetMapping
    public ResponseEntity<List<PaymentTransaction>> getAll() {
        return ResponseEntity.ok(paymentService.getAll());
    }


}
