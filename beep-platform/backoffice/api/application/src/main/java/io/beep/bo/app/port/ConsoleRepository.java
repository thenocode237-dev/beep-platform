package io.beep.bo.app.port;
import java.util.*;
import java.time.Instant;
public interface ConsoleRepository {
  List<Map<String,Object>> drafts();
  Map<String,Object> saveDraft(UUID staff,Map<String,Object> body,Instant now);
  Map<String,Object> draft(UUID id);
  void markPublished(UUID id,int expected,int published,String status,Instant now);
  List<Map<String,Object>> notes(UUID workspace);
  void note(UUID workspace,UUID staff,String body,Instant now);
  void impersonation(String hash,UUID staff,UUID workspace,Instant expiresAt);
  UUID impersonated(String hash,UUID staff,Instant now);
  void revokeImpersonation(String hash,UUID staff);
  List<Map<String,Object>> staff();
  void staffRole(UUID actor,UUID target,String role,boolean active);
  List<UUID> workspaceReferences(UUID after,int limit);
  void purgeWorkspace(UUID workspace);
  void expireViews(Instant now);
}
