package com.nano.clinicbooking.repository.rss_source;


import com.nano.clinicbooking.model.News;
import com.nano.clinicbooking.model.RssSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News,Long> {
    List<News> findAllByOrderByPubDateDesc(Pageable pageable);
    List<News> findBySourceOrderByPubDateDesc(RssSource source, Pageable pageable);
    Optional<News> findByLinkAndSource(String link, RssSource source);
    @Transactional
    @Modifying
    @Query("delete from News n where n.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") Long sourceId);

}
