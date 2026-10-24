package io.beep.core.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.plan.QuotaPolicy.Level;
import io.beep.core.domain.plan.QuotaPolicy.Period;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.plan.QuotaPolicy.Usage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuotaPolicyTest {
  static final Plan STARTER = new Plan("starter", "Starter", 1, 0, true, Map.of(
      "links.stock_max", 5L, "qr.stock_max", 5L, "bio.max", 1L, "analytics.retention_days", 30L, "features.analytics_advanced", 0L));
  static final Plan ELITE = new Plan("elite", "Elite", 1, 14999, false, Map.of(
      "links.monthly_creations", 500L, "qr.monthly_creations", 500L, "bio.max", 10L, "analytics.retention_days", -1L));

  @Test
  void starterIsAStockQuotaWithWarningAtEightyPercentAndBlockAtHundred() {
    Entitlements e = Entitlements.of(STARTER, Map.of());
    assertThat(QuotaPolicy.status(e, Resource.LINKS, new Usage(3, 99)).level()).isEqualTo(Level.OK);
    assertThat(QuotaPolicy.status(e, Resource.LINKS, new Usage(4, 0)).level()).isEqualTo(Level.WARNING);
    var full = QuotaPolicy.status(e, Resource.LINKS, new Usage(5, 0));
    assertThat(full.period()).isEqualTo(Period.STOCK);
    assertThat(full.level()).isEqualTo(Level.REACHED);
    assertThat(full.canCreate()).isFalse();
    assertThatThrownBy(() -> QuotaPolicy.requireCreatable(e, Resource.LINKS, new Usage(5, 0))).hasMessage("quota_exceeded");
    assertThatThrownBy(() -> QuotaPolicy.requireCreatable(e, Resource.BIO, new Usage(1, 0))).hasMessage("quota_exceeded");
  }

  @Test
  void eliteCountsMonthlyCreationsNotStock() {
    Entitlements e = Entitlements.of(ELITE, Map.of());
    var s = QuotaPolicy.status(e, Resource.QR, new Usage(10_000, 12));
    assertThat(s.period()).isEqualTo(Period.MONTHLY);
    assertThat(s.canCreate()).isTrue();
    assertThat(QuotaPolicy.status(e, Resource.QR, new Usage(0, 500)).canCreate()).isFalse();
  }

  @Test
  void overridesReplacePlanValuesKeyByKeyAndMinusOneIsUnlimited() {
    Entitlements e = Entitlements.of(STARTER, Map.of("links.stock_max", 55L, "qr.stock_max", -1L));
    assertThat(QuotaPolicy.status(e, Resource.LINKS, new Usage(54, 0)).canCreate()).isTrue();
    var qr = QuotaPolicy.status(e, Resource.QR, new Usage(1_000, 0));
    assertThat(qr.unlimited()).isTrue();
    assertThat(qr.level()).isEqualTo(Level.OK);
    assertThat(e.retentionDays()).isEqualTo(30);
    assertThat(e.enabled("features.analytics_advanced")).isFalse();
  }

  @Test
  void overrideValidationRejectsUnknownKeysAndIncoherentValues() {
    assertThatThrownBy(() -> Entitlements.validateOverride("links.stok_max", 5L)).hasMessage("unknown_entitlement");
    assertThatThrownBy(() -> Entitlements.validateOverride("features.analytics_advanced", 2L)).hasMessage("invalid_entitlement");
    assertThatThrownBy(() -> Entitlements.validateOverride("links.stock_max", -2L)).hasMessage("invalid_entitlement");
    assertThatCode(() -> Entitlements.validateOverride("links.stock_max", null)).doesNotThrowAnyException();
    assertThatCode(() -> Entitlements.validateOverride("bio.max", -1L)).doesNotThrowAnyException();
  }
}
