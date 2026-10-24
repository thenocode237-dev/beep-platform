package io.beep.core.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.core.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jooq.JSONB;

/** Utilitaires partagés des dépôts : JSONB, curseurs de pagination, codes d'erreur PostgreSQL, motifs LIKE. */
final class Sql {
  private Sql() {}

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

  static final String UNIQUE_VIOLATION = "23505";
  static final String FOREIGN_KEY_VIOLATION = "23503";
  static final String NO_PARTITION = "23514"; // « no partition of relation … found for row » (check_violation)

  static boolean hasState(Throwable t, String state) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof SQLException s && state.equals(s.getSQLState())) return true;
    }
    return false;
  }

  // ---- JSONB ----
  static JSONB json(Map<String, ?> value) {
    try {
      return JSONB.valueOf(JSON.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  static Map<String, Object> map(JSONB value) {
    if (value == null) return Map.of();
    try {
      return JSON.readValue(value.data(), MAP);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSONB illisible", e);
    }
  }

  static Map<String, String> strings(JSONB value) {
    Map<String, String> out = new LinkedHashMap<>();
    map(value).forEach((k, v) -> { if (v != null) out.put(k, String.valueOf(v)); });
    return out;
  }

  static Map<String, Long> longs(JSONB value) {
    Map<String, Long> out = new LinkedHashMap<>();
    map(value).forEach((k, v) -> { if (v instanceof Number n) out.put(k, n.longValue()); });
    return out;
  }

  // ---- Curseurs (created_at, id) décroissants, opaques pour le client ----
  record Cursor(Instant at, UUID id) {}

  static String cursor(Instant at, UUID id) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  static Cursor cursor(String raw) {
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8).split("\\|", 2);
      return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException e) {
      throw new DomainException("invalid_request"); // curseur falsifié ou périmé : 400, pas 500
    }
  }

  /** Motif ILIKE « contient », jokers de la saisie neutralisés (caractère d'échappement : \). */
  static String contains(String query) {
    return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
