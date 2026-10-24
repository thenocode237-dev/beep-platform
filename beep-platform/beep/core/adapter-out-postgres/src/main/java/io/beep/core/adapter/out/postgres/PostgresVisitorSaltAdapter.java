package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.VisitorSaltRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import org.jooq.DSLContext;

/** Sel du jour partagé par toutes les instances (première écriture gagnante), gardé en mémoire : un accès base par jour et par instance. */
public class PostgresVisitorSaltAdapter implements VisitorSaltRepository {
  private record Cached(LocalDate day, byte[] salt) {}

  private final DSLContext dsl;
  private final SecureRandom random = new SecureRandom();
  private volatile Cached cached;

  public PostgresVisitorSaltAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public byte[] saltFor(LocalDate day) {
    Cached c = cached;
    if (c != null && c.day().equals(day)) return c.salt();
    byte[] candidate = new byte[32];
    random.nextBytes(candidate);
    dsl.execute("INSERT INTO visitor_salts (day, salt) VALUES (?::date, ?) ON CONFLICT (day) DO NOTHING", day, candidate);
    byte[] salt = (byte[]) dsl.fetchValue("SELECT salt FROM visitor_salts WHERE day = ?::date", day);
    cached = new Cached(day, salt);
    return salt;
  }

  @Override
  public int deleteBefore(LocalDate day) {
    return dsl.execute("DELETE FROM visitor_salts WHERE day < ?::date", day);
  }
}
