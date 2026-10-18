package com.nano.clinicbooking.repository.shiftSlot;

import com.nano.clinicbooking.model.ShiftSlot;
import com.nano.clinicbooking.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ShiftSlotRepository extends JpaRepository<ShiftSlot, Long> {
    List<ShiftSlot> findByShiftIdOrderBySlotNumberAsc(Long shiftId);
    Optional<ShiftSlot> findFirstByShiftIdAndStatusOrderBySlotNumberAsc(Long shiftId, SlotStatus status);

    // Lock candidate rows until the booking transaction commits. Also exclude
    // occupied slots whose status may be stale; slot_id is unique in appointments.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot from ShiftSlot slot
            where slot.shift.id = :shiftId
              and slot.status = com.nano.clinicbooking.enums.SlotStatus.AVAILABLE
              and not exists (select a.id from Appointment a where a.slot = slot)
            order by slot.slotNumber asc
            """)
    List<ShiftSlot> findAvailableSlotsForUpdate(@Param("shiftId") Long shiftId);
}
