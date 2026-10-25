package io.beep.core.app.admin;

import io.beep.core.app.port.AppCatalogPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.redirect.DeeplinkSimulator;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.AppEntry;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Catalogue d'applications côté staff : lecture, publication versionnée d'une entrée, activation, test d'une entrée (publiée ou candidate). */
public class AdminCatalogUseCase {
  private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_-]{1,31}");
  private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+");
  private static final Pattern PACKAGE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
  private static final Pattern SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.*");

  private final AppCatalogPort catalog;
  private final DeeplinkSimulator simulator;
  private final TransactionPort tx;
  private final Clock clock;

  public AdminCatalogUseCase(AppCatalogPort catalog, DeeplinkSimulator simulator, TransactionPort tx, Clock clock) {
    this.catalog = catalog;
    this.simulator = simulator;
    this.tx = tx;
    this.clock = clock;
  }

  public List<AppEntry> list() {
    return catalog.all();
  }

  /** Les schémas sont validés : jamais `javascript:`, `data:` ni `file:` (un modèle de catalogue ne doit pas ouvrir de code). */
  public AppEntry publish(AppEntry draft, String staffId) {
    if (draft.code() == null || !CODE.matcher(draft.code()).matches() || draft.name() == null || draft.name().isBlank() || draft.name().length() > 60) throw new DomainException("invalid_app");
    if (draft.hosts().isEmpty() || draft.hosts().size() > 20) throw new DomainException("invalid_app");
    List<String> hosts = draft.hosts().stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
    for (String h : hosts) if (!HOST.matcher(h).matches()) throw new DomainException("invalid_app");
    String ios = blank(draft.iosTemplate());
    String androidTemplate = blank(draft.androidTemplate());
    String pkg = blank(draft.androidPackage());
    if (ios != null) requireScheme(ios);
    if (androidTemplate != null) requireScheme(androidTemplate);
    if (pkg != null && !PACKAGE.matcher(pkg).matches()) throw new DomainException("invalid_app");
    if (ios == null && pkg == null && androidTemplate == null) throw new DomainException("invalid_app"); // une entrée doit savoir ouvrir au moins une plateforme
    AppEntry entry = new AppEntry(draft.code(), draft.name().strip(), hosts, ios, pkg, androidTemplate, draft.enabled(), draft.version());
    return tx.run(() -> catalog.publish(entry, staffId, clock.instant()));
  }

  public void setEnabled(String code, boolean enabled, String staffId) {
    if (catalog.all().stream().noneMatch(a -> a.code().equals(code))) throw new DomainException("not_found");
    tx.run(() -> catalog.setEnabled(code, enabled, staffId, clock.instant()));
  }

  /** Test en direct : `candidate` null = l'entrée publiée qui correspond à l'adresse. */
  public DeeplinkSimulator.Result test(String url, String profile, AppEntry candidate) {
    return candidate == null ? simulator.simulate(url, profile) : simulator.simulate(url, profile, candidate);
  }

  private static void requireScheme(String template) {
    String l = template.toLowerCase(Locale.ROOT);
    if (!SCHEME.matcher(template).matches() || l.startsWith("javascript:") || l.startsWith("data:") || l.startsWith("file:") || l.startsWith("vbscript:") || template.length() > 500) {
      throw new DomainException("invalid_app");
    }
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
