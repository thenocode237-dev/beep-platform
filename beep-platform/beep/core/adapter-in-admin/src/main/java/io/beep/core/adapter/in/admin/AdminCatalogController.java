package io.beep.core.adapter.in.admin;

import io.beep.core.app.admin.AdminCatalogUseCase;
import io.beep.core.app.redirect.DeeplinkSimulator;
import io.beep.core.domain.link.AppEntry;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogue d'applications : publication versionnée depuis le back office, activation, test en direct. */
@RestController
@RequestMapping("/internal/admin/v1/catalog")
@Profile("!worker & !migrate")
public class AdminCatalogController {
  record AppView(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled, int version) {}

  record AppsList(List<AppView> items) {}

  record AppPublish(String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate, boolean enabled) {}

  record Enabled(boolean enabled) {}

  record Candidate(String code, String name, List<String> hosts, String iosTemplate, String androidPackage, String androidTemplate) {}

  record TestRequest(String url, String profile, Candidate candidate) {}

  private final AdminCatalogUseCase catalog;

  public AdminCatalogController(AdminCatalogUseCase catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  AppsList list() {
    return new AppsList(catalog.list().stream().map(AdminCatalogController::view).toList());
  }

  @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
  AppView publish(@PathVariable String code, @RequestBody AppPublish r, @RequestHeader(value = "X-Staff-Id", required = false) String staff) {
    return view(catalog.publish(new AppEntry(code, r.name(), r.hosts() == null ? List.of() : r.hosts(), r.iosTemplate(), r.androidPackage(), r.androidTemplate(), r.enabled(), 0), AdminViews.staff(staff)));
  }

  @PutMapping(value = "/{code}/enabled", consumes = MediaType.APPLICATION_JSON_VALUE)
  AppsList enabled(@PathVariable String code, @RequestBody Enabled r, @RequestHeader(value = "X-Staff-Id", required = false) String staff) {
    catalog.setEnabled(code, r.enabled(), AdminViews.staff(staff));
    return list();
  }

  /** Test en direct : sur l'entrée publiée qui correspond, ou sur une entrée candidate non publiée. */
  @PostMapping(value = "/test", consumes = MediaType.APPLICATION_JSON_VALUE)
  DeeplinkSimulator.Result test(@RequestBody TestRequest r) {
    AppEntry candidate = r.candidate() == null ? null
        : new AppEntry(r.candidate().code() == null ? "test" : r.candidate().code(), r.candidate().name() == null ? "Test" : r.candidate().name(),
            r.candidate().hosts() == null ? List.of() : r.candidate().hosts(), r.candidate().iosTemplate(), r.candidate().androidPackage(), r.candidate().androidTemplate(), true, 0);
    return catalog.test(r.url(), r.profile(), candidate);
  }

  private static AppView view(AppEntry e) {
    return new AppView(e.code(), e.name(), e.hosts(), e.iosTemplate(), e.androidPackage(), e.androidTemplate(), e.enabled(), e.version());
  }
}
