package io.beep.core.app.redirect;

import io.beep.core.domain.bio.BioPage;

/** Résultat de la résolution d'un chemin public, traduit en réponse HTTP par l'adapter entrant. */
public sealed interface Outcome {
  /** 302 vers la destination (paramètres entrants transmis). */
  record Redirect(String location) implements Outcome {}

  /** Page bio à rendre. */
  record ShowBio(BioPage page) implements Outcome {}

  /** Lien désactivé par la modération ou compte suspendu : page d'information, aucune redirection. */
  record Unavailable() implements Outcome {}

  record NotFound() implements Outcome {}

  Outcome NOT_FOUND = new NotFound();
  Outcome UNAVAILABLE = new Unavailable();
}
