package io.beep.core.app.billing;

import io.beep.core.domain.billing.BillingCycle;
import java.time.Instant;

/** Prix détaillé d'une période : prix de liste − remise − crédit du temps restant (upgrade immédiat) = montant TTC dû. */
public record Quote(String planCode, BillingCycle cycle, long listPriceXaf, long discountXaf, long creditXaf, long amountXaf, String couponCode, Instant newPeriodEnd) {}
