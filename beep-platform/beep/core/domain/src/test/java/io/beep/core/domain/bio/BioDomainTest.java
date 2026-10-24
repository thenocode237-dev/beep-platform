package io.beep.core.domain.bio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.link.Slug;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BioDomainTest {
  static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void blocksAreValidatedPerType() {
    assertThat(BioBlock.of("abc123", BioBlock.Type.LINK, Map.of("label", " Boutique ", "url", "shop.example")).config())
        .containsEntry("label", "Boutique").containsEntry("url", "https://shop.example");
    assertThatThrownBy(() -> BioBlock.of("abc123", BioBlock.Type.LINK, Map.of("label", "x", "url", "javascript:alert(1)"))).hasMessage("invalid_destination");
    assertThatThrownBy(() -> BioBlock.of("abc123", BioBlock.Type.IMAGE, Map.of("url", "http://img.example/a.png"))).hasMessage("invalid_block");
    assertThatThrownBy(() -> BioBlock.of("abc123", BioBlock.Type.HEADING, Map.of("text", "x", "color", "red"))).hasMessage("invalid_block");
    assertThatThrownBy(() -> BioBlock.of("bad id", BioBlock.Type.TEXT, Map.of("text", "x"))).hasMessage("invalid_block");
    var social = BioBlock.of("soc001", BioBlock.Type.SOCIAL, Map.of("instagram", "instagram.com/naya", "email", "Hello@Naya.cm"));
    assertThat(social.config()).containsEntry("email", "hello@naya.cm").containsEntry("instagram", "https://instagram.com/naya");
  }

  @Test
  void pageEnforcesBlockCountAndUniqueIds() {
    BioPage page = BioPage.create(UUID.randomUUID(), UUID.randomUUID(), new Slug("naya"), "Atelier Naya", NOW);
    List<BioBlock> many = new ArrayList<>();
    for (int i = 0; i < 51; i++) many.add(BioBlock.of("blk" + String.format("%04d", i), BioBlock.Type.TEXT, Map.of("text", "t")));
    assertThatThrownBy(() -> page.withBlocks(many, NOW)).hasMessage("too_many_blocks");
    BioBlock b = BioBlock.of("dup001", BioBlock.Type.TEXT, Map.of("text", "t"));
    assertThatThrownBy(() -> page.withBlocks(List.of(b, b), NOW)).hasMessage("invalid_block");
    assertThat(page.withBlocks(List.of(b), NOW).block("dup001")).isPresent();
  }

  @Test
  void editingValidatesTitleAvatarAndSeo() {
    BioPage page = BioPage.create(UUID.randomUUID(), UUID.randomUUID(), new Slug("naya"), "Atelier Naya", NOW);
    assertThatThrownBy(() -> page.edit(page.slug(), " ", null, null, BioTheme.FORET, BioSeo.NONE, NOW)).hasMessage("invalid_title");
    assertThatThrownBy(() -> page.edit(page.slug(), "Naya", null, "http://insecure.example/a.png", BioTheme.FORET, BioSeo.NONE, NOW)).hasMessage("invalid_block");
    assertThatThrownBy(() -> BioSeo.of("t".repeat(71), null, null, false)).hasMessage("invalid_seo");
    assertThat(BioTheme.parse("nuit")).isEqualTo(BioTheme.NUIT);
    assertThatThrownBy(() -> BioTheme.parse("rose")).hasMessage("invalid_theme");
  }
}
