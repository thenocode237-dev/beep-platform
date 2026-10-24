package io.beep.bo.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Profils : (aucun)/prod = API ; migrate = Flyway puis arrêt ; seed = compte staff de démo puis arrêt. */
@SpringBootApplication(scanBasePackages = "io.beep.bo")
public class BackofficeApplication {
  public static void main(String[] args) {
    SpringApplication.run(BackofficeApplication.class, args);
  }
}
