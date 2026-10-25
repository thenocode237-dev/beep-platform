package io.beep.core.adapter.out.postgres;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ConversionQueryTest {
 @Test void duplicateIncomingReceiptsCannotOverrideTheSignedReceipt(){assertThat(AsyncConversionTrackerAdapter.withCid("https://shop.example/?beep_cid=fake&beep_cid=other&utm_source=test#details","signed")).isEqualTo("https://shop.example/?utm_source=test&beep_cid=signed#details");}
 @Test void encodedAliasesAreRemovedWithoutChangingOtherValues(){assertThat(AsyncConversionTrackerAdapter.withCid("https://shop.example/?beep%5Fcid=fake&utm_campaign=x%26y&%62eep_cid=other","signed")).isEqualTo("https://shop.example/?utm_campaign=x%26y&beep_cid=signed");}
 @Test void fragmentsAndBareDestinationsArePreserved(){assertThat(AsyncConversionTrackerAdapter.withCid("https://shop.example/page#section","signed")).isEqualTo("https://shop.example/page?beep_cid=signed#section");}
}
