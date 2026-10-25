package io.beep.core.app.port;
import java.util.Map;
import java.util.UUID;
public interface PrivacyRepository {
  Map<String,Object> export(UUID userId);
  void erase(UUID userId);
}
