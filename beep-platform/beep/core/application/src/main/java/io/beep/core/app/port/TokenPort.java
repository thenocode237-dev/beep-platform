package io.beep.core.app.port;

public interface TokenPort {
  /** Jeton opaque aléatoire (256 bits), à transmettre à l'utilisateur. */
  String newToken();

  /** Empreinte déterministe du jeton, seule valeur persistée. */
  String hash(String token);
}
