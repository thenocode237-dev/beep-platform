package io.beep.bo.app.plans;

import io.beep.bo.app.auth.StepUp;
import io.beep.bo.app.port.AuditPort;
import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.app.port.PlanDraftRepository;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.audit.AuditEvent;
import io.beep.bo.domain.staff.Permission;
import io.beep.bo.domain.staff.StaffUser;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Édition et versionnement des plans sans déploiement (specs §6.4 règle 7) : le brouillon vit dans la base `backoffice` ;
 * la publication (droit dédié, ré-authentification TOTP) l'envoie au core, qui incrémente la version.
 * Un brouillon commencé sur une version déjà dépassée ne peut pas être publié (publication concurrente détectée).
 */
public class PlanDraftsUseCase {
  private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_-]{1,31}");

  private final PlanDraftRepository drafts;
  private final CoreGatewayPort core;
  private final AuditPort audit;
  private final StepUp stepUp;
  private final Clock clock;

  public PlanDraftsUseCase(PlanDraftRepository drafts, CoreGatewayPort core, AuditPort audit, StepUp stepUp, Clock clock) {
    this.drafts = drafts;
    this.core = core;
    this.audit = audit;
    this.stepUp = stepUp;
    this.clock = clock;
  }

  public List<PlanDraftRepository.Draft> list(StaffUser actor) {
    actor.require(Permission.PLANS_READ);
    return drafts.list();
  }

  public PlanDraftRepository.Draft save(StaffUser actor, String code, String name, long priceXaf, Long quarterly, Long annual, boolean isPublic,
      Map<String, Long> entitlements, int baseVersion, String note, String ip) {
    actor.require(Permission.PLANS_EDIT);
    if (code == null || !CODE.matcher(code).matches() || name == null || name.isBlank() || name.strip().length() > 60 || priceXaf < 0 || priceXaf > 10_000_000L
        || (quarterly != null && quarterly < 0) || (annual != null && annual < 0) || entitlements == null || baseVersion < 0) {
      throw new DomainException("invalid_request");
    }
    for (var e : entitlements.entrySet()) if (e.getValue() == null || e.getKey() == null || e.getKey().isBlank()) throw new DomainException("invalid_request");
    PlanDraftRepository.Draft draft = new PlanDraftRepository.Draft(code, name.strip(), priceXaf, quarterly, annual, isPublic, new TreeMap<>(entitlements), baseVersion,
        note == null ? null : note.strip(), actor.email().value(), clock.instant(), drafts.find(code).map(PlanDraftRepository.Draft::publishedVersion).orElse(null),
        drafts.find(code).map(PlanDraftRepository.Draft::publishedAt).orElse(null));
    drafts.save(draft);
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "plans.draft_save", "plan:" + code, "prix=" + priceXaf + " public=" + isPublic, ip));
    return draft;
  }

  public void discard(StaffUser actor, String code, String ip) {
    actor.require(Permission.PLANS_EDIT);
    drafts.delete(code);
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "plans.draft_discard", "plan:" + code, null, ip));
  }

  /** Publie le brouillon vers le core. Journalise l'avant/après (version et prix). */
  public CoreGatewayPort.PlanView publish(StaffUser actor, String code, String totp, String ip) {
    actor.require(Permission.PLANS_PUBLISH);
    PlanDraftRepository.Draft d = drafts.find(code).orElseThrow(() -> new DomainException("not_found"));
    CoreGatewayPort.PlanView current = core.plans(actor.id()).stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
    int liveVersion = current == null ? 0 : current.version();
    if (liveVersion != d.baseVersion()) throw new DomainException("draft_outdated");
    stepUp.require(actor, totp);
    CoreGatewayPort.PlanView published = core.publishPlan(code, d.name(), d.priceXaf(), d.priceQuarterlyXaf(), d.priceAnnualXaf(), d.isPublic(), d.entitlements(), actor.id());
    drafts.markPublished(code, published.version(), clock.instant());
    audit.append(new AuditEvent(clock.instant(), actor.id(), actor.email().value(), "plans.publish", "plan:" + code,
        "version=" + liveVersion + "->" + published.version() + " prix=" + (current == null ? "-" : current.priceXaf()) + "->" + published.priceXaf() + " public="
            + (current == null ? "-" : current.isPublic()) + "->" + published.isPublic(), ip));
    return published;
  }
}
