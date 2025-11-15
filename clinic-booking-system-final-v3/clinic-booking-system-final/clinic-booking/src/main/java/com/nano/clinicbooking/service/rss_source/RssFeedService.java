package com.nano.clinicbooking.service.rss_source;


import com.nano.clinicbooking.model.News;
import com.nano.clinicbooking.model.RssSource;
import com.nano.clinicbooking.repository.rss_source.NewsRepository;
import com.nano.clinicbooking.repository.rss_source.RssSourceRepository;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.rome.feed.rss.Description;
import com.rometools.rome.feed.rss.Item;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class RssFeedService {
    private final NewsRepository newsRepo;
    private final RssSourceRepository sourceRepo;

    public RssFeedService(NewsRepository newsRepo, RssSourceRepository sourceRepo) {
        this.newsRepo = newsRepo;
        this.sourceRepo = sourceRepo;
    }


    // Import bài từ 1 nguồn (Rome)
    @Transactional
    public int importFromSource(Long sourceId) throws Exception {
        RssSource src = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        // 1) Mở kết nối với User-Agent + timeout để tránh bị chặn
        URLConnection conn = new URL(src.getUrl()).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; ClinicBookingBot/1.0)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(conn));

        int count = 0;
        for (SyndEntry e : feed.getEntries()) {
            // 2) Lấy link “chắc chắn” (Atom có thể null ở getLink)
            String link = e.getLink();
            if ((link == null || link.isBlank()) && e.getLinks() != null && !e.getLinks().isEmpty()) {
                link = e.getLinks().get(0).getHref();
            }
            if ((link == null || link.isBlank()) && e.getUri() != null) {
                link = e.getUri();
            }
            if (link == null || link.isBlank()) {
                // không có link thì bỏ qua, log để biết vì sao không lưu
                System.out.println("[RSS] Skip entry vì không có link: " + e.getTitle());
                continue;
            }

            // 3) Nếu trùng link+source thì bỏ qua (tối ưu: dùng existsByLinkAndSource)
            if (newsRepo.findByLinkAndSource(link, src).isPresent()) {
                continue;
            }

            LocalDateTime pub = e.getPublishedDate() != null
                    ? LocalDateTime.ofInstant(e.getPublishedDate().toInstant(), ZoneId.systemDefault())
                    : LocalDateTime.now();

            News n = new News();
            n.setTitle(trimOrNull(e.getTitle()));
            n.setSummary(e.getDescription() != null ? e.getDescription().getValue() : null);
            n.setLink(link);
            n.setPubDate(pub);
            n.setSource(src);
            n.setImage(extractImage(e));

            // 4) Cẩn thận độ dài cột DB (nếu bạn để length=255, cân nhắc tăng lên 1000 hoặc TEXT)
            //    Ví dụ:
            //    @Column(name="link", nullable=false, length=1000)

            newsRepo.save(n);
            count++;
        }

        System.out.println("[RSS] Imported " + count + " items from source " + src.getName());
        return count;
    }
    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    // Tạo danh sách Rome Item từ DB
    public List<Item> latestItems(Integer limit) {
        List<News> news = newsRepo.findAllByOrderByPubDateDesc(PageRequest.of(0, limit == null ? 50 : limit));
        return toItems(news);
    }

    public List<Item> itemsBySource(Long sourceId, Integer limit) {
        RssSource src = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
        List<News> news = newsRepo.findBySourceOrderByPubDateDesc(src, PageRequest.of(0, limit == null ? 50 : limit));
        return toItems(news);
    }

    private List<Item> toItems(List<News> list) {
        List<Item> items = new ArrayList<>();
        for (News n : list) {
            Item item = new Item();
            item.setTitle(n.getTitle());
            item.setLink(n.getLink());
            item.setUri(n.getLink());

            Description d = new Description();
            d.setType("text/html");
            d.setValue(n.getSummary());
            item.setDescription(d);

            if (n.getPubDate() != null) {
                item.setPubDate(Date.from(n.getPubDate().atZone(ZoneId.systemDefault()).toInstant()));
            }
            items.add(item);
        }
        return items;
    }

    private String extractImage(SyndEntry entry) {
        MediaEntryModule media = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);
        if (media != null && media.getMediaContents() != null && media.getMediaContents().length > 0) {
            if (media.getMediaContents()[0].getReference() != null)
                return media.getMediaContents()[0].getReference().toString();
        }
        if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
            SyndEnclosure enc = entry.getEnclosures().get(0);
            if (enc.getUrl() != null && enc.getType() != null && enc.getType().startsWith("image")) {
                return enc.getUrl();
            }
        }
        return null;
    }

    @Transactional
    public RssSource updateSource(Long id, String name, String url, String category, RssSource.Status status) {
        RssSource s = sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));
        if (name != null) s.setName(name.trim());
        if (url != null) s.setUrl(url.trim());
        if (category != null) s.setCategory(category.trim());
        if (status != null) s.setStatus(status);
        return sourceRepo.save(s);
    }

    @Transactional
    public void deleteSourceSafe(Long sourceId) {
        if (!sourceRepo.existsById(sourceId)) return; // hoặc ném 404
        newsRepo.deleteBySourceId(sourceId);          // XÓA BẢNG CON TRƯỚC
        sourceRepo.deleteById(sourceId);              // Rồi xoá source
    }

    @Transactional
    public RssSource upsertSource(String name, String url, String category) {
        String n = name != null ? name.trim() : null;
        String u = url != null ? url.trim() : null;
        String c = category != null ? category.trim() : null;
        if (u == null || u.isEmpty()) {
            throw new IllegalArgumentException("URL must not be empty");
        }


        RssSource s = sourceRepo.findByUrl(u).orElseGet(() -> new RssSource(n, u, c));
        if (n != null) s.setName(n);
        if (c != null) s.setCategory(c);
        s.setUrl(u); // <-- quan trọng: luôn cập nhật URL
        if (s.getStatus() == null) s.setStatus(RssSource.Status.active);

        return sourceRepo.save(s);
    }
}


