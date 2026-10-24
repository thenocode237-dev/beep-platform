package io.beep.core.bootstrap;

import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Profil `seed` : crée un utilisateur de démo confirmé puis arrête l'application. Jamais utilisé en production. */
@Component
@Profile("seed")
class DevSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);
  static final String EMAIL = "demo@beep.localhost";
  static final String PASSWORD = "demo-password-1234";

  private final UserRepository users;
  private final PasswordHasherPort hasher;
  private final Clock clock;
  private final ConfigurableApplicationContext context;

  DevSeeder(UserRepository users, PasswordHasherPort hasher, Clock clock, ConfigurableApplicationContext context) {
    this.users = users;
    this.hasher = hasher;
    this.clock = clock;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    Email email = new Email(EMAIL);
    if (users.findByEmail(email).isEmpty()) {
      User u = User.register(UUID.randomUUID(), email, hasher.hash(PASSWORD), "fr", clock.instant()).verifyEmail(clock.instant());
      users.insert(u);
      log.info("utilisateur de démo créé : {} / {}", EMAIL, PASSWORD);
    } else {
      log.info("utilisateur de démo déjà présent : {}", EMAIL);
    }
    System.exit(SpringApplication.exit(context, () -> 0));
  }
}
