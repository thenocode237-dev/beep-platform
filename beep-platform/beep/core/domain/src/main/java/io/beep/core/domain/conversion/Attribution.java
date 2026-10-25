package io.beep.core.domain.conversion;

import io.beep.core.domain.DomainException;
import java.time.*;
import java.util.*;

/** Pure attribution policy. Timestamp, then click id break ties deterministically. */
public final class Attribution {
  private Attribution() {}
  public record Touch(String cid, UUID linkId, Instant at) {}
  public static Optional<Touch> select(List<Touch> touches, String model, int days, Instant eventAt) {
    if (!Set.of("first", "last").contains(model) || days < 1 || days > 90) throw new DomainException("invalid_request");
    var order = Comparator.comparing(Touch::at).thenComparing(Touch::cid);
    var stream = touches.stream().filter(t -> !t.at().isAfter(eventAt) && !t.at().isBefore(eventAt.minus(Duration.ofDays(days)))) ;
    return model.equals("first") ? stream.min(order) : stream.max(order);
  }
}
