package io.beep.core.app.port;
import java.time.Instant;
import java.util.*;
public interface EnterpriseRepository {
 Optional<Map<String,Object>> contract(UUID workspace);
 void saveContract(UUID workspace,Map<String,Object> contract,Map<String,Object> sso,String cipher,UUID staff,Instant now);
 String ssoSecret(UUID workspace);
 Map<String,Object> overview(Instant now);
 void evidence(Map<String,Object> body,UUID staff,Instant now);
 void sdk(Map<String,Object> body,UUID staff,Instant now);
 void review(UUID workspace,String decision,String reason,UUID staff,Instant now);
 Map<String,Object> failover(String node,UUID request,String action,String reason,UUID staff,Instant now);
 void heartbeat(String id,String role,String region,Map<String,Object> capacity,Instant now);
 void state(String hash,UUID workspace,int version,String verifierCipher,String nonce,Instant expires);
 Optional<Map<String,Object>> consumeState(String hash,Instant now);
 Optional<UUID> identity(UUID workspace,String issuer,String subject);
 void bindIdentity(UUID workspace,String issuer,String subject,UUID user);
 void proof(String sessionHash,UUID workspace,int version);
 boolean hasProof(String sessionHash,UUID workspace,int version);
 void pendingProof(String mfaHash,UUID workspace,int version,Instant expires);
 void completeProof(String mfaHash,String sessionHash,Instant now);
 List<Map<String,Object>> apps(UUID workspace);
 void app(UUID workspace,Map<String,Object> body,Instant now);
 void disableApp(UUID workspace,UUID app);
 Optional<Map<String,Object>> app(UUID app);
 void deferred(String hash,UUID app,String cipher,String platform,Instant now,Instant expires);
 Optional<String> consumeDeferred(String hash,UUID app,String platform,Instant now);
 void retention(Instant now);
}
