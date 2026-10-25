package io.beep.core.adapter.out.security;
import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
class WebhookSecurityTest {
 @Test void parsesStandardCrlfStatusLines()throws Exception{assertThat(PinnedWebhookTransportAdapter.statusLine("HTTP/1.1 204 No Content\r")).isEqualTo(204);assertThat(PinnedWebhookTransportAdapter.statusLine("HTTP/1.0 503 Service Unavailable\r")).isEqualTo(503);assertThatThrownBy(()->PinnedWebhookTransportAdapter.statusLine("invalid")).isInstanceOf(java.io.IOException.class);}
 @Test void privateAndSpecialNetworksAreDenied()throws Exception{for(String ip:new String[]{"127.0.0.1","0.0.0.0","10.0.0.1","172.16.0.1","192.168.0.1","169.254.169.254","100.64.0.1","198.18.0.1","::1","fc00::1","fe80::1","2001:db8::1","::ffff:127.0.0.1"})assertThat(PinnedWebhookTransportAdapter.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();}
 @Test void publicAddressesAreAccepted()throws Exception{for(String ip:new String[]{"8.8.8.8","1.1.1.1","2606:4700:4700::1111"})assertThat(PinnedWebhookTransportAdapter.publicAddress(InetAddress.getByName(ip))).isTrue();}
 static final String PAYLOAD="{\"id\":\"event-1\",\"data\":{\"name\":\"sale\",\"visitor\":\"pseudonymous\",\"at\":\"2026-10-25T12:00:00Z\",\"value\":49,\"currency\":\"EUR\"}}";
 @Test void postbackPayloadsUseProviderFormatsAndDoNotPutTokensInBody()throws Exception{var meta=PinnedWebhookTransportAdapter.postbackPayload("meta","123456\nsecret-token\nv99.0",PAYLOAD);assertThat(meta.url()).isEqualTo("https://graph.facebook.com/v99.0/123456/events?access_token=secret-token");assertThat(meta.body()).contains("Purchase","external_id","event_time").doesNotContain("secret-token");var google=PinnedWebhookTransportAdapter.postbackPayload("google","G-1234\nsecret-token\n",PAYLOAD);assertThat(google.body()).contains("purchase","client_id","transaction_id").doesNotContain("secret-token");}
 @Test void postbacksRejectEventsWithoutConsentingVisitor(){assertThatThrownBy(()->PinnedWebhookTransportAdapter.postbackPayload("meta","123456\nsecret\nv99.0",PAYLOAD.replace("pseudonymous",""))).hasMessageContaining("consenting visitor");}
}
