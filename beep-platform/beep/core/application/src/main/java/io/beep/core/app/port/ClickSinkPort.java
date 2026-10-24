package io.beep.core.app.port;

import io.beep.core.domain.analytics.ClickEvent;

/**
 * File bornée des événements de trafic, écrite par lots de façon asynchrone. Ne bloque jamais la redirection :
 * file pleine = événement abandonné (compté), la redirection continue.
 */
public interface ClickSinkPort {
  boolean offer(ClickEvent event);

  Stats stats();

  record Stats(long queued, long dropped, long written) {}
}
