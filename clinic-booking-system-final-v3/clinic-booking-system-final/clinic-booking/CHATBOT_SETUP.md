# Cấu hình và kiểm tra chatbot

Chatbot dùng Gemini để sinh câu trả lời từ dữ liệu bác sĩ, sự kiện và lịch khám trong MySQL.
Qdrant bổ sung kiến thức tham khảo. Khi Qdrant thiếu cấu hình, chưa có collection, lỗi kích thước
vector hoặc mất kết nối, chatbot tiếp tục dùng dữ liệu MySQL và Gemini.

## API key và model

Đặt `GEMINI_API_KEY` trong biến môi trường của tiến trình backend hoặc file local
`src/main/resources/application-secret.properties`. File này đã được loại khỏi Git.
Không chỉnh file trong `target/classes`: Maven sẽ ghi đè nó khi build.
Trên hosting, cập nhật biến môi trường của dịch vụ rồi triển khai/khởi động lại.

Cấu hình mặc định:

```properties
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_EMBEDDING_MODEL=gemini-embedding-2
GEMINI_EMBEDDING_DIMENSIONS=768
GEMINI_TIMEOUT_MS=20000
GEMINI_MAX_ATTEMPTS=2
QDRANT_COLLECTION=nano_knowledge_base_gemini_embedding_2_768
QDRANT_TIMEOUT_MS=5000
```

Có thể đặt `GEMINI_FALLBACK_MODELS` thành danh sách model cách nhau bằng dấu phẩy.
Mặc định để trống; fallback chỉ hữu ích khi model chính không có sẵn hoặc không trả nội dung.
Kiểm tra model được project của bạn hỗ trợ trước khi đổi cấu hình.

Key cũ đã được Google trả về `403 / CONSUMER_SUSPENDED` khi kiểm tra.
Tạo lại key không đảm bảo giải quyết được nếu project vẫn bị đình chỉ.
Cần kiểm tra quyền truy cập/trạng thái project trong Google AI Studio hoặc Google Cloud.
Code chỉ log HTTP status và mã lý do, không log key hoặc toàn bộ response lỗi của Google.

Tham khảo: [Gemini models và lịch ngừng hỗ trợ](https://ai.google.dev/gemini-api/docs/deprecations/),
[xử lý lỗi Gemini](https://ai.google.dev/gemini-api/docs/troubleshooting).

## Chuyển dữ liệu Qdrant sang embedding mới

Vector của `text-embedding-004` không dùng chung được với `gemini-embedding-2`, kể cả khi
cùng số chiều. Backend mặc định dùng collection mới, không tự tạo hoặc thay đổi collection cũ.
Giữ collection cũ để có thể đối chiếu; tạo lại vector từ payload `text` bằng script PowerShell 7:

```powershell
# Xem collection nguồn/đích; không tạo vector hoặc ghi dữ liệu.
./scripts/Reindex-ChatbotKnowledge.ps1

# Khi Gemini và Qdrant đều hoạt động, tạo collection mới và lập lại chỉ mục.
./scripts/Reindex-ChatbotKnowledge.ps1 -Apply
```

Script đọc file secret ở trên và ưu tiên biến môi trường. Qdrant REST dùng cổng 6333;
backend dùng gRPC cổng 6334. Có thể truyền `-QdrantUrl` khi dùng địa chỉ REST khác.
Script giữ ID/payload, bỏ qua point không có `text`, không xóa collection nguồn.
Nếu bị gián đoạn, có thể chạy lại; các point cùng ID sẽ được cập nhật.
`-Apply` gọi Gemini cho từng văn bản và có thể tiêu thụ quota/chi phí embedding.

Khi đổi model hoặc số chiều, chọn collection mới và đặt đồng bộ
`GEMINI_EMBEDDING_MODEL`, `GEMINI_EMBEDDING_DIMENSIONS`, `QDRANT_COLLECTION`.
Script cho phép truyền `-EmbeddingModel`, `-Dimensions`, `-TargetCollection` tương ứng.
Đợi script hoàn tất trước khi chuyển backend sang collection mới.
Đặt `QDRANT_ENABLED=false` nếu chỉ cần câu trả lời từ dữ liệu phòng khám trong MySQL.

## Qdrant cục bộ khi cụm Cloud không còn truy cập được

Chạy Docker Desktop, sau đó từ thư mục backend:

```powershell
./scripts/Start-LocalQdrant.ps1
./scripts/Reindex-ChatbotKnowledge.ps1 -Local -KnowledgeFile src/main/resources/knowledge/clinic-faq.json -Apply
./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=qdrant-local'
```

Profile `qdrant-local` chỉ được bật khi chỉ định rõ. Nó dùng `127.0.0.1`, gRPC 6334,
không dùng TLS cho kết nối loopback, và đọc key cục bộ từ `.qdrant-local.properties` đã loại khỏi Git.
Docker chỉ công bố cổng REST/gRPC trên loopback và lưu dữ liệu trong volume riêng.
Tệp FAQ đi kèm chứa các quy trình được xác định từ mã dự án, không chứa hồ sơ bệnh nhân.
Đây là kho kiến thức khởi tạo mới, không phải bản khôi phục dữ liệu Cloud đã mất.

Trên Render phải dùng một endpoint Qdrant mà Render truy cập được; địa chỉ `127.0.0.1` ở máy này
không dùng được cho Render. Giữ profile mặc định trên hosting và cấu hình:

```properties
QDRANT_ENABLED=true
QDRANT_HOST=<hostname của cụm đang chạy, không có https:// hoặc đường dẫn>
QDRANT_PORT=6334
QDRANT_TLS=true
QDRANT_API_KEY=<key có quyền đọc collection>
QDRANT_COLLECTION=nano_knowledge_base_gemini_embedding_2_768
```

Nếu Cloud cũ chưa truy cập được, kiểm tra trạng thái cụm trong tài khoản Qdrant Cloud.
Kết nối bị đóng trước khi TLS hoàn tất không xác nhận được tính hợp lệ của API key.
Nếu cần cụm mới, khởi tạo FAQ bằng lệnh sau sau khi cập nhật endpoint và key có quyền ghi
trong file local hoặc biến môi trường:

```powershell
./scripts/Reindex-ChatbotKnowledge.ps1 -KnowledgeFile src/main/resources/knowledge/clinic-faq.json -Apply
```

Tham khảo [cổng REST/gRPC trên Qdrant Cloud](https://qdrant.tech/documentation/cloud/cluster-access/).

## Lỗi 403 ở sự kiện trên trang chủ

Trang chủ chỉ gọi `/events/my` khi tài khoản hiện tại là bệnh nhân đã đăng nhập.
Khách được xem `GET /events` và `GET /events/all`; endpoint cá nhân và các thao tác
đăng ký/quản trị vẫn được bảo vệ. Cần triển khai cả frontend lẫn backend mới lên Render
để áp dụng thay đổi cho website đang dùng hosting.

## Build và kiểm thử

Chạy từ thư mục `clinic-booking` với JDK 22:

```powershell
./mvnw.cmd clean verify
./mvnw.cmd spring-boot:run
```

Kiểm tra riêng phần chatbot và script chuyển chỉ mục:

```powershell
./mvnw.cmd '-Dtest=GeminiClientTest,ClinicKnowledgeServiceTest,RagChatServiceTest,ChatbotConfigurationTest' verify
./scripts/tests/Reindex-ChatbotKnowledge.Tests.ps1
```

Kiểm tra `POST /chat/ask`, body `text/plain`, bằng câu hỏi về bác sĩ, lịch khám và sự kiện.
Câu chào dùng nội dung viết sẵn nên không chứng minh Gemini đang hoạt động.

Các test chatbot chạy với HTTP giả lập và Qdrant mock, không gửi key thật hoặc gọi dịch vụ cloud.
Test bao gồm: key bị đình chỉ, bảo vệ log, quota/retry, timeout, model fallback, JSON escaping,
embedding sai số chiều và chatbot vẫn trả lời khi Qdrant lỗi.
