package io.beep.core.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée unique. Le rôle est choisi par profil Spring :
 *   (aucun) / prod : API + redirection (adapters HTTP actifs) ;
 *   worker         : adapters HTTP désactivés, tâches de fond actives ;
 *   migrate        : exécute Flyway puis se termine ;
 *   seed           : données de démo puis arrêt.
 */
@SpringBootApplication(scanBasePackages = "io.beep.core")
public class BeepCoreApplication {
  public static void main(String[] args) {
    SpringApplication.run(BeepCoreApplication.class, args);
  }
}
