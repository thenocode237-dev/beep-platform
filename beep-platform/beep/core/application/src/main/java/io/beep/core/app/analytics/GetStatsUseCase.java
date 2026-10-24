package io.beep.core.app.analytics;

import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.AnalyticsRepository.Breakdown;
import io.beep.core.app.port.AnalyticsRepository.Point;
import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.app.port.AnalyticsRepository.Totals;
import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.plan.EntitlementKeys;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistiques d'un lien, d'un QR dynamique ou d'une page bio, lues dans les agrégats.
 * La fenêtre visible est bornée par la rétention du plan (Starter : 30 jours) ; `days = 1` = aujourd'hui, heure par heure (UTC).
 * Analytics de base pour tous (pays, appareil, origine) ; avancés (système, navigateur, référent, langue) selon le plan.
 */
public class GetStatsUseCase {
  public static final int RAW_RETENTION_DAYS = 395; // 13 mois d'événements bruts : borne absolue
  static final List<String> BASIC = List.of("country", "device");
  static final List<String> ADVANCED = List.of("os", "browser", "referrer", "lang");

  public record Query(Resource resource, UUID id, int days) {}

  public record StatsView(
      Resource resource,
      LocalDate from,
      LocalDate to,
      int days,
      int maxDays,
      String granularity,
      Totals totals,
      Totals secondary,
      List<Point> series,
      Map<String, List<Breakdown>> breakdowns,
      boolean advanced,
      boolean exportAllowed) {}

  private final Workspaces workspaces;
  private final AnalyticsRepository analytics;
  private final LinkRepository links;
  private final QrCodeRepository qrCodes;
  private final BioPageRepository bioPages;
  private final Clock clock;

  public GetStatsUseCase(Workspaces workspaces, AnalyticsRepository analytics, LinkRepository links, QrCodeRepository qrCodes, BioPageRepository bioPages, Clock clock) {
    this.workspaces = workspaces;
    this.analytics = analytics;
    this.links = links;
    this.qrCodes = qrCodes;
    this.bioPages = bioPages;
    this.clock = clock;
  }

  public StatsView execute(User actor, Query q) {
    Workspaces.Context ctx = workspaces.context(actor);
    requireOwned(ctx, q);
    long retention = ctx.entitlements().retentionDays();
    int maxDays = retention < 0 ? RAW_RETENTION_DAYS : (int) Math.min(retention, RAW_RETENTION_DAYS);
    int days = Math.max(1, Math.min(q.days(), maxDays));
    Instant now = clock.instant();
    LocalDate to = LocalDate.ofInstant(now, ZoneOffset.UTC);
    LocalDate from = to.minusDays(days - 1L);
    boolean advanced = ctx.entitlements().enabled(EntitlementKeys.FEATURE_ANALYTICS_ADVANCED);

    Totals totals = analytics.totals(q.resource(), q.id(), from, to);
    List<Point> series = days == 1
        ? analytics.hourly(q.resource(), q.id(), to.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
        : analytics.daily(q.resource(), q.id(), from, to);

    Map<String, List<Breakdown>> breakdowns = new LinkedHashMap<>();
    for (String dim : BASIC) breakdowns.put(dim, analytics.breakdown(q.resource(), q.id(), dim, from, to, 10));
    if (q.resource() == Resource.LINK) breakdowns.put("source", analytics.breakdown(q.resource(), q.id(), "source", from, to, 10));
    if (advanced) for (String dim : ADVANCED) breakdowns.put(dim, analytics.breakdown(q.resource(), q.id(), dim, from, to, 10));

    Totals secondary = null;
    if (q.resource() == Resource.BIO) {
      secondary = analytics.totals(Resource.BIO_CLICK, q.id(), from, to);
      breakdowns.put("block", analytics.breakdown(Resource.BIO_CLICK, q.id(), "block", from, to, 20));
    }
    return new StatsView(q.resource(), from, to, days, maxDays, days == 1 ? "hour" : "day", totals, secondary, series, breakdowns, advanced,
        ctx.entitlements().enabled(EntitlementKeys.FEATURE_EXPORT_CSV));
  }

  /** Export CSV : mêmes données que l'écran, réservé aux workspaces qui ont le droit `features.export_csv`. */
  public StatsView export(User actor, Query q) {
    StatsView view = execute(actor, q);
    if (!view.exportAllowed()) throw new DomainException("feature_unavailable");
    return view;
  }

  private void requireOwned(Workspaces.Context ctx, Query q) {
    UUID ws = switch (q.resource()) {
      case LINK -> links.findById(q.id()).map(l -> l.workspaceId()).orElse(null);
      case QR -> qrCodes.findById(q.id()).filter(c -> c.dynamic()).map(c -> c.workspaceId()).orElse(null);
      case BIO -> bioPages.findById(q.id()).map(p -> p.workspaceId()).orElse(null);
      case BIO_CLICK -> null;
    };
    if (!ctx.id().equals(ws)) throw new DomainException("not_found");
  }
}
