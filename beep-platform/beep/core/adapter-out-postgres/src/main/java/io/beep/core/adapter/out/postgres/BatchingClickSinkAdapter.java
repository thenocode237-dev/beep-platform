package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.ClickEventRepository;
import io.beep.core.app.port.ClickSinkPort;
import io.beep.core.domain.analytics.ClickEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File bornée en mémoire + écrivain unique par lots (specs §4.5, étape 7). La redirection ne fait qu'un `offer` non bloquant :
 * file pleine (base saturée ou indisponible) = événement abandonné et compté, la redirection continue.
 * Un lot en échec est retenté avec un délai croissant, puis abandonné (compté) pour ne pas bloquer la file indéfiniment.
 */
public class BatchingClickSinkAdapter implements ClickSinkPort, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(BatchingClickSinkAdapter.class);
  private static final int MAX_ATTEMPTS = 5;

  private final ClickEventRepository store;
  private final BlockingQueue<ClickEvent> queue;
  private final int batchSize;
  private final Duration flushInterval;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong written = new AtomicLong();
  private final Thread writer;
  private volatile boolean running = true;

  public BatchingClickSinkAdapter(ClickEventRepository store, int capacity, int batchSize, Duration flushInterval) {
    this.store = store;
    this.queue = new ArrayBlockingQueue<>(capacity);
    this.batchSize = batchSize;
    this.flushInterval = flushInterval;
    this.writer = Thread.ofPlatform().daemon().name("click-writer").unstarted(this::loop);
  }

  public BatchingClickSinkAdapter start() {
    writer.start();
    return this;
  }

  @Override
  public boolean offer(ClickEvent event) {
    if (queue.offer(event)) return true;
    long n = dropped.incrementAndGet();
    if (n == 1 || n % 10_000 == 0) log.warn("file d'événements pleine : {} événement(s) abandonné(s) au total", n);
    return false;
  }

  @Override
  public Stats stats() {
    return new Stats(queue.size(), dropped.get(), written.get());
  }

  private void loop() {
    List<ClickEvent> batch = new ArrayList<>(batchSize);
    while (running || !queue.isEmpty()) {
      try {
        ClickEvent first = queue.poll(flushInterval.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) continue;
        batch.add(first);
        queue.drainTo(batch, batchSize - 1);
        write(batch);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        running = false;
      } finally {
        batch.clear();
      }
    }
  }

  private void write(List<ClickEvent> batch) throws InterruptedException {
    for (int attempt = 1; ; attempt++) {
      try {
        store.insertBatch(batch);
        written.addAndGet(batch.size());
        return;
      } catch (RuntimeException e) {
        if (attempt >= MAX_ATTEMPTS || !running) {
          dropped.addAndGet(batch.size());
          log.error("lot de {} événement(s) abandonné après {} essai(s)", batch.size(), attempt, e);
          return;
        }
        log.warn("écriture des événements en échec (essai {}), nouvel essai", attempt, e);
        Thread.sleep(200L << attempt);
      }
    }
  }

  /** Arrêt propre : vide la file avant de rendre la main (au plus 10 s). */
  @Override
  public void close() throws InterruptedException {
    running = false;
    writer.join(10_000);
  }
}
