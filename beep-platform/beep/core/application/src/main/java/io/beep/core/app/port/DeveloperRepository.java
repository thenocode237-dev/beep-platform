package io.beep.core.app.port;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import io.beep.core.domain.conversion.Attribution.Touch;

public interface DeveloperRepository {
  record ApiKey(UUID id, UUID workspaceId, String name, String prefix, String hash, Set<String> scopes, List<String> origins, boolean publicKey, Instant revokedAt, Instant createdAt) {}
  record Tracking(UUID linkId, UUID workspaceId, boolean enabled, String model, int windowDays, Map<String,String> pixels) {}
  record Event(UUID id, UUID workspaceId, String name, String visitor, String cid, UUID linkId, BigDecimal value, String currency, String page, Instant at,Map<String,String> utm) {
    public Event(UUID id,UUID workspaceId,String name,String visitor,String cid,UUID linkId,BigDecimal value,String currency,String page,Instant at){this(id,workspaceId,name,visitor,cid,linkId,value,currency,page,at,Map.of());}
  }
  record Endpoint(UUID id, UUID workspaceId, String url, Set<String> events, String secretCipher, boolean enabled, Instant createdAt, String provider, String configCipher) {
    public Endpoint(UUID id,UUID workspaceId,String url,Set<String> events,String secretCipher,boolean enabled,Instant createdAt){this(id,workspaceId,url,events,secretCipher,enabled,createdAt,"webhook",null);}
  }
  record Delivery(UUID id, UUID workspaceId, UUID endpointId, String event, String payload, int attempts, String status, Integer httpStatus, Instant nextAt, Instant createdAt) {}
  record Reply(String fingerprint, int status, String contentType, String body) {}
  void key(ApiKey key);
  Optional<ApiKey> keyByHash(String hash);
  List<ApiKey> keys(UUID workspace);
  void revoke(UUID workspace, UUID id, Instant at);
  void lock(UUID workspace);
  boolean rate(UUID workspace, String bucket, long limit, Instant now);
  Optional<Reply> reply(UUID workspace, String key, Instant now);
  void reply(UUID workspace, String key, Reply reply, Instant now);
  Optional<Tracking> tracking(UUID link);
  void tracking(Tracking config);
  List<Tracking> trackingFor(UUID workspace);
  void touch(UUID workspace, String cid, UUID link, String visitor, Instant at);
  void identify(UUID workspace, String cid, String visitor);
  List<Touch> touches(UUID workspace, String visitor, Instant since);
  boolean event(Event event, long limit);
  Map<String,Object> analytics(UUID workspace, Instant since);
  List<Event> events(UUID workspace, Instant after, UUID afterId, int limit);
  List<Event> visitor(UUID workspace, String visitor);
  void eraseVisitor(UUID workspace, String visitor);
  void endpoint(Endpoint endpoint);
  List<Endpoint> endpoints(UUID workspace);
  void disableEndpoint(UUID workspace, UUID id);
  List<Delivery> deliveries(UUID workspace);
  void replay(UUID workspace, UUID id, Instant now);
  List<Delivery> claim(Instant now, int limit);
  void delivered(UUID id, int attempt, Integer status, Instant next, boolean terminal, Instant now);
  Map<String,Object> monitor();
  void apiMetric(UUID workspace, int status, Instant now);
  void rejection(String reason, Instant now);
  void retention(Instant now);
}
