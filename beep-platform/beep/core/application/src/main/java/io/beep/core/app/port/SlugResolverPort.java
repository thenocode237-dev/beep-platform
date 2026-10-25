package io.beep.core.app.port;

import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.link.LinkRules;
import java.util.Optional;
import java.util.UUID;

/**
 * Chemin chaud de la redirection : résolution d'un slug ou d'un code QR. L'implémentation est mise en cache en mémoire
 * (aucun accès base sur un succès de cache) et invalidée par LISTEN/NOTIFY à chaque modification.
 */
public interface SlugResolverPort {
  Resolution resolve(String slug);

  Optional<QrTarget> resolveQr(String code);

  Optional<PublishedBio> bioPage(UUID pageId);

  record PublishedBio(BioPage page, boolean workspaceSuspended,boolean showBadge) {
    public PublishedBio(BioPage page,boolean workspaceSuspended){this(page,workspaceSuspended,true);}
  }

  record Resolution(Kind kind, UUID targetId, UUID workspaceId, String destination, boolean disabled, boolean workspaceSuspended, LinkRules rules, String passwordHash) {
    public static final Resolution NONE = new Resolution(Kind.NONE, null, null, null, false, false);

    public Resolution(Kind kind, UUID targetId, UUID workspaceId, String destination, boolean disabled, boolean workspaceSuspended) {
      this(kind, targetId, workspaceId, destination, disabled, workspaceSuspended, LinkRules.NONE, null);
    }

    public enum Kind {
      LINK,
      BIO,
      NONE
    }

    public boolean unavailable() {
      return disabled || workspaceSuspended;
    }
  }

  record QrTarget(UUID qrId, UUID workspaceId, UUID linkId, String destination, boolean linkDisabled, boolean workspaceSuspended, LinkRules rules, String passwordHash) {
    public QrTarget(UUID qrId, UUID workspaceId, UUID linkId, String destination, boolean linkDisabled, boolean workspaceSuspended) {
      this(qrId, workspaceId, linkId, destination, linkDisabled, workspaceSuspended, LinkRules.NONE, null);
    }

    public boolean unavailable() {
      return linkDisabled || workspaceSuspended;
    }
  }
}
