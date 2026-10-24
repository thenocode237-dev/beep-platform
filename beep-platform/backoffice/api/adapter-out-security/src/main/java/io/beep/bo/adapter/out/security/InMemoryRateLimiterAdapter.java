package io.beep.bo.adapter.out.security;

import io.beep.bo.app.port.RateLimiterPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Fenêtre glissante en mémoire (une instance d'API en phase 0). */
public class InMemoryRateLimiterAdapter implements RateLimiterPort {
  private static final int MAX_KEYS = 50_000;

  private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryRateLimiterAdapter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean tryAcquire(String key, int max, Duration window) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    if (hits.size() > MAX_KEYS) hits.values().removeIf(d -> emptyAfterPrune(d, cutoff));
    Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (deque) {
      while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) deque.pollFirst();
      if (deque.size() >= max) return false;
      deque.addLast(now);
      return true;
    }
  }

  @Override
  public void reset(String key) {
    hits.remove(key);
  }

  private static boolean emptyAfterPrune(Deque<Instant> d, Instant cutoff) {
    synchronized (d) {
      while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) d.pollFirst();
      return d.isEmpty();
    }
  }
}
