package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.link.DeleteLinkUseCase;
import io.beep.core.app.link.LinkRulesCommand;
import io.beep.core.app.link.ListLinksUseCase;
import io.beep.core.app.link.UpdateLinkUseCase;
import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.domain.link.Link;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Liens courts de l'espace client (/v1/links) : création en un champ, modification, suppression, statistiques et export CSV. */
@RestController
@RequestMapping("/v1/links")
@Profile("!worker & !migrate")
public class LinksController {
  record RulesRequest(String expiresAt, String password, Boolean deeplink, OnelinkRequest onelink, List<RouteRequest> routes, List<VariantRequest> variants, UtmRequest utm) {}

  record OnelinkRequest(String ios, String android, String desktop) {}

  record RouteRequest(java.util.Set<String> countries, java.util.Set<String> os, java.util.Set<String> devices, java.util.Set<String> languages, String url) {}

  record VariantRequest(String url, int weight) {}

  record UtmRequest(String source, String medium, String campaign, String term, String content) {}

  record LinkRequest(String destination, String slug, String title, String domain, RulesRequest rules) {}

  static LinkRulesCommand rules(RulesRequest r) {
    if (r == null) return null;
    return new LinkRulesCommand(r.expiresAt(), r.password(), r.deeplink(), r.onelink() == null ? null : new LinkRulesCommand.Onelink(r.onelink().ios(), r.onelink().android(), r.onelink().desktop()),
        r.routes() == null ? null : r.routes().stream().map(x -> new LinkRulesCommand.RouteCmd(x.countries(), x.os(), x.devices(), x.languages(), x.url())).toList(),
        r.variants() == null ? null : r.variants().stream().map(x -> new LinkRulesCommand.VariantCmd(x.url(), x.weight())).toList(),
        r.utm() == null ? null : new LinkRulesCommand.UtmCmd(r.utm().source(), r.utm().medium(), r.utm().campaign(), r.utm().term(), r.utm().content()));
  }

  record LinksPage(List<Views.LinkView> items, String nextCursor) {}

  private final CreateLinkUseCase create;
  private final UpdateLinkUseCase update;
  private final DeleteLinkUseCase delete;
  private final ListLinksUseCase list;
  private final GetStatsUseCase stats;
  private final SessionCookies cookies;
  private final PublicSite site;

  public LinksController(CreateLinkUseCase create, UpdateLinkUseCase update, DeleteLinkUseCase delete, ListLinksUseCase list, GetStatsUseCase stats,
      SessionCookies cookies, PublicSite site) {
    this.create = create;
    this.update = update;
    this.delete = delete;
    this.list = list;
    this.stats = stats;
    this.cookies = cookies;
    this.site = site;
  }

  @GetMapping
  LinksPage list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor,
      HttpServletRequest req) {
    var page = list.execute(cookies.requireUser(req), q, limit, cursor);
    return new LinksPage(page.items().stream().map(i -> view(i.link(), i.clicks30d())).toList(), page.nextCursor());
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Views.LinkView> create(@RequestBody LinkRequest r, HttpServletRequest req) {
    Link link = create.execute(cookies.requireUser(req), new CreateLinkUseCase.Command(r.destination(), r.slug(), r.title(), r.domain(), rules(r.rules())));
    return ResponseEntity.status(HttpStatus.CREATED).body(view(link, 0L));
  }

  @GetMapping("/{id}")
  Views.LinkView get(@PathVariable UUID id, HttpServletRequest req) {
    return view(list.get(cookies.requireUser(req), id), null);
  }

  @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  Views.LinkView update(@PathVariable UUID id, @RequestBody LinkRequest r, HttpServletRequest req) {
    return view(update.execute(cookies.requireUser(req), id, new UpdateLinkUseCase.Command(r.destination(), r.slug(), r.title(), rules(r.rules()))), null);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest req) {
    delete.execute(cookies.requireUser(req), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/stats")
  Views.StatsResponse stats(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days, HttpServletRequest req) {
    return Views.stats(stats.execute(cookies.requireUser(req), new GetStatsUseCase.Query(Resource.LINK, id, days)));
  }

  @GetMapping("/{id}/stats.csv")
  ResponseEntity<byte[]> csv(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days, HttpServletRequest req) {
    var view = stats.export(cookies.requireUser(req), new GetStatsUseCase.Query(Resource.LINK, id, days));
    return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"beep-stats-" + id.toString().substring(0, 8) + "-" + view.from() + "-" + view.to() + ".csv\"")
        .body(Views.csv(view).getBytes(StandardCharsets.UTF_8));
  }

  private Views.LinkView view(Link l, Long clicks) {
    return Views.link(l, site.shortUrl(l), clicks);
  }
}
