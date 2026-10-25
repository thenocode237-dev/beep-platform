package io.beep.core.app.auth;

import io.beep.core.app.port.InvitationRepository;
import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.RateLimiterPort;
import io.beep.core.app.port.SecretCipherPort;
import io.beep.core.app.port.SessionRepository;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.invitation.Invitation;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Doublures en mémoire : les cas d'usage se testent sans framework ni base. */
public final class Fakes {
  private Fakes() {}

  public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

  public static class Users implements UserRepository {
    public final Map<UUID, User> byId = new HashMap<>();

    public Optional<User> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<User> findByEmail(Email e) { return byId.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
    public void insert(User u) {
      if (findByEmail(u.email()).isPresent()) throw new DuplicateEmailException();
      byId.put(u.id(), u);
    }
    public void update(User u) { byId.put(u.id(), u); }
    public Page page(int limit, String cursor, String query) {
      return new Page(byId.values().stream().filter(u -> query == null || u.email().value().contains(query.toLowerCase())).toList(), null);
    }
  }

  public static class Sessions implements SessionRepository {
    public final Map<String, Session> byHash = new HashMap<>();

    public void insert(Session s) { byHash.put(s.tokenHash(), s); }
    public Optional<Session> findActive(String h, Instant now) { return Optional.ofNullable(byHash.get(h)).filter(s -> s.expiresAt().isAfter(now)); }
    public void delete(String h) { byHash.remove(h); }
    public void deleteAllForUser(UUID id) { byHash.values().removeIf(s -> s.userId().equals(id)); }
    public int deleteExpired(Instant now) { return 0; }
  }

  public static class Tokens implements OneTimeTokenRepository {
    record Entry(UUID userId, Purpose purpose, Instant expiresAt, boolean[] used) {}

    public final Map<String, Entry> byHash = new HashMap<>();

    public void insert(UUID userId, Purpose p, String hash, Instant exp) { byHash.put(hash, new Entry(userId, p, exp, new boolean[1])); }
    public Optional<UUID> consume(Purpose p, String hash, Instant now) {
      Optional<UUID> found = find(p, hash, now);
      found.ifPresent(id -> byHash.get(hash).used[0] = true);
      return found;
    }
    public Optional<UUID> find(Purpose p, String hash, Instant now) {
      Entry e = byHash.get(hash);
      if (e == null || e.purpose != p || e.used[0] || !e.expiresAt.isAfter(now)) return Optional.empty();
      return Optional.of(e.userId);
    }
    public int deleteExpired(Instant now) { return 0; }
  }

  public static class Invitations implements InvitationRepository {
    public final Map<Email, Invitation> byEmail = new HashMap<>();

    public Invitation upsert(Invitation i) {
      Invitation existing = byEmail.get(i.email());
      Invitation saved = existing != null && existing.usedAt() == null ? new Invitation(existing.id(), i.email(), i.invitedBy(), i.createdAt(), i.expiresAt(), null) : i;
      byEmail.put(i.email(), saved);
      return saved;
    }
    public Optional<Invitation> findByEmail(Email e) { return Optional.ofNullable(byEmail.get(e)); }
    public boolean consume(Email e, Instant now) {
      Invitation i = byEmail.get(e);
      if (i == null || !i.usable(now)) return false;
      byEmail.put(e, new Invitation(i.id(), i.email(), i.invitedBy(), i.createdAt(), i.expiresAt(), now));
      return true;
    }
    public Page page(int limit, String cursor) { return new Page(new ArrayList<>(byEmail.values()), null); }
  }

  public static class Hasher implements PasswordHasherPort {
    public String hash(String raw) { return "h:" + raw; }
    public boolean matches(String raw, String hash) { return hash.equals("h:" + raw); }
  }

  public static class TokenGen implements TokenPort {
    int n;
    public String newToken() { return "tok" + (++n); }
    public String hash(String t) { return "hash:" + t; }
  }

  public static class Cipher implements SecretCipherPort {
    public String encrypt(String p) { return "enc:" + p; }
    public String decrypt(String c) { return c.substring(4); }
  }

  public static class Mail implements MailPort {
    public final List<String> sent = new ArrayList<>();
    public String lastToken;

    public void sendVerification(Email to, String locale, String token) { sent.add("verify:" + to); lastToken = token; }
    public void sendPasswordReset(Email to, String locale, String token) { sent.add("reset:" + to); lastToken = token; }
    public void sendAlreadyRegistered(Email to, String locale) { sent.add("already:" + to); }
    public void sendMagicLink(Email to, String locale, String token) { sent.add("magic:" + to); lastToken = token; }
    public void sendInvitation(Email to, String locale) { sent.add("invite:" + to); }
    public void sendInvitationRequired(Email to, String locale) { sent.add("invite-required:" + to); }
    public void sendReceipt(Email to, String locale, String invoiceNumber, String planName, long amountXaf, java.time.Instant validUntil, byte[] invoicePdf) { sent.add("receipt:" + to + ":" + invoiceNumber); }
    public void sendRenewalReminder(Email to, String locale, String planName, boolean trial, int daysLeft, java.time.Instant dueAt) { sent.add("reminder:" + to + ":d" + daysLeft); }
    public void sendGraceNotice(Email to, String locale, String planName, java.time.Instant graceEnd) { sent.add("grace:" + to); }
    public void sendPlanExpired(Email to, String locale, String planName) { sent.add("expired:" + to); }
  }

  public static class Limiter implements RateLimiterPort {
    final Map<String, Integer> counts = new HashMap<>();

    public boolean tryAcquire(String key, int max, Duration window) { return counts.merge(key, 1, Integer::sum) <= max; }
    public void reset(String key) { counts.remove(key); }
  }

  public static class Tx implements TransactionPort {
    public <T> T run(Supplier<T> work) { return work.get(); }
  }
}
