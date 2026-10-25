package io.beep.core.domain.conversion;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class AttributionTest {
  static final Instant NOW=Instant.parse("2026-10-25T12:00:00Z");
  static Attribution.Touch touch(String id,int days){return new Attribution.Touch(id,UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),NOW.minus(Duration.ofDays(days)));}
  @Test void goldenFirstAndLastClicksIgnoreOutOfWindowAndFutureClicks(){var touches=List.of(touch("expired",31),touch("first",30),touch("middle",10),touch("last",0),touch("future",-1));assertThat(Attribution.select(touches,"first",30,NOW)).contains(touch("first",30));assertThat(Attribution.select(touches,"last",30,NOW)).contains(touch("last",0));}
  @Test void windowIsInclusiveAtExactBoundary(){assertThat(Attribution.select(List.of(touch("boundary",1)),"last",1,NOW)).isPresent();assertThat(Attribution.select(List.of(new Attribution.Touch("outside",UUID.randomUUID(),NOW.minus(Duration.ofDays(1)).minusNanos(1))),"last",1,NOW)).isEmpty();}
  @Test void tiesHaveAStableOrderIndependentOfInput(){var a=touch("a",0);var z=touch("z",0);assertThat(Attribution.select(List.of(z,a),"first",90,NOW)).contains(a);assertThat(Attribution.select(List.of(a,z),"last",90,NOW)).contains(z);}
  @Test void invalidPolicyAndEmptyJourney(){assertThatThrownBy(()->Attribution.select(List.of(),"linear",30,NOW)).hasMessage("invalid_request");assertThatThrownBy(()->Attribution.select(List.of(),"last",91,NOW)).hasMessage("invalid_request");assertThat(Attribution.select(List.of(),"last",1,NOW)).isEmpty();}
}
