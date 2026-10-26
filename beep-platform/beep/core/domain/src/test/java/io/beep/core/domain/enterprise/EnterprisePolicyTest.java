package io.beep.core.domain.enterprise;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
class EnterprisePolicyTest {
 @Test void ipv4NetworksRespectBoundaries(){var net=IpNetwork.parse("192.0.2.0/24");assertThat(net.contains("192.0.2.255")).isTrue();assertThat(net.contains("192.0.3.0")).isFalse();assertThat(IpNetwork.parse("0.0.0.0/0").contains("203.0.113.42")).isTrue();assertThat(IpNetwork.parse("192.0.2.4/32").contains("192.0.2.5")).isFalse();}
 @Test void ipv6AndFamiliesAreHandledWithoutDns(){assertThat(IpNetwork.parse("2001:db8::/32").contains("2001:db8:1::1")).isTrue();assertThat(IpNetwork.parse("2001:db8::/32").contains("2001:db9::1")).isFalse();assertThat(IpNetwork.parse("::/0").contains("192.0.2.1")).isFalse();for(String invalid:List.of("localhost/24","10.2/16","192.0.2.1/33","192.0.2.1/-1","256.1.1.1","01.1.1.1","fe80::1%eth0","127.0.0.1/"))assertThatThrownBy(()->IpNetwork.parse(invalid)).hasMessage("invalid_request");}
 @Test void allowlistFailsClosedForMissingAndInvalidAddresses(){var c=Map.<String,Object>of("ipAllowlist",List.of("192.0.2.0/24"));assertThatCode(()->EnterprisePolicy.allowed(c,"192.0.2.4")).doesNotThrowAnyException();assertThatThrownBy(()->EnterprisePolicy.allowed(c,null)).hasMessage("ip_not_allowed");assertThatThrownBy(()->EnterprisePolicy.allowed(c,"192.0.2.4, 127.0.0.1")).hasMessage("ip_not_allowed");}
 @Test void contractValidityIsHalfOpen(){var c=Map.<String,Object>of("status","active","startsAt","2026-10-26T00:00:00Z","endsAt","2027-10-26T00:00:00Z");assertThat(EnterprisePolicy.active(c,Instant.parse("2026-10-26T00:00:00Z"))).isTrue();assertThat(EnterprisePolicy.active(c,Instant.parse("2027-10-26T00:00:00Z"))).isFalse();}
 @Test void fractionalAndNonFiniteLimitsAreRejected(){for(Number number:List.of(1.5,Double.NaN,Double.POSITIVE_INFINITY,999999999999L))assertThatThrownBy(()->EnterprisePolicy.number(Map.of("limit",number),"limit",1,3650)).hasMessage("invalid_request");}
}
