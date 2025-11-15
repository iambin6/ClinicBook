package com.nano.clinicbooking.service.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class VnpayConfig {

    public static final String vnp_PayUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String vnp_ApiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    public static final String vnp_ReturnUrl = "https://subcallosal-otelia-cajolingly.ngrok-free.dev/payment/vnpay-return";
    public static final String vnp_TmnCode = "95APQSOW";
    public static final String secretKey = "7B4QNIZI2EF4L139BOJB933N7YNXWV4H";

//
//    // 🌐 URL backend (VNPAY gọi callback về đây)
//    public static final String vnp_ReturnUrl = "https://clinic-api.onrender.com/payment/vnpay-return";
//
    // 🌐 URL frontend React (nơi redirect người dùng)
    public static final String FRONTEND_URL = "https://subcallosal-otelia-cajolingly.ngrok-free.dev";

    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        try {
            String ip = request.getHeader("X-FORWARDED-FOR");
            if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
            if (ip == null || ip.isEmpty()) ip = "127.0.0.1";
            return ip;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ✅ Chuẩn hmacSHA512
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) throw new NullPointerException();
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ✅ Hàm hashAllFields (y chang bản servlet gốc)
    public static String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                if (!first) sb.append("&");
                try {
                    sb.append(URLEncoder.encode(fieldName, "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(fieldValue, "UTF-8"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                first = false;
            }
        }

        return hmacSHA512(secretKey, sb.toString());
    }

    // ✅ Build URL chính xác 100%
    public static String buildPaymentUrl(Map<String, String> vnp_Params) {
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder query = new StringBuilder();

        boolean first = true;
        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                if (!first) query.append("&");
                try {
                    query.append(URLEncoder.encode(fieldName, "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(fieldValue, "UTF-8"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                first = false;
            }
        }

        String secureHash = hashAllFields(vnp_Params);

        System.out.println("🔑 HASH DATA = " + query);
        System.out.println("🔑 SIGNATURE = " + secureHash);

        return vnp_PayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    // ✅ Tạo tham số mặc định
    public static Map<String, String> initParams(String orderId, long amount, HttpServletRequest request) {
        Map<String, String> vnp_Params = new LinkedHashMap<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        String createDate = formatter.format(cld.getTime());
        cld.add(Calendar.MINUTE, 15);
        String expireDate = formatter.format(cld.getTime());

        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", orderId);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan lich kham #" + orderId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", getIpAddress(request));
        vnp_Params.put("vnp_CreateDate", createDate);
        vnp_Params.put("vnp_ExpireDate", expireDate);
        return vnp_Params;
    }
}
