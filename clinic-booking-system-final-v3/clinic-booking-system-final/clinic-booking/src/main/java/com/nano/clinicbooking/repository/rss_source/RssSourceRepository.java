package com.nano.clinicbooking.repository.rss_source;


import com.nano.clinicbooking.model.RssSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RssSourceRepository extends JpaRepository<RssSource, Long> {
    Optional<RssSource> findByUrl(String url);
    boolean existsByUrl(String url);                          // kiểm tra trùng URL
    Optional<RssSource> findByName(String name);              // tìm theo tên
    List<RssSource> findAllByStatus(RssSource.Status status); // lọc nguồn active/inactive
    List<RssSource> findAllByCategory(String category);
}
