package io.beep.bo.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beep.bo.app.port.PlanDraftRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresPlanDraftAdapter implements PlanDraftRepository {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String COLUMNS = "code, name, price_xaf, price_quarterly_xaf, price_annual_xaf, is_public, entitlements::text AS entitlements, base_version, note, "
      + "updated_by, updated_at, published_version, published_at";

  private final DSLContext dsl;

  public PostgresPlanDraftAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<Draft> list() {
    return dsl.fetch("SELECT " + COLUMNS + " FROM plan_drafts ORDER BY price_xaf, code").map(PostgresPlanDraftAdapter::map);
  }

  @Override
  public Optional<Draft> find(String code) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM plan_drafts WHERE code = ?", code).map(PostgresPlanDraftAdapter::map);
  }

  @Override
  public void save(Draft d) {
    dsl.execute("INSERT INTO plan_drafts (code, name, price_xaf, price_quarterly_xaf, price_annual_xaf, is_public, entitlements, base_version, note, updated_by, updated_at, "
            + "published_version, published_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::timestamptz, ?, ?::timestamptz) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, price_xaf = EXCLUDED.price_xaf, price_quarterly_xaf = EXCLUDED.price_quarterly_xaf, "
            + "price_annual_xaf = EXCLUDED.price_annual_xaf, is_public = EXCLUDED.is_public, entitlements = EXCLUDED.entitlements, base_version = EXCLUDED.base_version, "
            + "note = EXCLUDED.note, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at",
        d.code(), d.name(), d.priceXaf(), d.priceQuarterlyXaf(), d.priceAnnualXaf(), d.isPublic(), json(d.entitlements()), d.baseVersion(), d.note(), d.updatedBy(), odt(d.updatedAt()),
        d.publishedVersion(), odt(d.publishedAt()));
  }

  @Override
  public void delete(String code) {
    dsl.execute("DELETE FROM plan_drafts WHERE code = ?", code);
  }

  @Override
  public void markPublished(String code, int version, Instant at) {
    dsl.execute("UPDATE plan_drafts SET published_version = ?, published_at = ?::timestamptz, base_version = ? WHERE code = ?", version, odt(at), version, code);
  }

  private static Draft map(Record r) {
    return new Draft(r.get("code", String.class), r.get("name", String.class), r.get("price_xaf", Long.class), r.get("price_quarterly_xaf", Long.class),
        r.get("price_annual_xaf", Long.class), r.get("is_public", Boolean.class), entitlements(r.get("entitlements", String.class)), r.get("base_version", Integer.class),
        r.get("note", String.class), r.get("updated_by", String.class), instant(r.get("updated_at", OffsetDateTime.class)), r.get("published_version", Integer.class),
        instant(r.get("published_at", OffsetDateTime.class)));
  }

  private static String json(Map<String, Long> m) {
    try {
      return JSON.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Long> entitlements(String raw) {
    try {
      Map<String, Object> m = JSON.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
      Map<String, Long> out = new LinkedHashMap<>();
      m.forEach((k, v) -> { if (v instanceof Number n) out.put(k, n.longValue()); });
      return out;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("droits illisibles", e);
    }
  }

  private static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }

  private static Instant instant(OffsetDateTime o) {
    return o == null ? null : o.toInstant();
  }
}
