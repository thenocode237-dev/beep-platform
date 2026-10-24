package io.beep.core.app.qr;

import io.beep.core.app.common.Paging;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.link.OwnedLinks;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.port.QrRendererPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.SlugGenerator;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.qr.QrCode;
import io.beep.core.domain.qr.QrContent;
import io.beep.core.domain.qr.QrFormat;
import io.beep.core.domain.qr.QrStyle;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * QR codes du workspace : création (dynamique = suit un lien, compté dans le quota ; statique = contenu figé, illimité),
 * modification, suppression, liste et export SVG/PNG/PDF.
 */
public class QrCodesUseCase {
  public record Command(String kind, UUID linkId, String contentType, Map<String, String> fields, String foreground, String background,
      String errorCorrection, Integer margin, String title) {}

  public record Item(QrCode qr, String encoded, long scans30d) {}

  public record Page(List<Item> items, String nextCursor) {}

  public record Rendered(byte[] bytes, String mediaType, String filename) {}

  public static final int DEFAULT_SIZE = 1024;

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final QrCodeRepository qrCodes;
  private final LinkRepository links;
  private final AnalyticsRepository analytics;
  private final QrRendererPort renderer;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;

  public QrCodesUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, QrCodeRepository qrCodes, LinkRepository links,
      AnalyticsRepository analytics, QrRendererPort renderer, TransactionPort tx, PublicSite site, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.qrCodes = qrCodes;
    this.links = links;
    this.analytics = analytics;
    this.renderer = renderer;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public QrCode create(User actor, Command cmd) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    QrStyle style = QrStyle.of(cmd.foreground(), cmd.background(), cmd.errorCorrection(), cmd.margin());
    Instant now = clock.instant();
    QrCode.Kind kind = parseKind(cmd.kind());
    if (kind == QrCode.Kind.STATIC) {
      QrCode qr = QrCode.fixed(UUID.randomUUID(), ctx.id(), content(cmd), style, cmd.title(), now);
      tx.run(() -> qrCodes.insert(qr));
      return qr;
    }
    if (cmd.linkId() == null) throw new DomainException("invalid_qr");
    OwnedLinks.require(links, ctx, cmd.linkId());
    QrCode qr = QrCode.dynamic(UUID.randomUUID(), ctx.id(), SlugGenerator.code(QrCode.CODE_LENGTH), cmd.linkId(), style, cmd.title(), now);
    tx.run(() -> {
      workspaceRepository.lock(ctx.id());
      QuotaPolicy.requireCreatable(ctx.entitlements(), Resource.QR, workspaces.usage(ctx.id(), Resource.QR));
      qrCodes.insert(qr);
    });
    return qr;
  }

  public QrCode update(User actor, UUID id, Command cmd) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    QrCode qr = owned(ctx, id);
    UUID linkId = null;
    QrContent content = null;
    if (qr.dynamic()) {
      linkId = cmd.linkId() == null ? qr.linkId() : OwnedLinks.require(links, ctx, cmd.linkId()).id();
    } else {
      content = cmd.contentType() == null ? qr.content() : content(cmd);
    }
    QrStyle style = QrStyle.of(cmd.foreground() == null ? qr.style().foreground() : cmd.foreground(),
        cmd.background() == null ? qr.style().background() : cmd.background(),
        cmd.errorCorrection() == null ? String.valueOf(qr.style().errorCorrection()) : cmd.errorCorrection(),
        cmd.margin() == null ? qr.style().margin() : cmd.margin());
    QrCode updated = qr.edit(linkId, content, style, cmd.title() == null ? qr.title() : cmd.title(), clock.instant());
    tx.run(() -> qrCodes.update(updated));
    return updated;
  }

  public void delete(User actor, UUID id) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    QrCode qr = owned(ctx, id);
    tx.run(() -> qrCodes.delete(qr.id()));
  }

  public Item get(User actor, UUID id) {
    QrCode qr = owned(workspaces.context(actor), id);
    return new Item(qr, qr.encodedValue(site.url()), 0);
  }

  public Page list(User actor, int limit, String cursor) {
    Workspaces.Context ctx = workspaces.context(actor);
    QrCodeRepository.Page page = qrCodes.page(ctx.id(), Paging.limit(limit), cursor);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    List<UUID> dynamicIds = page.items().stream().filter(QrCode::dynamic).map(QrCode::id).toList();
    Map<UUID, Long> scans = dynamicIds.isEmpty() ? Map.of() : analytics.totalsByResource(AnalyticsRepository.Resource.QR, dynamicIds, today.minusDays(29), today);
    return new Page(page.items().stream().map(q -> new Item(q, q.encodedValue(site.url()), scans.getOrDefault(q.id(), 0L))).toList(), page.nextCursor());
  }

  public Rendered render(User actor, UUID id, String format, Integer size) {
    QrCode qr = owned(workspaces.context(actor), id);
    QrFormat f = QrFormat.parse(format);
    int px = size == null ? DEFAULT_SIZE : Math.max(128, Math.min(size, 4096));
    byte[] bytes = renderer.render(qr.encodedValue(site.url()), qr.style(), f, px);
    String base = qr.dynamic() ? "beep-qr-" + qr.code() : "beep-qr-" + qr.id().toString().substring(0, 8);
    return new Rendered(bytes, f.mediaType(), base + "." + f.extension());
  }

  private QrCode owned(Workspaces.Context ctx, UUID id) {
    return qrCodes.findById(id).filter(q -> q.workspaceId().equals(ctx.id())).orElseThrow(() -> new DomainException("not_found"));
  }

  private static QrCode.Kind parseKind(String raw) {
    if (raw == null) return QrCode.Kind.DYNAMIC;
    try {
      return QrCode.Kind.fromDb(raw);
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_qr");
    }
  }

  private static QrContent content(Command cmd) {
    return QrContent.of(QrContent.Type.fromDb(cmd.contentType()), cmd.fields() == null ? Map.of() : cmd.fields());
  }
}
