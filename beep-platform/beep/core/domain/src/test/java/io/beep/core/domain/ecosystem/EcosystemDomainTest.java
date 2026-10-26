package io.beep.core.domain.ecosystem;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.domain.team.WorkspaceRole;
import io.beep.core.domain.bio.BioBlock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EcosystemDomainTest {
  @Test void ownerCannotBeDemotedAndAdminsCannotPromoteTheirPeers(){assertThatThrownBy(()->WorkspaceRole.OWNER.requireManage(WorkspaceRole.OWNER)).hasMessage("forbidden");assertThatThrownBy(()->WorkspaceRole.ADMIN.requireManage(WorkspaceRole.ADMIN)).hasMessage("forbidden");WorkspaceRole.ADMIN.requireManage(WorkspaceRole.EDITOR);assertThat(WorkspaceRole.VIEWER.writes()).isFalse();assertThat(WorkspaceRole.EDITOR.manages()).isFalse();}
  @Test void csvPreservesQuotedCommasQuotesAndNewlines(){var rows=LinkCsv.parse("destination,slug,title\r\n\"https://example.org/?a=1,b=2\",demo,\"Title \"\"quoted\"\"\nnext\"\r\n");assertThat(rows.get(1)).containsExactly("https://example.org/?a=1,b=2","demo","Title \"quoted\"\nnext");}
  @Test void malformedOrOversizedCsvFailsBeforeAnyImport(){for(String csv:List.of("destination,slug\n\"unclosed,x","destination,slug\na","destination,destination\na,b","destination\n"+"x\n".repeat(501)))assertThatThrownBy(()->LinkCsv.parse(csv)).hasMessage("invalid_csv");assertThat(LinkCsv.cell("  =HYPERLINK(\"x\")")).startsWith("\"'");}
  @Test void stripeSignatureIsBoundToBodyAndFreshTimestamp(){Instant now=Instant.parse("2026-10-25T12:00:00Z");byte[] body="{\"id\":1}".getBytes(StandardCharsets.UTF_8);String t=""+now.getEpochSecond(),sig="t="+t+",v1="+HexFormat.of().formatHex(IntegrationSignatures.hmac("secret",(t+"."+new String(body,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));assertThat(IntegrationSignatures.verify("stripe","secret",body,sig,now)).isTrue();assertThat(IntegrationSignatures.verify("stripe","secret","{}".getBytes(StandardCharsets.UTF_8),sig,now)).isFalse();assertThat(IntegrationSignatures.verify("stripe","secret",body,sig,now.plusSeconds(301))).isFalse();}
  @Test void commerceHmacRejectsWrongSecretsAndMalformedSignatures(){byte[] body="hello".getBytes(StandardCharsets.UTF_8);String sig=Base64.getEncoder().encodeToString(IntegrationSignatures.hmac("shared",body));for(String p:List.of("shopify","woocommerce")){assertThat(IntegrationSignatures.verify(p,"shared",body,sig,Instant.now())).isTrue();assertThat(IntegrationSignatures.verify(p,"different",body,sig,Instant.now())).isFalse();assertThat(IntegrationSignatures.verify(p,"shared",body,"invalid!",Instant.now())).isFalse();}}
  @Test void radarNeedsMeaningfulTrafficBeforeConversionWarning(){assertThat(RadarPolicy.fires("conversion_drop",5,19,0,0,0)).isFalse();assertThat(RadarPolicy.fires("conversion_drop",5,100,0,4,0)).isTrue();assertThat(RadarPolicy.fires("conversion_drop",5,100,0,5,0)).isFalse();assertThat(RadarPolicy.fires("click_spike",100,100,0,0,0)).isTrue();}
  @Test void advancedBioRequiresHttpsAndExplicitConsentCopy(){assertThatThrownBy(()->BioBlock.of("form001",BioBlock.Type.FORM,Map.of("label","Contact","privacyUrl","https://example.org/privacy"))).hasMessage("invalid_block");assertThatThrownBy(()->BioBlock.of("video01",BioBlock.Type.VIDEO,Map.of("label","Demo","url","javascript:alert(1)"))).isInstanceOf(RuntimeException.class);assertThat(BioBlock.of("form001",BioBlock.Type.EMAIL,Map.of("label","Newsletter","privacyUrl","https://example.org/privacy","consentText","Send me updates")).config()).containsKey("consentText");}
}
