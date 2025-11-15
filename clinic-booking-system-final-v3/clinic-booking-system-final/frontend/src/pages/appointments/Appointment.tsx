import { useEffect, useState } from "react";
import { Container } from "react-bootstrap";
import { useSearchParams } from "react-router-dom";
import { useAppt } from "../../contexts/AppointmentContext";
import { fetchDoctorById } from "../../api/doctor";
import Step1 from "./steps/Step1";
import Step2 from "./steps/Step2";
import Step3 from "./steps/Step3";
import Step4 from "./steps/Step4";

export default function Appointment() {
  const { state, dispatch } = useAppt();
  const [sp, setSp] = useSearchParams();
  const [loadingDoctor, setLoadingDoctor] = useState(false);

  // đọc step từ URL
  useEffect(() => {
    const s = Number(sp.get("step") || 1) as 1|2|3|4;
    if (s !== state.step) dispatch({ type: "SET_STEP", step: s });
  }, [sp]);

  // đọc doctorId và specialtyId từ URL khi vào trang
  useEffect(() => {
    const doctorIdParam = sp.get("doctorId");
    const specialtyIdParam = sp.get("specialtyId");
    
    // Chỉ xử lý khi có tham số trong URL
    if (!doctorIdParam && !specialtyIdParam) {
      return;
    }

    // Nếu có cả doctorId và specialtyId, fetch doctor trước để đảm bảo thông tin đầy đủ
    if (doctorIdParam) {
      const doctorId = Number(doctorIdParam);
      if (!isNaN(doctorId)) {
        // Chỉ fetch nếu doctorId trong URL khác với state hiện tại
        if (state.doctorId !== doctorId) {
          setLoadingDoctor(true);
          fetchDoctorById(doctorId)
            .then((doctor) => {
              // Nếu có specialtyId trong URL, kiểm tra xem có match với doctor không
              const urlSpecialtyId = specialtyIdParam ? Number(specialtyIdParam) : null;
              const doctorSpecialtyId = doctor.specialtyId;
              
              // Set specialty: ưu tiên URL param, sau đó lấy từ doctor
              const finalSpecialtyId = urlSpecialtyId || doctorSpecialtyId;
              
              // Sử dụng action mới để set cả specialty và doctor cùng lúc, tránh reset
              if (finalSpecialtyId) {
                dispatch({
                  type: "SET_SPECIALTY_AND_DOCTOR",
                  specialtyId: finalSpecialtyId,
                  specialtyName: doctor.specialtyName,
                  doctorId: doctor.id,
                  doctorFullName: doctor.fullName,
                  doctorDegree: doctor.degree,
                  doctorExperience: doctor.experience,
                  doctorAvatarUrl: doctor.avatarUrl,
                  doctorDescription: doctor.description,
                });
              } else {
                // Nếu không có specialtyId, chỉ set doctor
                dispatch({
                  type: "SET_DOCTOR",
                  id: doctor.id,
                  fullName: doctor.fullName,
                  degree: doctor.degree,
                  experience: doctor.experience,
                  avatarUrl: doctor.avatarUrl,
                  description: doctor.description,
                  specialtyName: doctor.specialtyName,
                });
              }
            })
            .catch((err) => {
              console.error("Không thể lấy thông tin bác sĩ:", err);
              // Nếu fetch thất bại, vẫn set doctorId và specialtyId nếu có
              if (specialtyIdParam) {
                const specialtyId = Number(specialtyIdParam);
                if (!isNaN(specialtyId) && state.specialtyId !== specialtyId) {
                  dispatch({
                    type: "SET_SPECIALTY",
                    id: specialtyId,
                  });
                }
              }
              dispatch({
                type: "SET_DOCTOR",
                id: doctorId,
              });
            })
            .finally(() => {
              setLoadingDoctor(false);
            });
        }
      }
    } else if (specialtyIdParam) {
      // Chỉ có specialtyId, không có doctorId
      const specialtyId = Number(specialtyIdParam);
      if (!isNaN(specialtyId) && state.specialtyId !== specialtyId) {
        dispatch({
          type: "SET_SPECIALTY",
          id: specialtyId,
        });
      }
    }
  }, [sp, state.specialtyId, state.doctorId, dispatch]);

  // ghi step ra URL khi đổi
  useEffect(() => {
    const current = Number(sp.get("step") || 1);
    if (current !== state.step) {
      sp.set("step", String(state.step));
      setSp(sp, { replace: true });
    }
  }, [state.step]);

  return (
    <Container className="appt-wrap py-4">
      {/* Stepper… (giống Step 1, đánh active theo state.step) */}
      {state.step === 1 && <Step1 />}
      {state.step === 2 && <Step2 />}
      {state.step === 3 && <Step3 />}
      {state.step === 4 && <Step4 />}
    </Container>
  );
}