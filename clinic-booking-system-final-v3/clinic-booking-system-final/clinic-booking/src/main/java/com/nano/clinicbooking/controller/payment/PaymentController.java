package com.nano.clinicbooking.controller.payment;

import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.model.PaymentTransaction;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import com.nano.clinicbooking.repository.payment.PaymentTransactionRepository;
import com.nano.clinicbooking.service.payment.VnpayConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final AppointmentRepository appointmentRepo;
    private final PaymentTransactionRepository paymentRepo;

    // ✅ Tạo URL thanh toán
    // ✅ 1. React gọi để tạo link thanh toán
    @Transactional
    @GetMapping("/create")
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestParam String appointmentNo,
            HttpServletRequest request) {

        Appointment appointment = appointmentRepo.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn"));

        long amount = (long) (appointment.getDoctor().getConsultationFee() * 100);
        Map<String, String> vnp_Params = VnpayConfig.initParams(appointmentNo, amount, request);
        String paymentUrl = VnpayConfig.buildPaymentUrl(vnp_Params);

        Map<String, Object> response = new HashMap<>();
        response.put("paymentUrl", paymentUrl);
        return ResponseEntity.ok(response);
    }

    // ✅ 2. VNPAY gọi callback sau khi thanh toán
    @GetMapping("/vnpay-return")
    public ResponseEntity<Map<String, Object>> vnpayReturn(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));

        String vnp_SecureHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String signValue = VnpayConfig.hashAllFields(params);
        Map<String, Object> response = new HashMap<>();
        boolean success = false;

        if (signValue.equals(vnp_SecureHash)) {
            String txnRef = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String amount = params.get("vnp_Amount");
            String bankCode = params.get("vnp_BankCode");

            Appointment appointment = appointmentRepo.findByAppointmentNo(txnRef).orElse(null);
            if (appointment != null) {
                PaymentTransaction trans = new PaymentTransaction();
                trans.setTransactionNo(txnRef);
                trans.setOrderInfo(params.get("vnp_OrderInfo"));
                trans.setAmount(amount);
                trans.setBankCode(bankCode);
                trans.setResponseCode(responseCode);
                trans.setTransactionStatus("00".equals(responseCode) ? "SUCCESS" : "FAILED");
                trans.setPayDate(LocalDateTime.now());
                trans.setAppointment(appointment);
                paymentRepo.save(trans);

                if ("00".equals(responseCode)) {
                    appointment.setStatus(AppointmentStatus.CONFIRMED);
                    appointment.setMeetingUrl("https://meet.jit.si/" + appointment.getJoinCode());
                    success = true;
                } else {
                    appointment.setStatus(AppointmentStatus.CANCELLED);
                }
                appointmentRepo.save(appointment);
            }
        }

        response.put("success", success);
        response.put("redirectUrl", success
                ? VnpayConfig.FRONTEND_URL + "/payment-result?status=success"
                : VnpayConfig.FRONTEND_URL + "/payment-result?status=failed");
        return ResponseEntity.ok(response);
    }
















//    @GetMapping("/vnpay-return")
//    public String vnpayReturn(HttpServletRequest request) {
//        Map<String, String> params = new HashMap<>();
//        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
//
//        String vnp_SecureHash = params.remove("vnp_SecureHash");
//        params.remove("vnp_SecureHashType");
//
//        String signValue = VnpayConfig.hashAllFields(params);
//        String frontend = VnpayConfig.FRONTEND_URL + "/payment-result";
//
//        if (!signValue.equals(vnp_SecureHash)) {
//            return "redirect:" + frontend + "?status=invalid";
//        }
//
//        String txnRef = params.get("vnp_TxnRef");
//        String responseCode = params.get("vnp_ResponseCode");
//        String amount = params.get("vnp_Amount");
//        String bankCode = params.get("vnp_BankCode");
//
//        Appointment appointment = appointmentRepo.findByAppointmentNo(txnRef).orElse(null);
//        if (appointment == null) {
//            return "redirect:" + frontend + "?status=notfound";
//        }
//
//        PaymentTransaction trans = new PaymentTransaction();
//        trans.setTransactionNo(txnRef);
//        trans.setOrderInfo(params.get("vnp_OrderInfo"));
//        trans.setAmount(amount);
//        trans.setBankCode(bankCode);
//        trans.setResponseCode(responseCode);
//        trans.setTransactionStatus("00".equals(responseCode) ? "SUCCESS" : "FAILED");
//        trans.setPayDate(LocalDateTime.now());
//        trans.setAppointment(appointment);
//        paymentRepo.save(trans);
//
//        if ("00".equals(responseCode)) {
//            appointment.setStatus(AppointmentStatus.CONFIRMED);
//            appointmentRepo.save(appointment);
//            return "redirect:" + frontend + "?status=success&appointmentNo=" + txnRef;
//        } else {
//            appointment.setStatus(AppointmentStatus.CANCELLED);
//            appointmentRepo.save(appointment);
//            return "redirect:" + frontend + "?status=failed&appointmentNo=" + txnRef;
//        }
//    }

}
