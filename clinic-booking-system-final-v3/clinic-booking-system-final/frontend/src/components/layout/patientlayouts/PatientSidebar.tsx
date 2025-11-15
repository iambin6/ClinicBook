import { NavLink } from "react-router-dom";

const navItems = [
  { to: "/patient/profile", label: "Patient Profile" },
  { to: "/patient/appointments", label: "Appointment" },
  { to: "/patient/history", label: "History" },
  { to: "/patient/events", label: "Event" },
  { to: "/patient/vouchers", label: "Voucher" },
];

export default function PatientSidebar() {
  return (
    <aside className="patient-sidebar">
      <nav className="patient-sidebar__nav">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `patient-sidebar__link${isActive ? " is-active" : ""}`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}

