package io.beep.core.app.port;

import io.beep.core.domain.billing.Coupon;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {
  Optional<Coupon> find(String code);

  /** Verrouille le coupon jusqu'à la fin de la transaction (plafond d'utilisations exact). */
  Optional<Coupon> lock(String code);

  /** Lève {@link DuplicateCodeException} si le code existe. */
  void insert(Coupon coupon);

  void update(Coupon coupon);

  List<Coupon> list(int limit);

  class DuplicateCodeException extends RuntimeException {
    public DuplicateCodeException(Throwable cause) {
      super(cause);
    }
  }
}
