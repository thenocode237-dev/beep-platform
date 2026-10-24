package io.beep.core.app.port;

import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BioPageRepository {
  /** Lève {@link SlugRegistryPort.SlugTakenException} si le slug est pris. */
  void insert(BioPage page);

  /** Champs de la page (slug inclus) ; les blocs passent par {@link #replaceBlocks}. */
  void update(BioPage page);

  void replaceBlocks(UUID pageId, List<BioBlock> blocks, Instant at);

  void delete(UUID id);

  Optional<BioPage> findById(UUID id);

  List<BioPage> listByWorkspace(UUID workspaceId);

  long count(UUID workspaceId);
}
