package io.beep.core.app.conversion;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ClickIdsTest {
  final ClickIds ids=new ClickIds("test-secret-with-at-least-thirty-two-characters");
  final UUID ws=UUID.randomUUID(),link=UUID.randomUUID();final Instant now=Instant.parse("2026-10-25T12:00:00Z");
  @Test void validReceiptsAreBoundToWorkspaceAndLink(){String cid=ids.issue(ws,link,now);assertThat(ids.read(cid,ws,now)).contains(new ClickIds.Click(ws,link,now));assertThat(ids.read(cid,UUID.randomUUID(),now)).isEmpty();}
  @Test void alteredExpiredFutureOrMalformedReceiptsAreRejected(){String cid=ids.issue(ws,link,now);assertThat(ids.read(cid+"x",ws,now)).isEmpty();assertThat(ids.read(cid,ws,now.plus(Duration.ofDays(91)))).isEmpty();assertThat(ids.read(cid,ws,now.minusSeconds(1))).isEmpty();assertThat(ids.read("invalid",ws,now)).isEmpty();assertThat(ids.read(null,ws,now)).isEmpty();}
  @Test void eachClickIsUniqueEvenAtSameInstant(){assertThat(ids.issue(ws,link,now)).isNotEqualTo(ids.issue(ws,link,now));}
}
