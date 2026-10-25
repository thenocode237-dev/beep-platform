package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.instant;
import static io.beep.core.adapter.out.postgres.Schema.odt;

import io.beep.core.app.port.CouponRepository;
import io.beep.core.domain.billing.Coupon;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;

public class PostgresCouponAdapter implements CouponRepository {
  private static final String COLUMNS = "c.code, c.percent_off, c.amount_off_xaf, c.max_redemptions, c.redeemed, c.valid_until, c.plan_codes, c.active, c.created_at";

  private final DSLContext dsl;

  public PostgresCouponAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Coupon> find(String code) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM coupons c WHERE c.code = ?", code).map(PostgresCouponAdapter::map);
  }

  @Override
  public Optional<Coupon> lock(String code) {
    return dsl.fetchOptional("SELECT " + COLUMNS + " FROM coupons c WHERE c.code = ? FOR UPDATE", code).map(PostgresCouponAdapter::map);
  }

  @Override
  public void insert(Coupon c) {
    try {
      dsl.execute("INSERT INTO coupons (code, percent_off, amount_off_xaf, max_redemptions, redeemed, valid_until, plan_codes, active, created_at) "
              + "VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::text[], ?, ?::timestamptz)",
          c.code(), c.percentOff(), c.amountOffXaf(), c.maxRedemptions(), c.redeemed(), odt(c.validUntil()), c.planCodes().toArray(new String[0]), c.active(), odt(c.createdAt()));
    } catch (org.jooq.exception.DataAccessException e) {
      if (Sql.hasState(e, Sql.UNIQUE_VIOLATION)) throw new DuplicateCodeException(e);
      throw e;
    }
  }

  @Override
  public void update(Coupon c) {
    dsl.execute("UPDATE coupons SET redeemed = ?, active = ? WHERE code = ?", c.redeemed(), c.active(), c.code());
  }

  @Override
  public List<Coupon> list(int limit) {
    return dsl.fetch("SELECT " + COLUMNS + " FROM coupons c ORDER BY c.created_at DESC, c.code LIMIT ?", limit).map(PostgresCouponAdapter::map);
  }

  private static Coupon map(Record r) {
    String[] plans = r.get("plan_codes", String[].class);
    return new Coupon(r.get("code", String.class), r.get("percent_off", Integer.class), r.get("amount_off_xaf", Long.class), r.get("max_redemptions", Integer.class),
        r.get("redeemed", Integer.class), instant(r.get("valid_until", OffsetDateTime.class)), plans == null ? Set.of() : Set.copyOf(Arrays.asList(plans)),
        r.get("active", Boolean.class), instant(r.get("created_at", OffsetDateTime.class)));
  }
}
