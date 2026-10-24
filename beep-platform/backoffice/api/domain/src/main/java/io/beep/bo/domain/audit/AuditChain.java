package io.beep.bo.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Chaînage de hachage du journal d'audit : chaque entrée engage l'empreinte de la précédente.
 * Modifier ou supprimer une ligne passée casse toute la suite, ce qui se détecte avec {@link #verify}.
 */
public final class AuditChain {
  public static final String GENESIS = "0".repeat(64);
  private static final char SEP = '\u001f'; // séparateur de champs : n'apparaît dans aucune valeur légitime

  private AuditChain() {}

  public record Entry(long seq, AuditEvent event, String prevHash, String hash) {}

  public static String hash(String prevHash, AuditEvent e) {
    String canonical = String.join(String.valueOf(SEP), prevHash, e.at().truncatedTo(ChronoUnit.MICROS).toString(), String.valueOf(e.actorId()),
        nz(e.actorEmail()), nz(e.action()), nz(e.target()), nz(e.detail()), nz(e.ip()));
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Retourne le numéro de la première entrée incohérente, ou -1 si la chaîne est intacte. Entrées triées par seq croissant. */
  public static long verify(List<Entry> entries) {
    String prev = GENESIS;
    for (Entry e : entries) {
      if (!e.prevHash().equals(prev) || !e.hash().equals(hash(prev, e.event()))) return e.seq();
      prev = e.hash();
    }
    return -1;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
