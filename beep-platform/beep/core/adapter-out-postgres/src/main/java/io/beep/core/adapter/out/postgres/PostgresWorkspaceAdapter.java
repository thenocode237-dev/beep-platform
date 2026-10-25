package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.domain.workspace.Workspace;
import io.beep.core.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresWorkspaceAdapter implements WorkspaceRepository {
  private final DSLContext dsl;

  public PostgresWorkspaceAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Workspace> findById(UUID id) {
    return dsl.selectFrom(WORKSPACES).where(W_ID.eq(id)).fetchOptional(PostgresWorkspaceAdapter::map);
  }

  @Override
  public Optional<Workspace> findByOwner(UUID ownerId) {
    return dsl.selectFrom(WORKSPACES).where(W_OWNER.eq(ownerId)).fetchOptional(PostgresWorkspaceAdapter::map);
  }

  /** ON CONFLICT DO NOTHING : deux premières requêtes simultanées d'un même utilisateur ne créent qu'un workspace. */
  @Override
  public Workspace ensure(Workspace w) {
    dsl.execute("INSERT INTO workspaces (id, owner_id, name, plan_code, status, created_at) VALUES (?, ?, ?, ?, ?, ?::timestamptz) ON CONFLICT (owner_id) DO NOTHING",
        w.id(), w.ownerId(), w.name(), w.planCode(), w.status().db(), odt(w.createdAt()));
    return findByOwner(w.ownerId()).orElseThrow();
  }

  @Override
  public void lock(UUID id) {
    dsl.execute("SELECT 1 FROM workspaces WHERE id = ? FOR UPDATE", id);
  }

  @Override
  public void updateStatus(UUID id, WorkspaceStatus status) {
    dsl.update(WORKSPACES).set(W_STATUS, status.db()).where(W_ID.eq(id)).execute();
  }

  @Override
  public void updatePlan(UUID id, String planCode) {
    dsl.update(WORKSPACES).set(W_PLAN, planCode).where(W_ID.eq(id)).execute();
  }

  @Override
  public void markFirstLink(UUID id, Instant at) {
    dsl.update(WORKSPACES).set(W_FIRST_LINK, odt(at)).where(W_ID.eq(id).and(W_FIRST_LINK.isNull())).execute();
  }

  static Workspace map(Record r) {
    return new Workspace(r.get(W_ID), r.get(W_OWNER), r.get(W_NAME), r.get(W_PLAN), WorkspaceStatus.fromDb(r.get(W_STATUS)), instant(r.get(W_FIRST_LINK)),
        instant(r.get(W_CREATED)));
  }
}
