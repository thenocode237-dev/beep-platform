package io.beep.core.app.port;

/** Pays d'une adresse IP depuis une base locale (aucun appel externe sur le chemin chaud). */
public interface GeoIpPort {
  /** Code ISO 3166-1 alpha-2, ou null si inconnu. */
  String country(String ip);
}
