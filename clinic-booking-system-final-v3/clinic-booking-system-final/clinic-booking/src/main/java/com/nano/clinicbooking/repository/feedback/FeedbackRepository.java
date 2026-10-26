package com.nano.clinicbooking.repository.feedback;

import com.nano.clinicbooking.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Feedback> findByStatusOrderByCreatedAtDesc(String status);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    Optional<Feedback> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.status = 'APPROVED'")
    Double getAverageRating();

    @Query("SELECT f.rating, COUNT(f) FROM Feedback f WHERE f.status = 'APPROVED' GROUP BY f.rating")
    List<Object[]> countByRatingGroup();
}
