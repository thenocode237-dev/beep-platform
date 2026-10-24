package io.beep.core.bootstrap;

import io.beep.core.app.bio.BioPagesUseCase;
import io.beep.core.app.link.CreateLinkUseCase;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.app.qr.QrCodesUseCase;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Profil `seed` : utilisateur de démo confirmé, avec un lien `demo` (cible du test de charge), un QR dynamique et une page bio
 * `atelier-demo`, puis arrêt. Idempotent. Refusé en production.
 */
@Component
@Profile("seed")
class DevSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);
  static final String EMAIL = "demo@beep.localhost";
  static final String PASSWORD = "demo-password-1234";

  private final UserRepository users;
  private final PasswordHasherPort hasher;
  private final CreateLinkUseCase createLink;
  private final QrCodesUseCase qrCodes;
  private final BioPagesUseCase bioPages;
  private final SlugRegistryPort slugs;
  private final Clock clock;
  private final Environment env;
  private final ConfigurableApplicationContext context;

  DevSeeder(UserRepository users, PasswordHasherPort hasher, CreateLinkUseCase createLink, QrCodesUseCase qrCodes, BioPagesUseCase bioPages, SlugRegistryPort slugs,
      Clock clock, Environment env, ConfigurableApplicationContext context) {
    this.users = users;
    this.hasher = hasher;
    this.createLink = createLink;
    this.qrCodes = qrCodes;
    this.bioPages = bioPages;
    this.slugs = slugs;
    this.clock = clock;
    this.env = env;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("le profil seed est interdit en production");
    Email email = new Email(EMAIL);
    User user = users.findByEmail(email).orElseGet(() -> {
      User u = User.register(UUID.randomUUID(), email, hasher.hash(PASSWORD), "fr", clock.instant()).verifyEmail(clock.instant());
      users.insert(u);
      log.info("utilisateur de démo créé : {} / {}", EMAIL, PASSWORD);
      return u;
    });
    if (!slugs.isTaken(new Slug("demo"))) {
      Link link = createLink.execute(user, new CreateLinkUseCase.Command("https://example.com/?source=beep-demo", "demo", "Lien de démonstration"));
      qrCodes.create(user, new QrCodesUseCase.Command("dynamic", link.id(), null, null, null, null, null, null, "QR de démonstration"));
      log.info("lien de démo : /demo (et son QR dynamique)");
    }
    if (!slugs.isTaken(new Slug("atelier-demo"))) {
      var page = bioPages.create(user, new BioPagesUseCase.CreateCommand("atelier-demo", "Atelier Démo"));
      bioPages.replaceBlocks(user, page.id(), List.of(
          new BioPagesUseCase.BlockInput(null, "text", Map.of("text", "Une page bio de démonstration.")),
          new BioPagesUseCase.BlockInput(null, "button", Map.of("label", "Visiter la boutique", "url", "https://example.com/boutique")),
          new BioPagesUseCase.BlockInput(null, "link", Map.of("label", "Nos coulisses", "url", "https://example.com/coulisses")),
          new BioPagesUseCase.BlockInput(null, "social", Map.of("instagram", "https://instagram.com/example", "email", "hello@example.com"))));
      log.info("page bio de démo : /atelier-demo");
    }
    System.exit(SpringApplication.exit(context, () -> 0));
  }
}
