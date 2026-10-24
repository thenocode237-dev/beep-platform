package io.beep.core.app.port;

import io.beep.core.domain.link.Slug;

/**
 * Espace de noms unique des chemins publics : un slug désigne soit un lien, soit une page bio.
 * Les dépôts de liens et de pages y inscrivent leurs slugs dans la même transaction.
 */
public interface SlugRegistryPort {
  boolean isTaken(Slug slug);

  /** Slug déjà attribué (course entre deux créations, ou contrôle préalable dépassé). */
  class SlugTakenException extends RuntimeException {
    public SlugTakenException() {
      super("slug taken");
    }
  }
}
