package io.beep.bo.bootstrap;

import io.beep.bo.app.port.PasswordHasherPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.app.port.StaffRepository;
import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.Role;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Profil `seed` : crée un compte superadmin de démo avec TOTP puis arrête l'application.
 * Le secret TOTP est affiché UNE fois dans les journaux (à scanner dans l'application d'authentification). Refusé en production.
 */
@Component
@Profile("seed")
class DevSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

  private final StaffRepository staff;
  private final PasswordHasherPort hasher;
  private final SecretCipherPort cipher;
  private final Clock clock;
  private final Environment env;
  private final ConfigurableApplicationContext context;
  private final String email;
  private final String password;
  private final String totpSecret;

  DevSeeder(StaffRepository staff, PasswordHasherPort hasher, SecretCipherPort cipher, Clock clock, Environment env, ConfigurableApplicationContext context,
      @Value("${BO_SEED_EMAIL:admin@beep.localhost}") String email, @Value("${BO_SEED_PASSWORD:staff-password-1234}") String password,
      @Value("${BO_SEED_TOTP_SECRET:}") String totpSecret) {
    this.staff = staff;
    this.hasher = hasher;
    this.cipher = cipher;
    this.clock = clock;
    this.env = env;
    this.context = context;
    this.email = email;
    this.password = password;
    this.totpSecret = totpSecret;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("le profil seed est interdit en production");
    Email address = new Email(email);
    if (staff.findByEmail(address).isPresent()) {
      log.info("compte staff de démo déjà présent : {}", address);
    } else {
      String secret = totpSecret.isBlank() ? Totp.newSecret() : totpSecret;
      staff.insert(new StaffUser(UUID.randomUUID(), address, hasher.hash(password), Role.SUPERADMIN, cipher.encrypt(secret), true, clock.instant()));
      log.info("compte staff de démo créé : {} / {}", address, password);
      log.info("secret TOTP (à enregistrer maintenant) : {}", secret);
      log.info("otpauth : {}", Totp.otpauthUri("Beepclick Back office", address.value(), secret));
    }
    System.exit(SpringApplication.exit(context, () -> 0));
  }
}
