package com.nano.clinicbooking.controller.rss_source;
import com.nano.clinicbooking.model.News;
import com.nano.clinicbooking.model.RssSource;
import com.nano.clinicbooking.repository.rss_source.NewsRepository;
import com.nano.clinicbooking.repository.rss_source.RssSourceRepository;
import com.nano.clinicbooking.service.rss_source.RssFeedService;
import com.nano.clinicbooking.utils.UrlMapping;
import com.rometools.rome.feed.rss.Channel;
import com.rometools.rome.feed.rss.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController

@RequestMapping(UrlMapping.RSS)
public class RssFeedController {

    private final RssFeedService feedService;
    private final RssSourceRepository sourceRepo;
  private final NewsRepository newsRepo;
    public RssFeedController(RssFeedService feedService, RssSourceRepository sourceRepo , NewsRepository newsRepo) {
        this.feedService = feedService;
        this.sourceRepo = sourceRepo;
        this.newsRepo = newsRepo;
    }

    // RSS tổng hợp
    @GetMapping(UrlMapping.GET_ALL_RSS)
    public List<RssSource> getAllSources() {
        return sourceRepo.findAll();
    }

    //  Lấy chi tiết nguồn theo ID
    @GetMapping(UrlMapping.GET_RSS_BY_ID)
    public RssSource getById(@PathVariable Long id) {
        return sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));
    }

    //  Tạo nguồn mới
    @PostMapping(UrlMapping.CREATE_RSS)
    public RssSource createSource(@RequestBody RssSource req) {
        return feedService.upsertSource(req.getName(), req.getUrl(), req.getCategory());
    }

    //  Cập nhật nguồn
    @PutMapping(UrlMapping.UPDATE_RSS)
    public RssSource updateSource(@PathVariable Long id, @RequestBody RssSource req) {
        RssSource src = sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));
        src.setName(req.getName());
        src.setUrl(req.getUrl());
        src.setCategory(req.getCategory());
        src.setStatus(req.getStatus());
        return sourceRepo.save(src);
    }

    //  Xoá nguồn RSS
    @DeleteMapping(UrlMapping.DELETE_RSS_BY_ID)
    public ResponseEntity<?> deleteSource(@PathVariable Long id) {
        if (!sourceRepo.existsById(id)) return ResponseEntity.notFound().build();
        int deletedNews = newsRepo.deleteBySourceId(id);
        sourceRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deletedSourceId", id, "deletedNews", deletedNews));
    }
    //  Import RSS feed theo ID
    @PostMapping(UrlMapping.IMPORT_RSS)
    public String importFeed(@PathVariable Long id) throws Exception {
        int count = feedService.importFromSource(id);
        return "Imported " + count + " items.";
    }


    //  Xuất feed tổng hợp
    @GetMapping(UrlMapping.RSS_FEED)
    public Channel allFeed(HttpServletRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null).build().toUriString();
        Channel ch = new Channel();
        ch.setFeedType("rss_2.0");
        ch.setTitle("All RSS Sources Feed");
        ch.setLink(baseUrl);
        ch.setDescription("Combined RSS Feed");
        ch.setPubDate(new Date());
        List<Item> items = feedService.latestItems(20);
        ch.setItems(items);
        return ch;
    }

    //  Xuất feed theo nguồn cụ thể
    @GetMapping(UrlMapping.RSS_FEED_BY_SOURCE)
    public Channel feedBySource(@PathVariable Long id, HttpServletRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null).build().toUriString();
        RssSource src = sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));
        Channel ch = new Channel();
        ch.setFeedType("rss_2.0");
        ch.setTitle(src.getName());
        ch.setDescription("RSS Feed for source: " + src.getName());
        ch.setLink(baseUrl);
        ch.setPubDate(new Date());
        ch.setItems(feedService.itemsBySource(id, 20));
        return ch;
    }
    // Lấy tin tức mới nhất
    @GetMapping("/news/latest")
    public List<News> getLatestNews(
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return newsRepo.findAllByOrderByPubDateDesc(PageRequest.of(0, limit));
    }
    // Lấy tin tức theo nguồn
    @GetMapping("/news/source/{id}")
    public List<News> getNewsBySource(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        RssSource src = sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));

        return newsRepo.findBySourceOrderByPubDateDesc(src, PageRequest.of(0, limit));
    }
}
