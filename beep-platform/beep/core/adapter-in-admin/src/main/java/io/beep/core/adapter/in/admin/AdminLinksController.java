package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.AdminLinksUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Liens côté staff : recherche, désactivation/réactivation (effet immédiat sur toutes les instances), slugs réservés. */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminLinksController {
  record LinksPage(List<AdminViews.LinkView> items, String nextCursor) {}

  record Reason(String reason) {}

  record ReservedSlugs(List<String> items) {}

  private final AdminLinksUseCase links;

  public AdminLinksController(AdminLinksUseCase links) {
    this.links = links;
  }

  @GetMapping("/links")
  LinksPage search(@RequestParam(required = false) String q, @RequestParam(required = false) UUID workspaceId, @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    var page = links.search(q, workspaceId, limit, cursor);
    return new LinksPage(page.items().stream().map(AdminViews::link).toList(), page.nextCursor());
  }

  @GetMapping("/links/{id}")
  AdminViews.LinkView get(@PathVariable UUID id) {
    return AdminViews.link(links.get(id));
  }

  @PostMapping(value = "/links/{id}/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
  AdminViews.LinkView disable(@PathVariable UUID id, @RequestBody Reason r) {
    return AdminViews.link(links.disable(id, r.reason()));
  }

  @PostMapping("/links/{id}/enable")
  AdminViews.LinkView enable(@PathVariable UUID id) {
    return AdminViews.link(links.enable(id));
  }

  @GetMapping("/reserved-slugs")
  ReservedSlugs reserved() {
    return new ReservedSlugs(links.reservedSlugs());
  }
}
