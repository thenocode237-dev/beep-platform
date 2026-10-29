package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.bio.BioPagesUseCase;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.domain.bio.BioPage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pages link in bio (/v1/bio-pages) : création, réglages (thème, SEO), blocs (remplacement complet, ordre = position), statistiques. */
@RestController
@RequestMapping("/v1/bio-pages")
@Profile("!worker & !migrate")
public class BioPagesController {
  record CreateRequest(String slug, String title) {}

  record UpdateRequest(String slug, String title, String description, String avatarUrl, String theme, String seoTitle, String seoDescription, String seoImageUrl,
      Boolean noindex, String primaryColor, String secondaryColor, String accentColor) {}

  record BlockRequest(String id, String type, Map<String, String> config) {}

  record BlocksRequest(List<BlockRequest> blocks) {}

  record BioList(List<Views.BioView> items) {}

  private final BioPagesUseCase bio;
  private final GetStatsUseCase stats;
  private final SessionCookies cookies;
  private final PublicSite site;

  public BioPagesController(BioPagesUseCase bio, GetStatsUseCase stats, SessionCookies cookies, PublicSite site) {
    this.bio = bio;
    this.stats = stats;
    this.cookies = cookies;
    this.site = site;
  }

  @GetMapping
  BioList list(HttpServletRequest req) {
    return new BioList(bio.list(cookies.requireUser(req)).stream().map(i -> Views.bio(i.page(), i.publicUrl(), i.views30d())).toList());
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Views.BioView> create(@RequestBody CreateRequest r, HttpServletRequest req) {
    BioPage page = bio.create(cookies.requireUser(req), new BioPagesUseCase.CreateCommand(r.slug(), r.title()));
    return ResponseEntity.status(HttpStatus.CREATED).body(view(page));
  }

  @GetMapping("/{id}")
  Views.BioView get(@PathVariable UUID id, HttpServletRequest req) {
    var item = bio.get(cookies.requireUser(req), id);
    return Views.bio(item.page(), item.publicUrl(), item.views30d());
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  Views.BioView update(@PathVariable UUID id, @RequestBody UpdateRequest r, HttpServletRequest req) {
    return view(bio.update(cookies.requireUser(req), id, new BioPagesUseCase.UpdateCommand(r.slug(), r.title(), r.description(), r.avatarUrl(), r.theme(),
        r.seoTitle(), r.seoDescription(), r.seoImageUrl(), Boolean.TRUE.equals(r.noindex()), r.primaryColor(), r.secondaryColor(), r.accentColor())));
  }

  @PutMapping(value = "/{id}/blocks", consumes = MediaType.APPLICATION_JSON_VALUE)
  Views.BioView blocks(@PathVariable UUID id, @RequestBody BlocksRequest r, HttpServletRequest req) {
    List<BioPagesUseCase.BlockInput> inputs = r.blocks() == null ? null
        : r.blocks().stream().map(b -> new BioPagesUseCase.BlockInput(b.id(), b.type(), b.config())).toList();
    return view(bio.replaceBlocks(cookies.requireUser(req), id, inputs));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest req) {
    bio.delete(cookies.requireUser(req), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/stats")
  Views.StatsResponse stats(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days, HttpServletRequest req) {
    return Views.stats(stats.execute(cookies.requireUser(req), new GetStatsUseCase.Query(Resource.BIO, id, days)));
  }

  private Views.BioView view(BioPage p) {
    return Views.bio(p, site.shortUrl(p.slug().value()), 0);
  }
}
