package io.beep.core.adapter.out.oauth;
import io.beep.core.app.port.EnterpriseSsoPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.enterprise.EnterprisePolicy;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
/** OIDC code + PKCE, RS256, nonce, issuer, audience and authorized party verification.
 * IdP network hosts are approved by the deployment operator, never by a workspace member.
 */
public class OidcEnterpriseAdapter implements EnterpriseSsoPort {
 private final Set<String> hosts;private final Clock clock;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();private final ObjectMapper json=new ObjectMapper();
 public OidcEnterpriseAdapter(String allowedHosts,Clock clock){this.hosts=new HashSet<>();for(String host:allowedHosts.split(","))if(!host.isBlank())hosts.add(host.strip().toLowerCase(Locale.ROOT));this.clock=clock;}
 @Override public void validate(Map<String,Object> c){for(String key:List.of("issuer","authorizationUrl","tokenUrl","jwksUrl")){String url=Objects.toString(c.get(key),"");EnterprisePolicy.https(url);URI uri=URI.create(url);if(!hosts.contains(uri.getHost().toLowerCase(Locale.ROOT))||key.equals("issuer")&&uri.getQuery()!=null)throw new DomainException("sso_host_not_allowed");}String client=Objects.toString(c.get("clientId"),"");if(client.isBlank()||client.length()>256)throw new DomainException("invalid_request");}
 private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 @Override public String authorization(Map<String,Object> c,String state,String nonce,String challenge,String callback){validate(c);String url=c.get("authorizationUrl").toString();return url+(url.contains("?")?"&":"?")+"response_type=code&scope=openid%20email&client_id="+enc(c.get("clientId").toString())+"&redirect_uri="+enc(callback)+"&state="+enc(state)+"&nonce="+enc(nonce)+"&code_challenge_method=S256&code_challenge="+enc(challenge);}
 private String fetch(HttpRequest request)throws Exception{var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var in=response.body()){if(response.statusCode()!=200)throw new DomainException("sso_failed");byte[] body=in.readNBytes(65537);if(body.length>65536)throw new DomainException("sso_failed");return new String(body,StandardCharsets.UTF_8);}}
 @Override public Identity exchange(Map<String,Object> c,String secret,String code,String verifier,String nonce,String callback){validate(c);try{
  String form="grant_type=authorization_code&client_id="+enc(c.get("clientId").toString())+"&code="+enc(code)+"&code_verifier="+enc(verifier)+"&redirect_uri="+enc(callback);if(!secret.isEmpty())form+="&client_secret="+enc(secret);
  JsonNode reply=json.readTree(fetch(HttpRequest.newBuilder(URI.create(c.get("tokenUrl").toString())).timeout(Duration.ofSeconds(10)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build()));String token=reply.path("id_token").asText();if(token.length()>32768)throw new DomainException("sso_failed");return verify(token,fetch(HttpRequest.newBuilder(URI.create(c.get("jwksUrl").toString())).timeout(Duration.ofSeconds(10)).GET().build()),c,nonce);
 }catch(Exception e){throw new DomainException("sso_failed");}}
 Identity verify(String token,String jwks,Map<String,Object> c,String nonce){try{var untrusted=SignedJWT.parse(token);if(!"RS256".equals(untrusted.getHeader().getAlgorithm().getName())||untrusted.getHeader().getKeyID()==null)throw new DomainException("sso_failed");
  JWK key=JWKSet.parse(jwks).getKeyByKeyId(untrusted.getHeader().getKeyID());if(!(key instanceof RSAKey rsa)||rsa.isPrivate()||rsa.getKeyUse()!=null&&!rsa.getKeyUse().equals(com.nimbusds.jose.jwk.KeyUse.SIGNATURE))throw new DomainException("sso_failed");var decoder=NimbusJwtDecoder.withPublicKey(rsa.toRSAPublicKey()).build();var timestamp=new JwtTimestampValidator(Duration.ofSeconds(30));timestamp.setClock(clock);decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(timestamp,new JwtIssuerValidator(c.get("issuer").toString())));var jwt=decoder.decode(token);Instant now=clock.instant();if(jwt.getExpiresAt()==null||!jwt.getExpiresAt().isAfter(now.minusSeconds(30))||jwt.getIssuedAt()==null||jwt.getIssuedAt().isAfter(now.plusSeconds(30))||jwt.getIssuedAt().isBefore(now.minusSeconds(660))||jwt.getSubject()==null||jwt.getSubject().isBlank()||jwt.getSubject().length()>255||!jwt.getAudience().contains(c.get("clientId").toString())||jwt.getAudience().size()>1&&!c.get("clientId").equals(jwt.getClaimAsString("azp"))||jwt.hasClaim("azp")&&!c.get("clientId").equals(jwt.getClaimAsString("azp"))||!MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),Objects.toString(jwt.getClaimAsString("nonce"),"").getBytes(StandardCharsets.UTF_8)))throw new DomainException("sso_failed");
  return new Identity(c.get("issuer").toString(),jwt.getSubject(),jwt.getClaimAsString("email"),Boolean.TRUE.equals(jwt.getClaim("email_verified")));
 }catch(Exception e){throw new DomainException("sso_failed");}}
}
