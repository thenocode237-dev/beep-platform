package io.beep.core.app.port;

import io.beep.core.domain.bio.BioPage;
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

  record PublishedBio(BioPage page, boolean workspaceSuspended) {}

  record Resolution(Kind kind, UUID targetId, UUID workspaceId, String destination, boolean disabled, boolean workspaceSuspended) {
    public static final Resolution NONE = new Resolution(Kind.NONE, null, null, null, false, false);

    public enum Kind {
      LINK,
      BIO,
      NONE
    }

    public boolean unavailable() {
      return disabled || workspaceSuspended;
    }
  }

  record QrTarget(UUID qrId, UUID workspaceId, UUID linkId, String destination, boolean linkDisabled, boolean workspaceSuspended) {
    public boolean unavailable() {
      return linkDisabled || workspaceSuspended;
    }
  }
}
