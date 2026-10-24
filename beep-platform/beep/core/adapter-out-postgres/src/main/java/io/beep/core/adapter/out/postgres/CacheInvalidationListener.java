package io.beep.core.adapter.out.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.function.Consumer;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Écoute `LISTEN beep_cache` sur une connexion dédiée (hors pool, directe même derrière un futur PgBouncer) et
 * transmet chaque notification au cache. À chaque (re)connexion, tout le cache est vidé : des notifications ont pu être manquées.
 */
public class CacheInvalidationListener implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);
  public static final String CHANNEL = "beep_cache";

  private final String url;
  private final String user;
  private final String password;
  private final Consumer<String> onNotification;
  private final Runnable onReconnect;
  private final Thread thread;
  private volatile boolean running = true;
  private volatile boolean connected;

  public CacheInvalidationListener(String url, String user, String password, Consumer<String> onNotification, Runnable onReconnect) {
    this.url = url;
    this.user = user;
    this.password = password;
    this.onNotification = onNotification;
    this.onReconnect = onReconnect;
    this.thread = Thread.ofPlatform().daemon().name("cache-listener").unstarted(this::loop);
  }

  public CacheInvalidationListener start() {
    thread.start();
    return this;
  }

  public boolean connected() {
    return connected;
  }

  private void loop() {
    long backoff = 500;
    while (running) {
      try (Connection conn = user == null || user.isBlank() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password)) {
        try (Statement st = conn.createStatement()) {
          st.execute("LISTEN " + CHANNEL);
        }
        PGConnection pg = conn.unwrap(PGConnection.class);
        connected = true;
        backoff = 500;
        onReconnect.run();
        log.info("écoute des invalidations de cache ({})", CHANNEL);
        while (running) {
          PGNotification[] notes = pg.getNotifications(5_000);
          if (notes != null) for (PGNotification n : notes) onNotification.accept(n.getParameter());
          if (!conn.isValid(2)) throw new SQLException("connexion perdue");
        }
      } catch (SQLException e) {
        connected = false;
        if (!running) return;
        log.warn("écoute des invalidations interrompue ({}), reconnexion dans {} ms", e.getMessage(), backoff);
        onReconnect.run(); // sans écoute, on ne garde rien de potentiellement périmé
        sleep(backoff);
        backoff = Math.min(backoff * 2, Duration.ofSeconds(30).toMillis());
      }
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    running = false;
    thread.interrupt();
  }
}
