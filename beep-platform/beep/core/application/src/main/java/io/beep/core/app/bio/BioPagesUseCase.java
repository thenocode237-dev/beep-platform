package io.beep.core.app.bio;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioColors;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.bio.BioSeo;
import io.beep.core.domain.bio.BioTheme;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.link.SlugGenerator;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pages bio du workspace (quota de stock `bio.max`) : création, édition, blocs (remplacement atomique), suppression. */
public class BioPagesUseCase {
  public record CreateCommand(String slug, String title) {}

  public record UpdateCommand(String slug, String title, String description, String avatarUrl, String theme, String seoTitle, String seoDescription,
      String seoImageUrl, boolean noindex, String primaryColor, String secondaryColor, String accentColor) {
    public UpdateCommand(String slug, String title, String description, String avatarUrl, String theme, String seoTitle, String seoDescription,
        String seoImageUrl, boolean noindex) {
      this(slug, title, description, avatarUrl, theme, seoTitle, seoDescription, seoImageUrl, noindex, null, null, null);
    }
  }

  /** Bloc saisi : identifiant absent pour un nouveau bloc. */
  public record BlockInput(String id, String type, Map<String, String> config) {}

  public record Item(BioPage page, String publicUrl, long views30d) {}

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final BioPageRepository pages;
  private final AnalyticsRepository analytics;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;

  public BioPagesUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, BioPageRepository pages, AnalyticsRepository analytics,
      TransactionPort tx, PublicSite site, Clock clock) {
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.pages = pages;
    this.analytics = analytics;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public BioPage create(User actor, CreateCommand cmd) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    BioPage page = BioPage.create(UUID.randomUUID(), ctx.id(), Slug.custom(cmd.slug()), cmd.title(), clock.instant());
    try {
      tx.run(() -> {
        workspaceRepository.lock(ctx.id());
        QuotaPolicy.requireCreatable(ctx.entitlements(), Resource.BIO, workspaces.usage(ctx.id(), Resource.BIO));
        pages.insert(page);
      });
    } catch (SlugRegistryPort.SlugTakenException e) {
      throw new DomainException("slug_taken");
    }
    return page;
  }

  public BioPage update(User actor, UUID id, UpdateCommand cmd) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    BioPage page = owned(ctx, id);
    Slug slug = cmd.slug() == null || cmd.slug().equals(page.slug().value()) ? page.slug() : Slug.custom(cmd.slug());
    BioPage updated = page.edit(slug, cmd.title(), cmd.description(), cmd.avatarUrl(), BioTheme.parse(cmd.theme()),
        BioSeo.of(cmd.seoTitle(), cmd.seoDescription(), cmd.seoImageUrl(), cmd.noindex()), colors(page.colors(), cmd), clock.instant());
    try {
      tx.run(() -> pages.update(updated));
    } catch (SlugRegistryPort.SlugTakenException e) {
      throw new DomainException("slug_taken");
    }
    return updated;
  }

  /** Couleur absente : conservée ; chaîne vide : effacée (retour à la palette du thème). */
  private static BioColors colors(BioColors current, UpdateCommand cmd) {
    return BioColors.of(cmd.primaryColor() == null ? current.primary() : cmd.primaryColor(), cmd.secondaryColor() == null ? current.secondary() : cmd.secondaryColor(),
        cmd.accentColor() == null ? current.accent() : cmd.accentColor());
  }

  public BioPage replaceBlocks(User actor, UUID id, List<BlockInput> inputs) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    BioPage page = owned(ctx, id);
    if (inputs == null) throw new DomainException("invalid_block");
    BioBlock.requireCount(inputs.size());
    List<BioBlock> blocks = new ArrayList<>();
    for (BlockInput in : inputs) {
      String blockId = in.id() == null || in.id().isBlank() ? SlugGenerator.code(10) : in.id();
      blocks.add(BioBlock.of(blockId, BioBlock.Type.fromDb(in.type()), in.config() == null ? Map.of() : in.config()));
    }
    BioPage updated = page.withBlocks(blocks, clock.instant());
    if(blocks.stream().anyMatch(b->java.util.Set.of(BioBlock.Type.VIDEO,BioBlock.Type.AUDIO,BioBlock.Type.EMAIL,BioBlock.Type.FORM).contains(b.type()))&&!ctx.entitlements().enabled("features.bio_advanced"))throw new DomainException("feature_unavailable");
    tx.run(() -> pages.replaceBlocks(page.id(), updated.blocks(), updated.updatedAt()));
    return updated;
  }

  public void delete(User actor, UUID id) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    BioPage page = owned(ctx, id);
    tx.run(() -> pages.delete(page.id()));
  }

  public List<Item> list(User actor) {
    Workspaces.Context ctx = workspaces.context(actor);
    List<BioPage> all = pages.listByWorkspace(ctx.id());
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    Map<UUID, Long> views = all.isEmpty() ? Map.of()
        : analytics.totalsByResource(AnalyticsRepository.Resource.BIO, all.stream().map(BioPage::id).toList(), today.minusDays(29), today);
    return all.stream().map(p -> new Item(p, site.shortUrl(p.slug().value()), views.getOrDefault(p.id(), 0L))).toList();
  }

  public Item get(User actor, UUID id) {
    BioPage page = owned(workspaces.context(actor), id);
    return new Item(page, site.shortUrl(page.slug().value()), 0);
  }

  private BioPage owned(Workspaces.Context ctx, UUID id) {
    return pages.findById(id).filter(p -> p.workspaceId().equals(ctx.id())).orElseThrow(() -> new DomainException("not_found"));
  }
}
