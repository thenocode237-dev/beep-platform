package io.beep.bo.app.port;

public interface TokenPort {
  String newToken();

  String hash(String token);
}
