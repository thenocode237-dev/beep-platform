package io.beep.core.app.redirect;

import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.link.AppEntry;
import io.beep.core.domain.link.DeeplinkDecision;

/** Résultat de la résolution d'un chemin public, traduit en réponse HTTP par l'adapter entrant. */
public sealed interface Outcome {
  /** 302 vers la destination (paramètres entrants transmis). */
  record Redirect(String location) implements Outcome {}

  /** Page bio à rendre. */
  record ShowBio(BioPage page,boolean showBadge) implements Outcome {
    public ShowBio(BioPage page){this(page,true);}
  }

  /** Lien désactivé par la modération ou compte suspendu : page d'information, aucune redirection. */
  record Unavailable() implements Outcome {}

  record NotFound() implements Outcome {}

  /** Lien expiré : page d'information (410). */
  record Expired() implements Outcome {}

  /** Lien protégé : formulaire de mot de passe (aucun clic compté tant qu'il n'est pas validé). `wrong` : le mot de passe saisi était incorrect. */
  record PasswordRequired(boolean wrong) implements Outcome {}

  /** Ouverture d'une application : 302 vers l'intent Android, ou page intermédiaire (iOS, navigateurs intégrés). */
  record Deeplink(DeeplinkDecision.Mode mode, AppEntry.Plan plan, String os, boolean inApp, String browser) implements Outcome {}

  Outcome NOT_FOUND = new NotFound();
  Outcome EXPIRED = new Expired();
  Outcome UNAVAILABLE = new Unavailable();
}
