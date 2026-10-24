package io.beep.core.domain.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkDomainTest {
  @Test
  void customSlugsAreValidatedAndReservedOnesRefusedWhateverTheCase() {
    assertThat(Slug.custom(" promo-2026 ").value()).isEqualTo("promo-2026");
    for (String bad : new String[] {"ab", "-start", "with space", "é-accent", "a/b", "x".repeat(65), null}) {
      assertThatThrownBy(() -> Slug.custom(bad)).hasMessage("invalid_slug");
    }
    for (String reserved : new String[] {"login", "LOGIN", "App", "pricing", "healthz", "dashboard", "beep", "support"}) {
      assertThatThrownBy(() -> Slug.custom(reserved)).hasMessage("reserved_slug");
    }
  }

  @Test
  void generatedSlugsAreBase62SevenCharactersAndDistinct() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 2000; i++) {
      Slug s = SlugGenerator.next();
      assertThat(s.value()).matches("[0-9A-Za-z]{7}");
      seen.add(s.value());
    }
    assertThat(seen).hasSizeGreaterThan(1990);
    assertThat(SlugGenerator.next(new Random(42), 7)).isEqualTo(SlugGenerator.next(new Random(42), 7)); // déterministe à graine égale
  }

  @Test
  void reservedListCoversEveryWebRouteOfTheSpec() {
    assertThat(ReservedSlugs.all()).contains("app", "login", "signup", "_b", "_next", ".well-known", "en", "fr", "q", "verify-email");
  }

  @Test
  void destinationsAcceptHttpOnlyAndAddHttpsWhenMissing() {
    assertThat(Destination.parse("example.com/path?a=1").url()).isEqualTo("https://example.com/path?a=1");
    assertThat(Destination.parse("http://shop.example.cm").url()).isEqualTo("http://shop.example.cm");
    for (String bad : new String[] {"javascript:alert(1)", "ftp://x.example", "https://user:pw@bank.example", "https://", "   ", "https://exa mple.com",
        "data:text/html,hi", "https://" + "a".repeat(2050) + ".com", "intranet"}) {
      assertThatThrownBy(() -> Destination.parse(bad)).as(bad).hasMessage("invalid_destination");
    }
  }

  @Test
  void destinationPointingToBeepItselfIsALoop() {
    assertThatThrownBy(() -> Destination.parse("https://Beep.localhost/abc").requireNotHost("beep.localhost")).hasMessage("destination_loop");
    assertThatCode(() -> Destination.parse("https://example.com").requireNotHost("beep.localhost")).doesNotThrowAnyException();
  }

  @Test
  void incomingQueryIsForwardedBeforeTheFragment() {
    Destination d = Destination.parse("https://shop.example/p?id=1#top");
    assertThat(d.withQuery("utm_source=wa")).isEqualTo("https://shop.example/p?id=1&utm_source=wa#top");
    assertThat(Destination.parse("https://shop.example").withQuery("a=b")).isEqualTo("https://shop.example?a=b");
    assertThat(d.withQuery(null)).isEqualTo(d.url());
  }

  @Test
  void disablingRequiresAReasonAndEnablingClearsIt() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Link link = Link.create(UUID.randomUUID(), UUID.randomUUID(), new Slug("abc"), Destination.parse("example.com"), "  Promo ", now);
    assertThat(link.title()).isEqualTo("Promo");
    assertThatThrownBy(() -> link.disable("", now)).hasMessage("invalid_reason");
    Link disabled = link.disable("hameçonnage signalé", now);
    assertThat(disabled.disabled()).isTrue();
    assertThat(disabled.enable(now).disabledReason()).isNull();
    assertThatThrownBy(() -> link.edit(link.slug(), link.destination(), "t".repeat(201), now)).hasMessage("invalid_title");
  }
}
