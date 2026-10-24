package io.beep.core.app.link;

import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.user.User;
import java.util.UUID;

/**
 * Suppression : libère une place de quota (stock) et le slug. Refusée tant qu'un QR dynamique suit le lien
 * (un QR déjà imprimé ne doit pas devenir une impasse). Les statistiques passées suivent la rétention.
 */
public class DeleteLinkUseCase {
  private final Workspaces workspaces;
  private final LinkRepository links;
  private final QrCodeRepository qrCodes;
  private final TransactionPort tx;

  public DeleteLinkUseCase(Workspaces workspaces, LinkRepository links, QrCodeRepository qrCodes, TransactionPort tx) {
    this.workspaces = workspaces;
    this.links = links;
    this.qrCodes = qrCodes;
    this.tx = tx;
  }

  public void execute(User actor, UUID id) {
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    Link link = OwnedLinks.require(links, ctx, id);
    tx.run(() -> {
      if (qrCodes.countByLink(link.id()) > 0) throw new DomainException("link_in_use");
      links.delete(link.id());
    });
  }
}
