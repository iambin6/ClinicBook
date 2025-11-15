package com.nano.clinicbooking.service.user;

import com.nano.clinicbooking.dto.EntityConverter;
import com.nano.clinicbooking.enums.UserEventStatus;
import com.nano.clinicbooking.model.Event;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.model.UserEvent;
import com.nano.clinicbooking.repository.event_voucher.EventRepository;
import com.nano.clinicbooking.repository.event_voucher.UserEventRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.dto.response.UserEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserEventService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final UserEventRepository userEventRepository;

    public UserEventResponse registerUserForEvent(Long userId, Long eventId) {
        // 🔒 Kiểm tra xem user đã đăng ký chưa
        if (userEventRepository.existsByUserIdAndEventId(userId, eventId)) {
            throw new RuntimeException("Bạn đã đăng ký sự kiện này rồi!");
        }

        // 🔍 Tìm user và event
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện"));

        // 📝 Tạo bản ghi mới
        UserEvent userEvent = UserEvent.builder()
                .user(user)
                .event(event)
                .registeredAt(LocalDateTime.now())
                .status(UserEventStatus.PENDING)
                .build();

        userEventRepository.save(userEvent);

        // 🧭 Trả về response
        return mapToResponse(userEvent);
    }

    public List<UserEventResponse> getUserEvents(Long userId) {
        return userEventRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private UserEventResponse mapToResponse(UserEvent userEvent) {
        Event event = userEvent.getEvent();
        User user = userEvent.getUser();
        return UserEventResponse.builder()
                .id(userEvent.getId())
                .userId(user != null ? user.getId() : null)
                .userFullName(user != null ? user.getFullName() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .userPhone(user != null ? user.getPhoneNumber() : null)
                .eventId(event != null ? event.getId() : null)
                .eventName(event != null ? event.getTitle() : null)
                .eventDescription(event != null ? event.getDescription() : null)
                .eventLocation(event != null ? event.getLocation() : null)
                .eventStart(event != null ? event.getStartDate() : null)
                .eventEnd(event != null ? event.getEndDate() : null)
                .registeredAt(userEvent.getRegisteredAt())
                .status(userEvent.getStatus().name())
                .build();
    }
}
