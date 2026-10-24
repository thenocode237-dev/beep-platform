package io.beep.core.app.port;

import io.beep.core.domain.qr.QrCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QrCodeRepository {
  void insert(QrCode qr);

  void update(QrCode qr);

  void delete(UUID id);

  Optional<QrCode> findById(UUID id);

  Page page(UUID workspaceId, int limit, String cursor);

  long countDynamic(UUID workspaceId);

  long countDynamicCreatedSince(UUID workspaceId, Instant since);

  /** QR codes dynamiques qui suivent ce lien (un lien imprimé ne doit pas disparaître sous un QR). */
  long countByLink(UUID linkId);

  record Page(List<QrCode> items, String nextCursor) {}
}
