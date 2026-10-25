package io.beep.core.app.billing;

import io.beep.core.app.port.PaymentGatewayPort;
import io.beep.core.app.port.PaymentRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.billing.Payment;
import io.beep.core.domain.billing.PaymentStatus;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Règle un paiement en attente. Trois déclencheurs, une seule logique idempotente : la notification de l'agrégateur (webhook),
 * l'interrogation du client après sa confirmation sur le téléphone, et la tâche de rapprochement du worker.
 * Le fournisseur fait foi : on l'interroge toujours, le contenu d'une notification n'est jamais cru sur parole.
 */
public class SettlePaymentUseCase {
  private final PaymentRepository payments;
  private final PaymentGatewayPort gateway;
  private final BillingEngine engine;
  private final Workspaces workspaces;
  private final TransactionPort tx;
  private final Clock clock;

  public SettlePaymentUseCase(PaymentRepository payments, PaymentGatewayPort gateway, BillingEngine engine, Workspaces workspaces, TransactionPort tx, Clock clock) {
    this.payments = payments;
    this.gateway = gateway;
    this.engine = engine;
    this.workspaces = workspaces;
    this.tx = tx;
    this.clock = clock;
  }

  /** État d'un paiement du client connecté ; un paiement en attente est vérifié auprès du fournisseur. */
  public Payment status(User actor, UUID paymentId) {
    var w = workspaces.ensureFor(actor);
    Payment p = payments.findById(paymentId).filter(x -> x.workspaceId().equals(w.id())).orElseThrow(() -> new DomainException("not_found"));
    return settle(p.id());
  }

  /** Notification de l'agrégateur : signature obligatoire, puis vérification auprès du fournisseur. Référence inconnue = ignorée. */
  public void onNotification(String rawBody, String signature) {
    if (signature == null || !gateway.validSignature(rawBody, signature)) throw new DomainException("unauthorized");
    String ref = gateway.referenceOf(rawBody);
    if (ref == null) return;
    payments.findByProviderRef(gateway.name(), ref).ifPresent(p -> settle(p.id()));
  }

  public Payment settle(UUID paymentId) {
    Payment p = payments.findById(paymentId).orElseThrow(() -> new DomainException("not_found"));
    if (p.status() != PaymentStatus.PENDING || !p.provider().equals(gateway.name())) return p;
    PaymentGatewayPort.Verification v;
    try {
      v = gateway.verify(p.providerRef());
    } catch (PaymentGatewayPort.GatewayException e) {
      return p; // fournisseur injoignable : reste en attente, la tâche de rapprochement reviendra
    }
    Instant now = clock.instant();
    BillingEngine.Applied[] done = new BillingEngine.Applied[1];
    Payment result = tx.run(() -> {
      Payment locked = payments.lockById(paymentId).orElseThrow();
      if (locked.status() != PaymentStatus.PENDING) return locked; // réglé entre-temps par un autre déclencheur
      switch (v.state()) {
        case SUCCEEDED -> {
          done[0] = engine.apply(locked, now);
          return done[0].payment();
        }
        case FAILED -> {
          Payment failed = locked.failed(v.failureReason() == null ? "declined" : v.failureReason());
          payments.update(failed);
          return failed;
        }
        default -> {
          if (locked.createdAt().plus(engine.settings().pendingTimeout()).isBefore(now)) {
            Payment expired = locked.failed("timeout");
            payments.update(expired);
            return expired;
          }
          return locked;
        }
      }
    });
    engine.sendReceipt(done[0]);
    return result;
  }
}
