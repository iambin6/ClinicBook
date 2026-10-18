import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import Step4 from "../src/pages/appointments/steps/Step4";
import { AppointmentProvider } from "../src/contexts/AppointmentContext";
import { bookAppointment } from "../src/api/appointment";
import { http } from "../src/api/http";

vi.mock("../src/api/appointment", () => ({ bookAppointment: vi.fn() }));
vi.mock("../src/api/http", () => ({ http: { get: vi.fn() } }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root;
let container: HTMLDivElement;

async function open(type = "ONSITE") {
  sessionStorage.setItem("appt", JSON.stringify({
    step: 4, specialtyId: 1, doctorId: 11, doctorFullName: "Test Doctor", appointmentType: type,
    dateISO: "2030-10-20", shiftId: 901,
    patientSelections: [{ fullName: "Demo Patient", gender: "Other", dob: "2000-01-01", phoneNumber: "" }],
  }));
  await act(async () => { root.render(<AppointmentProvider><Step4 /></AppointmentProvider>); });
}
async function submit() {
  await act(async () => { container.querySelector<HTMLButtonElement>(".btn-next")!.click(); });
}
beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  vi.mocked(bookAppointment).mockResolvedValue({
    ok: true, data: { id: 1, appointmentNo: "OFF-DEMO", status: "PENDING_CONFIRM" },
  });
});
afterEach(async () => {
  await act(async () => { root.unmount(); });
  container.remove();
});

it("shows the pending status returned by the server for onsite bookings", async () => {
  await open();
  await submit();
  expect(container.textContent).toContain("chờ phòng khám xác nhận");
  expect(container.textContent).not.toContain("đã được xác nhận");
  expect(bookAppointment).toHaveBeenCalledTimes(1);
  expect(container.querySelector<HTMLButtonElement>(".btn-next")!.disabled).toBe(true);
});

it("reuses a created online appointment when retrying a failed payment URL", async () => {
  vi.mocked(http.get).mockRejectedValue(new Error("Payment temporarily unavailable"));
  await open("ONLINE");
  await submit();
  expect(container.textContent).toContain("Payment temporarily unavailable");
  await submit();
  expect(bookAppointment).toHaveBeenCalledTimes(1);
  expect(http.get).toHaveBeenCalledTimes(2);
});

it("blocks repeated submission while the booking request is still in progress", async () => {
  vi.mocked(bookAppointment).mockImplementation(() => new Promise(() => {}));
  await open();
  await act(async () => {
    const button = container.querySelector<HTMLButtonElement>(".btn-next")!;
    button.click();
    button.click();
  });
  expect(bookAppointment).toHaveBeenCalledTimes(1);
});
