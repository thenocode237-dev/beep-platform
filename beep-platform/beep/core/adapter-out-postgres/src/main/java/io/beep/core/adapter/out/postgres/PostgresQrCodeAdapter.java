package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.domain.qr.QrCode;
import io.beep.core.domain.qr.QrContent;
import io.beep.core.domain.qr.QrStyle;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

public class PostgresQrCodeAdapter implements QrCodeRepository {
  private final DSLContext dsl;

  public PostgresQrCodeAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(QrCode q) {
    dsl.insertInto(QR)
        .set(Q_ID, q.id())
        .set(Q_WORKSPACE, q.workspaceId())
        .set(Q_KIND, q.kind().db())
        .set(Q_CODE, q.code())
        .set(Q_LINK, q.linkId())
        .set(Q_CONTENT_TYPE, q.content() == null ? null : q.content().type().db())
        .set(Q_CONTENT, q.content() == null ? null : Sql.json(q.content().fields()))
        .set(Q_STYLE, style(q.style()))
        .set(Q_TITLE, q.title())
        .set(Q_CREATED, odt(q.createdAt()))
        .set(Q_UPDATED, odt(q.updatedAt()))
        .execute();
  }

  @Override
  public void update(QrCode q) {
    dsl.update(QR)
        .set(Q_LINK, q.linkId())
        .set(Q_CONTENT_TYPE, q.content() == null ? null : q.content().type().db())
        .set(Q_CONTENT, q.content() == null ? null : Sql.json(q.content().fields()))
        .set(Q_STYLE, style(q.style()))
        .set(Q_TITLE, q.title())
        .set(Q_UPDATED, odt(q.updatedAt()))
        .where(Q_ID.eq(q.id()))
        .execute();
  }

  @Override
  public void delete(UUID id) {
    dsl.deleteFrom(QR).where(Q_ID.eq(id)).execute();
  }

  @Override
  public Optional<QrCode> findById(UUID id) {
    return dsl.selectFrom(QR).where(Q_ID.eq(id)).fetchOptional(PostgresQrCodeAdapter::map);
  }

  @Override
  public Page page(UUID workspaceId, int limit, String cursor) {
    Condition where = Q_WORKSPACE.eq(workspaceId);
    if (cursor != null && !cursor.isBlank()) {
      Sql.Cursor c = Sql.cursor(cursor);
      where = where.and(DSL.row(Q_CREATED, Q_ID).lt(DSL.row(odt(c.at()), c.id())));
    }
    List<QrCode> rows = dsl.selectFrom(QR).where(where).orderBy(Q_CREATED.desc(), Q_ID.desc()).limit(limit + 1).fetch(PostgresQrCodeAdapter::map);
    if (rows.size() <= limit) return new Page(rows, null);
    List<QrCode> items = rows.subList(0, limit);
    QrCode last = items.get(limit - 1);
    return new Page(items, Sql.cursor(last.createdAt(), last.id()));
  }

  @Override
  public long countDynamic(UUID workspaceId) {
    return dsl.fetchCount(QR, Q_WORKSPACE.eq(workspaceId).and(Q_KIND.eq("dynamic")));
  }

  @Override
  public long countDynamicCreatedSince(UUID workspaceId, Instant since) {
    return dsl.fetchSingle("SELECT count(*) FROM resource_creations WHERE workspace_id=? AND resource='qr' AND at>=?::timestamptz",workspaceId,odt(since)).get(0,Long.class);
  }

  @Override
  public long countByLink(UUID linkId) {
    return dsl.fetchCount(QR, Q_LINK.eq(linkId));
  }

  private static org.jooq.JSONB style(QrStyle s) {
    Map<String,Object> map=new java.util.LinkedHashMap<>(Map.of("fg",s.foreground(),"bg",s.background(),"ecc",String.valueOf(s.errorCorrection()),"margin",s.margin()));
    if(s.advanced()!=null) {var a=s.advanced();Map<String,Object> adv=new java.util.LinkedHashMap<>();adv.put("shape",a.shape());adv.put("frame",a.frame());if(a.gradient()!=null)adv.put("gradient",a.gradient());if(a.logo()!=null)adv.put("logo",a.logo());map.put("advanced",adv);}
    return Sql.json(map);
  }

  static QrCode map(Record r) {
    Map<String, Object> s = Sql.map(r.get(Q_STYLE));
    QrStyle style = new QrStyle((String) s.get("fg"), (String) s.get("bg"), ((String) s.get("ecc")).charAt(0), ((Number) s.get("margin")).intValue());
    if(s.get("advanced") instanceof Map<?,?> a) style=style.decorated(new QrStyle.Decoration((String)a.get("shape"),(String)a.get("gradient"),(String)a.get("logo"),(String)a.get("frame")));
    String type = r.get(Q_CONTENT_TYPE);
    QrContent content = type == null ? null : new QrContent(QrContent.Type.fromDb(type), Sql.strings(r.get(Q_CONTENT)));
    return new QrCode(r.get(Q_ID), r.get(Q_WORKSPACE), QrCode.Kind.fromDb(r.get(Q_KIND)), r.get(Q_CODE), r.get(Q_LINK), content, style, r.get(Q_TITLE),
        instant(r.get(Q_CREATED)), instant(r.get(Q_UPDATED)));
  }
}
