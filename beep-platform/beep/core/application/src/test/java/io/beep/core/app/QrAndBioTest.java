package io.beep.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.app.analytics.Visit;
import io.beep.core.app.bio.BioPagesUseCase;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.qr.QrCodesUseCase;
import io.beep.core.app.redirect.Outcome;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.qr.QrCode;
import io.beep.core.domain.user.User;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QrAndBioTest {
  Platform p;
  User ada;

  @BeforeEach
  void setUp() {
    p = new Platform();
    ada = p.verifiedUser("ada@example.com");
  }

  Visit visit() {
    return new Visit("203.0.113.9", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36", null, null, null, true, p.now);
  }

  static QrCodesUseCase.Command dynamic(Link link) {
    return new QrCodesUseCase.Command("dynamic", link.id(), null, null, null, null, null, null, "Affiche");
  }

  @Test
  void dynamicQrFollowsItsLinkAndCountsScansForBoth() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com/menu", null, null));
    QrCode qr = p.qr.create(ada, dynamic(link));
    assertThat(p.qr.get(ada, qr.id()).encoded()).isEqualTo("https://beep.example/q/" + qr.code());

    assertThat(p.resolveQr.execute(qr.code(), visit())).isEqualTo(new Outcome.Redirect("https://example.com/menu"));
    var e = p.sink.events.get(0);
    assertThat(e.kind()).isEqualTo(EventKind.SCAN);
    assertThat(e.qrId()).isEqualTo(qr.id());
    assertThat(e.linkId()).isEqualTo(link.id());

    // la destination change sans réimprimer
    p.updateLink.execute(ada, link.id(), new io.beep.core.app.link.UpdateLinkUseCase.Command("example.com/carte", null, null));
    assertThat(p.resolveQr.execute(qr.code(), visit())).isEqualTo(new Outcome.Redirect("https://example.com/carte"));
  }

  @Test
  void aLinkFollowedByAQrCannotBeDeleted() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    QrCode qr = p.qr.create(ada, dynamic(link));
    assertThatThrownBy(() -> p.deleteLink.execute(ada, link.id())).hasMessage("link_in_use");
    p.qr.delete(ada, qr.id());
    p.deleteLink.execute(ada, link.id());
    assertThat(p.links.byId).isEmpty();
  }

  @Test
  void dynamicQrCountsInTheQuotaStaticOnesDoNot() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    for (int i = 0; i < 5; i++) p.qr.create(ada, dynamic(link));
    assertThatThrownBy(() -> p.qr.create(ada, dynamic(link))).hasMessage("quota_exceeded");
    QrCode wifi = p.qr.create(ada, new QrCodesUseCase.Command("static", null, "wifi", Map.of("ssid", "Atelier", "password", "secret123"), "#243D30", null, "Q", 4, null));
    assertThat(wifi.dynamic()).isFalse();
    var rendered = p.qr.render(ada, wifi.id(), "pdf", null);
    assertThat(rendered.mediaType()).isEqualTo("application/pdf");
    assertThat(rendered.filename()).endsWith(".pdf");
    assertThat(new String(rendered.bytes(), StandardCharsets.UTF_8)).isEqualTo("PDF:WIFI:T:WPA;S:Atelier;P:secret123;;");
  }

  @Test
  void qrForAnotherWorkspacesLinkIsRefused() {
    Link link = p.createLink.execute(ada, new CreateLinkUseCase.Command("example.com", null, null));
    User mallory = p.verifiedUser("mallory@example.com");
    assertThatThrownBy(() -> p.qr.create(mallory, dynamic(link))).hasMessage("not_found");
    assertThatThrownBy(() -> p.qr.create(ada, new QrCodesUseCase.Command("dynamic", link.id(), null, null, "#FFFFFF", "#000000", null, null, null)))
        .hasMessage("qr_low_contrast");
  }

  @Test
  void bioPageIsServedOnItsSlugAndBlockClicksAreMeasured() {
    BioPage page = p.bio.create(ada, new BioPagesUseCase.CreateCommand("atelier-naya", "Atelier Naya"));
    assertThatThrownBy(() -> p.bio.create(ada, new BioPagesUseCase.CreateCommand("autre-page", "Autre"))).hasMessage("quota_exceeded"); // Starter : 1 page
    BioPage withBlocks = p.bio.replaceBlocks(ada, page.id(), List.of(
        new BioPagesUseCase.BlockInput(null, "heading", Map.of("text", "Nos créations")),
        new BioPagesUseCase.BlockInput(null, "link", Map.of("label", "Boutique", "url", "shop.example"))));
    String linkBlock = withBlocks.blocks().get(1).id();

    Outcome shown = p.resolveSlug.execute("atelier-naya", visit());
    assertThat(shown).isInstanceOf(Outcome.ShowBio.class);
    assertThat(((Outcome.ShowBio) shown).page().blocks()).hasSize(2);
    assertThat(p.sink.events.get(0).kind()).isEqualTo(EventKind.VIEW);

    assertThat(p.bioClick.execute(page.id(), linkBlock, visit())).isEqualTo(new Outcome.Redirect("https://shop.example"));
    assertThat(p.sink.events.get(1).kind()).isEqualTo(EventKind.BIO_CLICK);
    assertThat(p.sink.events.get(1).bioBlockId()).isEqualTo(linkBlock);
    assertThat(p.bioClick.execute(page.id(), withBlocks.blocks().get(0).id(), visit())).isEqualTo(Outcome.NOT_FOUND); // un titre n'est pas cliquable
  }

  @Test
  void editingThePageKeepsItsBlocksAndValidatesFields() {
    BioPage page = p.bio.create(ada, new BioPagesUseCase.CreateCommand("atelier-naya", "Atelier Naya"));
    p.bio.replaceBlocks(ada, page.id(), List.of(new BioPagesUseCase.BlockInput(null, "text", Map.of("text", "Bonjour"))));
    BioPage edited = p.bio.update(ada, page.id(), new BioPagesUseCase.UpdateCommand("naya", "Naya", "Objets", null, "foret", "Naya — atelier", null, null, true));
    assertThat(edited.slug().value()).isEqualTo("naya");
    assertThat(p.bioPages.findById(page.id()).orElseThrow().blocks()).hasSize(1);
    assertThat(p.resolveSlug.execute("atelier-naya", visit())).isEqualTo(Outcome.NOT_FOUND); // ancien slug libéré
    assertThatThrownBy(() -> p.bio.update(ada, page.id(), new BioPagesUseCase.UpdateCommand("naya", "Naya", null, null, "rose", null, null, null, false)))
        .hasMessage("invalid_theme");
  }
}
