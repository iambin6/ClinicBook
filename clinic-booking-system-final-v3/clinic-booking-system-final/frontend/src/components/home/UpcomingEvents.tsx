import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Row, Spinner } from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import {
  fetchAvailableEvents,
  fetchRegisteredEventIds,
  registerEvent,
  type UserEventItem,
} from "../../api/patientEvents";
import "../../styles/appointmentlist.css";

function formatDateTime(value?: string): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatLocation(location?: string | null): string {
  if (!location) return "Tại phòng khám";
  return location;
}

function formatVoucher(event: UserEventItem): string {
  if (!event.voucherName && !event.voucherCode) return "Không có";
  const parts = [];
  if (event.voucherName) parts.push(event.voucherName);
  if (event.voucherCode) parts.push(`Mã: ${event.voucherCode}`);
  return parts.join(" - ");
}

export default function UpcomingEvents() {
  const { user, userType, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const canRegister = isAuthenticated && userType === "Patient";
  const [events, setEvents] = useState<UserEventItem[]>([]);
  const [registeredIds, setRegisteredIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>("");
  const [registeringId, setRegisteringId] = useState<number | null>(null);
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      setError("");
      try {
        const list = await fetchAvailableEvents();
        if (mounted) {
          setEvents(list);
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : "Không thể tải sự kiện.";
        if (mounted) setError(message);
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    setRegisteredIds([]);
    // Personal registrations are only available to authenticated patients.
    if (!canRegister) return;
    fetchRegisteredEventIds()
      .then((registered) => { if (mounted) setRegisteredIds(registered); })
      .catch((err: unknown) => {
        if (mounted) setError(err instanceof Error ? err.message : "Không thể tải thông tin đăng ký.");
      });
    return () => { mounted = false; };
  }, [canRegister, user?.id]);

  const upcomingEvents = useMemo(
    () =>
      [...events].sort((a, b) => {
        const aTime = a.startDate ? new Date(a.startDate).getTime() : Number.MAX_SAFE_INTEGER;
        const bTime = b.startDate ? new Date(b.startDate).getTime() : Number.MAX_SAFE_INTEGER;
        return aTime - bTime;
      }),
    [events]
  );

  const handleRegister = async (eventId: number) => {
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }
    if (!canRegister) return;
    setRegisteringId(eventId);
    setError("");
    setSuccessMessage("");
    try {
      await registerEvent(eventId);
      const updatedRegistered = await fetchRegisteredEventIds();
      setRegisteredIds(updatedRegistered);
      setSuccessMessage("Bạn đã đăng ký sự kiện thành công! Vui lòng kiểm tra thông tin trong mục Sự kiện của tôi.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Không thể đăng ký sự kiện.";
      setError(message);
    } finally {
      setRegisteringId(null);
    };
  };

  return (
    <section className="home-section events-section">
      <div className="section-header text-center mb-4">
        <h2 className="fw-bold">Sự kiện nổi bật</h2>
        <p className="text-muted mb-0">
          Đăng ký tham gia các chương trình ưu đãi và sự kiện chăm sóc sức khỏe mới nhất của chúng tôi.
        </p>
      </div>

      {error && (
        <Alert variant="danger" onClose={() => setError("")} dismissible>
          {error}
        </Alert>
      )}
      {successMessage && (
        <Alert variant="success" onClose={() => setSuccessMessage("")} dismissible>
          {successMessage}
        </Alert>
      )}

      {loading ? (
        <div className="d-flex justify-content-center py-5">
          <Spinner animation="border" />
        </div>
      ) : upcomingEvents.length === 0 ? (
        <Card body className="text-center text-muted">
          Hiện chưa có sự kiện nào.
        </Card>
      ) : (
        <Row className="g-4 justify-content-center">
          {upcomingEvents.map((event) => (
            <Col key={event.id} xs={12} md={6} lg={4} className="d-flex">
              <Card className="h-100 shadow-sm border-0 w-100">
                <Card.Body className="d-flex flex-column">
                  <div className="mb-2">
                    <div className="badge bg-primary-subtle text-primary">
                      {formatDateTime(event.startDate)}
                    </div>
                  </div>
                  <Card.Title>{event.title}</Card.Title>
                  {event.description && (
                    <Card.Text className="text-muted">{event.description}</Card.Text>
                  )}
                  <div className="mt-auto">
                    <div className="small text-muted mb-1">
                      <strong>Địa điểm:</strong> {formatLocation(event.location)}
                    </div>
                    <div className="small text-muted mb-3">
                      <strong>Voucher:</strong> {formatVoucher(event)}
                    </div>
                    {registeredIds.includes(event.id) ? (
                      <Button variant="outline-secondary" disabled className="w-100">
                        Bạn đã đăng ký sự kiện này
                      </Button>
                    ) : (
                      <Button
                        variant="primary"
                        onClick={() => handleRegister(event.id)}
                        disabled={registeringId === event.id || (isAuthenticated && !canRegister)}
                        className="w-100"
                      >
                        {registeringId === event.id ? (
                          <>
                            <Spinner animation="border" size="sm" className="me-2" />
                            Đang đăng ký...
                          </>
                        ) : (
                          !isAuthenticated ? "Đăng nhập để đăng ký" : canRegister ? "Đăng ký tham gia" : "Chỉ dành cho bệnh nhân"
                        )}
                      </Button>
                    )}
                  </div>
                </Card.Body>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </section>
  );
}


