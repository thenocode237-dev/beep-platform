package io.beep.core.adapter.out.oauth;
import static org.assertj.core.api.Assertions.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class OidcEnterpriseTest {
 final Instant now=Instant.parse("2026-10-26T12:00:00Z");final OidcEnterpriseAdapter adapter=new OidcEnterpriseAdapter("idp.example",Clock.fixed(now,ZoneOffset.UTC));
 final Map<String,Object> config=Map.of("issuer","https://idp.example","clientId","beep","authorizationUrl","https://idp.example/authorize","tokenUrl","https://idp.example/token","jwksUrl","https://idp.example/jwks");
 String token(RSAKey key,String issuer,String audience,String nonce,Instant expires)throws Exception{var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("subject-1").audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(expires)).claim("nonce",nonce).claim("email","member@example.org").claim("email_verified",true).build();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-1").build(),claims);jwt.sign(new RSASSASigner(key));return jwt.serialize();}
 @Test void verifiesSignatureIssuerAudienceNonceAndExpiry()throws Exception{var key=new RSAKeyGenerator(2048).keyID("key-1").generate();String jwks=new JWKSet(key.toPublicJWK()).toString();assertThat(adapter.verify(token(key,"https://idp.example","beep","nonce",now.plusSeconds(60)),jwks,config,"nonce").subject()).isEqualTo("subject-1");for(String bad:List.of(token(key,"https://evil.example","beep","nonce",now.plusSeconds(60)),token(key,"https://idp.example","other","nonce",now.plusSeconds(60)),token(key,"https://idp.example","beep","other",now.plusSeconds(60)),token(key,"https://idp.example","beep","nonce",now.minusSeconds(60))))assertThatThrownBy(()->adapter.verify(bad,jwks,config,"nonce")).hasMessage("sso_failed");}
 @Test void rejectsUnknownSigningKeys()throws Exception{var real=new RSAKeyGenerator(2048).keyID("key-1").generate();var fake=new RSAKeyGenerator(2048).keyID("key-1").generate();assertThatThrownBy(()->adapter.verify(token(fake,"https://idp.example","beep","nonce",now.plusSeconds(60)),new JWKSet(real.toPublicJWK()).toString(),config,"nonce")).hasMessage("sso_failed");}
 @Test void idpHostsRequireOperatorApproval(){assertThatCode(()->adapter.validate(config)).doesNotThrowAnyException();var bad=new HashMap<>(config);bad.put("tokenUrl","https://other.example/token");assertThatThrownBy(()->adapter.validate(bad)).hasMessage("sso_host_not_allowed");bad.put("tokenUrl","http://idp.example/token");assertThatThrownBy(()->adapter.validate(bad)).hasMessage("invalid_request");}
}
