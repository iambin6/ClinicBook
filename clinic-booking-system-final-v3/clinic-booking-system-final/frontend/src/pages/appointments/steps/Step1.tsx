import { useEffect, useState } from "react";
import { Card, Col, Form, Row, Spinner } from "react-bootstrap";
import { useSearchParams } from "react-router-dom";
import { useAppt } from "../../../contexts/AppointmentContext";
import { fetchSpecialties, type Specialty } from "../../../api/specialty";
import { fetchDoctorsBySpecialty, type Doctor } from "../../../api/doctor";
import { getDoctorImagePathWithFallback } from "../../../utils/doctorImage";
import "../../../styles/appointment.css";

export default function Step1Select() {
  const { state, dispatch } = useAppt();
  const [searchParams] = useSearchParams();
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [loadingSpec, setLoadingSpec] = useState(true);

  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [loadingDoc, setLoadingDoc] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        setSpecialties(await fetchSpecialties());
      } finally {
        setLoadingSpec(false);
      }
    })();
  }, []);

  useEffect(() => {
    // Chỉ reset danh sách doctors khi specialty thay đổi
    if (!state.specialtyId) {
      setDoctors([]);
      setLoadingDoc(false);
      return;
    }
    
    setLoadingDoc(true);
    (async () => {
      try {
        const data = await fetchDoctorsBySpecialty(Number(state.specialtyId));
        setDoctors(data);
        
        // Tự động chọn doctor nếu có doctorId trong URL hoặc state
        const doctorIdFromUrl = searchParams.get("doctorId");
        const targetDoctorId = doctorIdFromUrl ? Number(doctorIdFromUrl) : state.doctorId;
        
        // Chỉ update doctor nếu:
        // 1. Có doctorId trong URL và khác với state hiện tại
        // 2. Hoặc state có doctorId nhưng không match với danh sách doctors hiện tại
        if (targetDoctorId && data.length > 0) {
          const doctor = data.find(d => d.id === targetDoctorId);
          if (doctor) {
            // Chỉ update nếu doctorId thay đổi hoặc thông tin chưa đầy đủ
            if (state.doctorId !== doctor.id || !state.doctorFullName) {
              dispatch({
                type: "SET_DOCTOR",
                id: Number(doctor.id),
                fullName: doctor.fullName,
                degree: doctor.degree,
                experience: doctor.experience,
                avatarUrl: getDoctorImagePathWithFallback(doctor.fullName) || undefined,
                description: doctor.description,
                specialtyName: doctor.specialtyName,
              });
            }
          }
        } else if (state.doctorId && data.length > 0) {
          // Nếu state đã có doctorId, kiểm tra xem có trong danh sách không
          const doctor = data.find(d => d.id === state.doctorId);
          if (doctor && (!state.doctorFullName || !state.doctorAvatarUrl)) {
            // Cập nhật thông tin doctor nếu thiếu
            dispatch({
              type: "SET_DOCTOR",
              id: Number(doctor.id),
              fullName: doctor.fullName ?? state.doctorFullName,
              degree: doctor.degree ?? state.doctorDegree,
              experience: doctor.experience ?? state.doctorExperience,
              avatarUrl: getDoctorImagePathWithFallback(doctor.fullName ?? state.doctorFullName) || undefined,
              description: doctor.description ?? state.doctorDescription,
              specialtyName: doctor.specialtyName ?? state.specialtyName,
            });
          }
        }
      } finally {
        setLoadingDoc(false);
      }
    })();
  }, [state.specialtyId, searchParams, dispatch, state.doctorId, state.doctorFullName, state.doctorDegree, state.doctorExperience, state.doctorAvatarUrl, state.doctorDescription, state.specialtyName]);

  const canNext = !!state.doctorId;

  return (
    <div className="appt-card p-4">
      <Card className="appt-inner-card mb-4">
        <Card.Body className="d-flex flex-column flex-lg-row gap-3 align-items-lg-center">
          <div className="flex-grow-1 w-100">
            <Form.Label className="appt-label mb-2">Select Specialty</Form.Label>
            <Form.Select
              value={state.specialtyId ?? ""}
              onChange={(e) =>
                dispatch({
                  type: "SET_SPECIALTY",
                  id: e.target.value ? Number(e.target.value) : null,
                  name: e.target.selectedOptions[0]?.text,
                })
              }
              disabled={loadingSpec}
            >
              <option value="">-- Choose --</option>
              {specialties.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Form.Select>
          </div>
          {loadingSpec && (
            <div className="text-muted small d-flex align-items-center gap-2">
              <Spinner size="sm" /> Đang tải chuyên khoa…
            </div>
          )}
        </Card.Body>
      </Card>

      <Card className="appt-inner-card mb-4">
        <Card.Body>
          <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
            <div className="appt-heading mb-0">Doctors by Specialty</div>
            {state.specialtyName ? (
              <span className="appt-chip-secondary">{state.specialtyName}</span>
            ) : null}
          </div>

          {!state.specialtyId && (
            <div className="appt-empty-state">
              Hãy chọn chuyên khoa để xem danh sách bác sĩ phù hợp.
            </div>
          )}

          {state.specialtyId && loadingDoc && (
            <div className="appt-empty-state">
              <Spinner size="sm" /> Đang tải danh sách bác sĩ…
            </div>
          )}

          {state.specialtyId && !loadingDoc && doctors.length === 0 && (
            <div className="appt-empty-state">
              Không tìm thấy bác sĩ cho chuyên khoa này.
            </div>
          )}

          {state.specialtyId && doctors.length > 0 && (
            <Row className="g-3">
              {doctors.map((d) => {
                const active = state.doctorId === d.id;
                const avatar = getDoctorImagePathWithFallback(d.fullName);
                return (
                  <Col md={6} key={d.id}>
                    <Card
                      className={`doctor-card doctor-card--with-avatar ${active ? "active" : ""}`}
                      role="button"
                      onClick={() =>
                        dispatch({
                          type: "SET_DOCTOR",
                          id: Number(d.id),
                          fullName: d.fullName,
                          degree: d.degree,
                          experience: d.experience,
                          avatarUrl: avatar || undefined,
                          description: d.description,
                          specialtyName: d.specialtyName,
                        })
                      }
                    >
                      <Card.Body className="d-flex gap-3">
                        <div className="doctor-card__avatar">
                          {avatar ? (
                            <img src={avatar} alt={d.fullName} onError={(e) => {
                              const target = e.target as HTMLImageElement;
                              target.style.display = 'none';
                              const placeholder = target.parentElement?.querySelector('.doctor-avatar-placeholder');
                              if (placeholder) {
                                (placeholder as HTMLElement).style.display = 'flex';
                              }
                            }} />
                          ) : null}
                          <div 
                            className="doctor-avatar-placeholder"
                            style={{ 
                              display: avatar ? 'none' : 'flex',
                              width: '60px',
                              height: '60px',
                              backgroundColor: '#e9ecef',
                              alignItems: 'center',
                              justifyContent: 'center',
                              color: '#6c757d',
                              fontSize: '24px',
                              fontWeight: 'bold',
                              borderRadius: '50%'
                            }}
                          >
                            {d.fullName?.charAt(0)?.toUpperCase() || 'BS'}
                          </div>
                        </div>
                        <div className="doctor-card__content">
                          <div className="doctor-name">{d.fullName}</div>
                        </div>
                      </Card.Body>
                    </Card>
                  </Col>
                );
              })}
            </Row>
          )}
        </Card.Body>
      </Card>

      <div className="appt-header mb-4 justify-content-end">
        {state.doctorId && (
          <div className="appt-selected-tag">
            Đang chọn&nbsp;
            <strong>{state.doctorFullName}</strong>
          </div>
        )}
      </div>

      <div className="appt-bottom justify-content-end">
        <button
          className="btn-next"
          disabled={!canNext}
          onClick={() => dispatch({ type: "SET_STEP", step: 2 })}
        >
          Tiếp tục
        </button>
      </div>
    </div>
  );
}
