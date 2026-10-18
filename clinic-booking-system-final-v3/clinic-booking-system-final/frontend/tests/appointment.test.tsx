import { act, StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Appointment from "../src/pages/appointments/Appointment";
import { AppointmentProvider, useAppt } from "../src/contexts/AppointmentContext";
import { fetchDoctorById, fetchDoctorsBySpecialty, type Doctor } from "../src/api/doctor";
import { fetchSpecialties } from "../src/api/specialty";

vi.mock("../src/api/doctor", () => ({ fetchDoctorById: vi.fn(), fetchDoctorsBySpecialty: vi.fn() }));
vi.mock("../src/api/specialty", () => ({ fetchSpecialties: vi.fn() }));
vi.mock("../src/pages/appointments/steps/Step2", () => ({ default: function TestStep2() {
  const { state, dispatch } = useAppt();
  return <><p>Step 2: {state.doctorFullName}</p>
    <button onClick={() => dispatch({ type: "SET_SHIFT", id: 901 })}>Choose shift</button>
    <button onClick={() => dispatch({ type: "SET_STEP", step: 1 })}>Back to doctors</button></>;
} }));
vi.mock("../src/pages/appointments/steps/Step3", () => ({ default: () => <p>Step 3</p> }));
vi.mock("../src/pages/appointments/steps/Step4", () => ({ default: () => <p>Step 4</p> }));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const doctorA: Doctor = { id: 11, fullName: "Test Doctor A", specialtyId: 1, specialtyName: "Mắt" };
const doctorB: Doctor = { id: 12, fullName: "Test Doctor B", specialtyId: 1, specialtyName: "Mắt" };
const doctorC: Doctor = { id: 21, fullName: "Test Doctor C", specialtyId: 2, specialtyName: "Tim mạch" };
let root: Root;
let container: HTMLDivElement;
let navigate: ReturnType<typeof useNavigate>;
let location: ReturnType<typeof useLocation>;

function RouterState() {
  navigate = useNavigate();
  location = useLocation();
  return null;
}

function savedState() { return JSON.parse(sessionStorage.getItem("appt")!); }
function button(text: string) {
  const result = Array.from(container.querySelectorAll("button")).find(item => item.textContent?.trim() === text);
  if (!result) throw new Error(`Missing button: ${text}`);
  return result;
}
async function click(text: string) { await act(async () => { button(text).click(); }); }
async function selectSpecialty(id: number) {
  await act(async () => {
    const select = container.querySelector("select")!;
    select.value = String(id);
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
}
async function open(url = "/appointment", saved?: object) {
  if (saved) sessionStorage.setItem("appt", JSON.stringify(saved));
  await act(async () => { root.render(
    <MemoryRouter initialEntries={[url]}><RouterState />
      <StrictMode><AppointmentProvider><Appointment /></AppointmentProvider></StrictMode>
    </MemoryRouter>
  ); });
}

beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  vi.mocked(fetchDoctorById).mockImplementation(async id => id === 21 ? doctorC : doctorA);
  vi.mocked(fetchSpecialties).mockResolvedValue([
    { id: 1, name: "Mắt", description: "" }, { id: 2, name: "Tim mạch", description: "" },
  ]);
  vi.mocked(fetchDoctorsBySpecialty).mockImplementation(async id => id === 2 ? [doctorC] : [doctorA, doctorB]);
});
afterEach(async () => {
  await act(async () => { root.unmount(); });
  container.remove();
});

describe("booking navigation", () => {
  it("loads a direct step URL and follows URL navigation without bouncing between steps", async () => {
    await open("/appointment?step=2", { step: 1, specialtyId: 1, doctorId: 11, doctorFullName: doctorA.fullName });
    expect(container.textContent).toContain("Step 2:");
    expect(savedState().step).toBe(2);
    await act(async () => { await navigate("/appointment?step=1"); });
    expect(savedState().step).toBe(1);
    await click("Tiếp tục");
    expect(savedState().step).toBe(2);
    expect(location.search).toContain("step=2");
  });

  it.each(["99", "-1", "invalid", "2.5"])("normalizes invalid step %s", async step => {
    await open(`/appointment?step=${step}`);
    expect(container.textContent).toContain("Select Specialty");
    expect(savedState().step).toBe(1);
    expect(location.search).toBe("?step=1");
  });
});

describe("doctor selection", () => {
  it.each(["0", "-1", "invalid", "1.5"])("does not reuse a saved doctor for invalid doctorId %s", async id => {
    await open(`/appointment?doctorId=${id}`, { specialtyId: 1, doctorId: 11, doctorFullName: doctorA.fullName });
    expect(fetchDoctorById).not.toHaveBeenCalled();
    expect(savedState().doctorId).toBeNull();
    expect(button("Tiếp tục").disabled).toBe(true);
  });

  it("prefills the doctor's actual specialty and keeps a new doctor when going back", async () => {
    await open("/appointment?doctorId=11&specialtyId=2");
    expect(savedState().specialtyId).toBe(1);
    await act(async () => {
      Array.from(container.querySelectorAll<HTMLElement>(".doctor-card"))
        .find(card => card.textContent?.includes(doctorB.fullName))!.click();
    });
    expect(savedState().doctorId).toBe(12);
    expect(location.search).not.toContain("doctorId");
    await click("Tiếp tục");
    await click("Choose shift");
    await click("Back to doctors");
    expect(savedState().doctorId).toBe(12);
    expect(savedState().shiftId).toBe(901);
  });

  it("lets a user change specialty after entering from a doctor link", async () => {
    await open("/appointment?doctorId=11&specialtyId=1");
    await selectSpecialty(2);
    expect(savedState().specialtyId).toBe(2);
    expect(savedState().doctorId).toBeNull();
    expect(container.textContent).toContain(doctorC.fullName);
    expect(container.textContent).not.toContain(doctorA.fullName);
    expect(button("Tiếp tục").disabled).toBe(true);
  });

  it("shows a recoverable error instead of selecting an unfetched doctor", async () => {
    vi.mocked(fetchDoctorById).mockRejectedValue(new Error("offline"));
    await open("/appointment?doctorId=11&specialtyId=1");
    expect(container.textContent).toContain("Không thể tải thông tin bác sĩ");
    expect(savedState().doctorId).toBeNull();
    expect(button("Tiếp tục").disabled).toBe(true);
  });

  it("ignores a late response for the previous doctor's URL", async () => {
    let resolveFirst!: (value: Doctor) => void;
    const first = new Promise<Doctor>(resolve => { resolveFirst = resolve; });
    vi.mocked(fetchDoctorById).mockImplementation(id => id === 11 ? first : Promise.resolve(doctorC));
    await open("/appointment?doctorId=11");
    expect(container.textContent).toContain("Đang tải thông tin bác sĩ");
    await act(async () => { await navigate("/appointment?doctorId=21"); });
    await act(async () => { resolveFirst(doctorA); });
    expect(savedState().doctorId).toBe(21);
    expect(savedState().specialtyId).toBe(2);
  });

  it("ignores a late doctor list after switching specialties", async () => {
    let resolveFirst!: (value: Doctor[]) => void;
    const first = new Promise<Doctor[]>(resolve => { resolveFirst = resolve; });
    vi.mocked(fetchDoctorsBySpecialty).mockImplementation(id => id === 1 ? first : Promise.resolve([doctorC]));
    await open("/appointment?specialtyId=1");
    await selectSpecialty(2);
    await act(async () => { resolveFirst([doctorA]); });
    expect(container.textContent).toContain(doctorC.fullName);
    expect(container.textContent).not.toContain(doctorA.fullName);
  });

  it("handles list request failures and blocks continuation", async () => {
    vi.mocked(fetchDoctorsBySpecialty).mockRejectedValue(new Error("offline"));
    await open("/appointment?specialtyId=1");
    expect(container.textContent).toContain("Không thể tải danh sách bác sĩ");
    expect(button("Tiếp tục").disabled).toBe(true);
  });
});
