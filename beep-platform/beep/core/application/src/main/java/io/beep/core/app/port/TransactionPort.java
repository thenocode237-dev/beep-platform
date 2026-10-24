package io.beep.core.app.port;

import java.util.function.Supplier;

/** Frontière transactionnelle : les cas d'usage ignorent JDBC et Spring. */
public interface TransactionPort {
  <T> T run(Supplier<T> work);

  default void run(Runnable work) {
    run(() -> {
      work.run();
      return null;
    });
  }
}
