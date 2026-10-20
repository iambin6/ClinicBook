package com.nano.clinicbooking.service.chat;

import com.nano.clinicbooking.service.gemini.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagChatService {

    private final GeminiClient gemini;
    private final UnifiedClinicDataProvider dataProvider;
    private final ClinicKnowledgeService knowledgeService;

    private static final String UNAVAILABLE_MESSAGE =
            "Xin lỗi bạn, trợ lý AI hiện đang bận hoặc gián đoạn kết nối tạm thời. Vui lòng bấm thử lại hoặc liên hệ hotline phòng khám để được hỗ trợ đặt lịch nhanh nhất.";

    /* =================== PUBLIC ENTRYPOINTS =================== */

    // NEW: giữ API cũ, forward sang bản đầy đủ

    public String ask(String question) {
        try {
            if (question == null || question.isBlank()) {
                return "Dạ bạn cần Nano Clinic hỗ trợ thông tin gì ạ?";
            }

            // 0) Lời chào thân thiện (hi, hello, xin chào...)
            if (isGreetingIntent(question)) {
                return "Xin chào bạn! 👋 Tôi là trợ lý ảo của phòng khám **Nano Clinic** (Đà Nẵng). Tôi có thể giúp bạn tìm thông tin về bác sĩ, chuyên khoa, lịch khám và các sự kiện ưu đãi. Bạn cần hỗ trợ gì ạ?";
            }

            // 1) Giới thiệu phòng khám
            if (isIntroIntent(question)) {
                String ai = tryIntroWithAI(question);
                if (ai != null && !ai.isBlank()) return ai.trim();
                return getClinicIntro();
            }

            // 2) Còn lại → trả lời theo unified data (events/doctors/shifts + RAG)
            return answerWithUnifiedData(question);

        } catch (Exception e) {
            log.error("Chatbot request failed ({})", e.getClass().getSimpleName());
            return UNAVAILABLE_MESSAGE;
        }
    }

    /*  CORE UNIFIED ANSWER  */

    // NEW: gom toàn bộ logic lấy JSON từ DB + RAG + prompt + call LLM
    private String answerWithUnifiedData(String question) throws Exception { // NEW
        // 1) Lấy data JSON từ DB
        String eventsJson = dataProvider.getEventsJson();
        String doctorsJson = dataProvider.getDoctorsJson();
        String shiftsJson = dataProvider.getShiftsJsonForWeek(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));

        // 2) RAG context (nếu có)
        String ragContext = knowledgeService.findContext(question);

        // 3) Build prompt hợp nhất
        String prompt = buildUnifiedPrompt(ragContext, eventsJson, doctorsJson, shiftsJson, question);

        // 4) Gọi model và trả lời
        String answer = gemini.generateHtml(prompt);
        if (answer != null && !answer.isBlank()) {
            return answer.trim();
        }

        log.warn("Gemini returned no chatbot answer; see Gemini failure code in server logs");
        return UNAVAILABLE_MESSAGE;
    }

    /* PROMPT BUILDER  */

    // tách ra method riêng, KHÔNG để lẫn trong ask()
    private String buildUnifiedPrompt(String ragContext, String eventsJson,String doctorsJson, String shiftsJson, String userQuestion) { // NEW
        return """
                Bạn là trợ lý ảo của phòng khám Nano Clinic (Đà Nẵng).
                
                DỮ LIỆU SỰ KIỆN (JSON, enable=true):
                %s
                
                 DỮ LIỆU BÁC SĨ (JSON):
                              %s
                
                
                DỮ LIỆU LỊCH KHÁM 7 NGÀY (JSON):
                %s
                
                CÁCH DÙNG:
                - Nếu câu hỏi liên quan "sự kiện/khuyến mãi/ưu đãi/voucher/event/gần đây/đang diễn ra/còn áp dụng không/...":
                  → TỰ ĐỘNG liệt kê sự kiện phù hợp, định dạng:
                  • **<Tiêu đề>**
                  • Thời gian: dd/MM/yyyy → dd/MM/yyyy
                  • (Nếu có) Địa điểm, mô tả (1–2 dòng)
                  • (Nếu có) 🎁 Voucher: <tên — mã>
                  • Ưu tiên đang diễn ra hoặc sắp tới gần nhất
                - Nếu câu hỏi liên quan bác sĩ/chuyên khoa (ví dụ: "đau mắt", "khoa mắt", "tâm thần", "khám TMH", ...):
                  → TỰ ĐỘNG đối sánh từ khóa triệu chứng/chuyên khoa với danh sách bác sĩ trong JSON để gợi ý 2–3 bác sĩ phù hợp (tên + chuyên khoa + vài chi tiết ngắn).
                - Nếu hỏi lịch/khung giờ hôm nay:
                  → Dựa vào JSON lịch, gợi ý khung giờ khả dụng và tên bác sĩ/chuyên khoa.
                - Nếu không liên quan các dữ liệu trên, có thể dùng KB dưới; nếu vẫn không đủ, trả lời chung nhưng KHÔNG bịa.
                NGUYÊN TẮC:
                - Trả lời tiếng Việt tự nhiên, ngắn gọn, không bịa, nếu hỏi tiếng anh thì trả lời tiêng anh
                - KHÔNG in JSON ra màn hình; chỉ dùng JSON để suy luận.
                - Nếu không có dữ liệu phù hợp: nói rõ "Hiện chưa có ..." thay vì bịa.
                - trả lời ngày, thứ, tháng đúng theo giờ việt nam .
                (Kiến thức tham khảo từ KB, có thể trống):
                %s
                
                CÂU HỎI NGƯỜI DÙNG:
                "%s"
                """.formatted(
                eventsJson == null ? "[]" : eventsJson,
                doctorsJson == null ? "[]" : doctorsJson,
                shiftsJson == null ? "[]" : shiftsJson,
                ragContext == null ? "" : ragContext,
                userQuestion
        );
    }

    /*  INTENT HELPERS  */

    private boolean isGreetingIntent(String q) {
        if (q == null) return false;
        String s = q.trim().toLowerCase().replaceAll("[!?,.]+$", "").trim();
        return s.equals("hi") || s.equals("hello") || s.equals("hey")
                || s.equals("xin chào") || s.equals("chào") || s.equals("chào bạn")
                || s.equals("alo") || s.equals("chào bot") || s.equals("hi bot")
                || s.equals("hello bot") || s.startsWith("xin chào")
                || s.startsWith("chào bạn");
    }

    private boolean isIntroIntent(String q) {
        if (q == null) return false;
        String s = q.toLowerCase();
        return s.contains("giới thiệu")
                || s.contains("phòng khám nano")
                || s.contains("nano clinic")
                || s.contains("về nano")
                || (s.contains("phòng khám") && (s.contains("ở đâu") || s.contains("thông tin")));
    }



    /* INTRO TEXT  */

    private String tryIntroWithAI(String userQuestion) {
        String prompt = """
                Bạn là trợ lý của **Nano Clinic** (phòng khám đa khoa tại Đà Nẵng).
                Viết đoạn **giới thiệu 4–6 câu, tiếng Việt tự nhiên**, nêu:
                • Dịch vụ chính (nội tổng quát, nhi, sản, TMH, tim mạch, da liễu, xét nghiệm/siêu âm…)
                • Địa chỉ/hotline: 032594011 / Giờ làm việc: Thứ 2 – Chủ nhật, 7:30–19:00
                • Văn phong thân thiện, súc tích, không phóng đại.
                Chỉ trả về đoạn giới thiệu (không kèm giải thích).
                Câu hỏi người dùng: "%s"
                """.formatted(userQuestion);
        try {
            String ans = gemini.generateHtml(prompt);
            return (ans == null || ans.isBlank()) ? null : ans;
        } catch (Exception e) {
            log.warn("Clinic introduction generation unavailable ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private String getClinicIntro() {
        return """
                💙 **Phòng khám Nano Clinic** là phòng khám đa khoa hiện đại tại **Đà Nẵng**,
                cung cấp các dịch vụ: nội tổng quát, nhi, sản, tai mũi họng, tim mạch, da liễu...
                • Xét nghiệm, siêu âm, chẩn đoán hình ảnh
                • Đặt lịch & quản lý hồ sơ sức khỏe trực tuyến
                ☎️ 032594011  • 🕐 7:30–19:00 (Thứ 2–CN)
                📍 123 Nguyễn Văn Linh, Hải Châu, Đà Nẵng
                """;
    }

}
