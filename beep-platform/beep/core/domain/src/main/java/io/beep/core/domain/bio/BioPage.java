package io.beep.core.domain.bio;

import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Slug;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Page « link in bio » publiée sur `beepclick.app/{slug}` (même espace de noms que les liens courts). */
public record BioPage(
    UUID id,
    UUID workspaceId,
    Slug slug,
    String title,
    String description,
    String avatarUrl,
    BioTheme theme,
    BioSeo seo,
    BioColors colors,
    List<BioBlock> blocks,
    Instant createdAt,
    Instant updatedAt) {
  public static final int MAX_TITLE = 80;
  public static final int MAX_DESCRIPTION = 300;

  public BioPage {
    blocks = List.copyOf(blocks);
  }

  public static BioPage create(UUID id, UUID workspaceId, Slug slug, String title, Instant now) {
    return new BioPage(id, workspaceId, slug, title(title), null, null, BioTheme.IVOIRE, BioSeo.NONE, BioColors.NONE, List.of(), now, now);
  }

  public BioPage edit(Slug newSlug, String newTitle, String newDescription, String newAvatarUrl, BioTheme newTheme, BioSeo newSeo, Instant now) {
    return edit(newSlug, newTitle, newDescription, newAvatarUrl, newTheme, newSeo, colors, now);
  }

  public BioPage edit(Slug newSlug, String newTitle, String newDescription, String newAvatarUrl, BioTheme newTheme, BioSeo newSeo, BioColors newColors,
      Instant now) {
    return new BioPage(id, workspaceId, newSlug, title(newTitle), description(newDescription),
        newAvatarUrl == null || newAvatarUrl.isBlank() ? null : BioBlock.httpsUrl(newAvatarUrl), newTheme, newSeo, newColors, blocks, createdAt, now);
  }

  public BioPage withBlocks(List<BioBlock> newBlocks, Instant now) {
    BioBlock.requireCount(newBlocks.size());
    Set<String> ids = new HashSet<>();
    for (BioBlock b : newBlocks) if (!ids.add(b.id())) throw new DomainException("invalid_block");
    return new BioPage(id, workspaceId, slug, title, description, avatarUrl, theme, seo, colors, newBlocks, createdAt, now);
  }

  public Optional<BioBlock> block(String blockId) {
    return blocks.stream().filter(b -> b.id().equals(blockId)).findFirst();
  }

  static String title(String raw) {
    if (raw == null || raw.isBlank() || raw.strip().length() > MAX_TITLE) throw new DomainException("invalid_title");
    return raw.strip();
  }

  static String description(String raw) {
    if (raw == null || raw.isBlank()) return null;
    if (raw.strip().length() > MAX_DESCRIPTION) throw new DomainException("invalid_description");
    return raw.strip();
  }
}
