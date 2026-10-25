package io.beep.core.app.port;

import io.beep.core.domain.link.AppEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Catalogue d'applications publié (lecture : chemin chaud, mis en cache ; écriture : publication depuis le back office, versionnée). */
public interface AppCatalogPort {
  /** Application active qui revendique cet hôte (suffixe de domaine), le cas échéant. */
  Optional<AppEntry> match(String host);

  /** Toutes les entrées (actives ou non), pour le back office. */
  List<AppEntry> all();

  /** Crée ou remplace une entrée : la version est incrémentée et un instantané est conservé. */
  AppEntry publish(AppEntry entry, String staffId, Instant at);

  void setEnabled(String code, boolean enabled, String staffId, Instant at);
}
