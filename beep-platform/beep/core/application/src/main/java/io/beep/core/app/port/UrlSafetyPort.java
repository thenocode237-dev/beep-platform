package io.beep.core.app.port;
public interface UrlSafetyPort {
  enum Verdict { SAFE, UNSAFE, UNAVAILABLE, UNCONFIGURED }
  Verdict check(String url);
}
