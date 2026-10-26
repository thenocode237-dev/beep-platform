package io.beep.core.app.port;

import io.beep.core.domain.team.WorkspaceRole;
import java.time.Instant;
import java.util.*;

public interface EcosystemRepository {
  record Invite(UUID id,UUID workspace,String email,String role,String tokenHash,Instant expiresAt){}
  record Integration(UUID id,UUID workspace,String provider,String name,Map<String,String> config,String credentials,UUID endpoint,String status){}
  record Incoming(UUID id,UUID integration,UUID workspace,String externalId,String event,io.beep.core.app.conversion.DeveloperUseCase.EventCommand command,String status,int attempts,Instant at){}
  record Probe(UUID link,UUID workspace,String url){}
  record Task(UUID id,UUID workspace,String channel,String destination,String body,int attempts){}
  Optional<WorkspaceRole> role(UUID workspace,UUID user);
  boolean canAccess(UUID workspace,UUID user);
  UUID createWorkspace(UUID owner,String name,Instant now);
  List<Map<String,Object>> workspaces(UUID user);
  Map<String,Object> team(UUID workspace);
  void invite(Invite invitation);
  Optional<Invite> invitation(String tokenHash,Instant now);
  void accept(Invite invitation,UUID user,Instant now);
  void member(UUID workspace,UUID user,String role);
  void remove(UUID workspace,UUID user);
  void revokeInvitation(UUID workspace,UUID id);
  void audit(UUID workspace,UUID actor,String action,String target,Instant now);
  List<Map<String,Object>> audit(UUID workspace);
  List<Map<String,Object>> organization(UUID workspace);
  UUID label(UUID workspace,String kind,String name);
  void removeLabel(UUID workspace,UUID id);
  void organize(UUID workspace,UUID link,UUID folder,List<UUID> tags);
  List<Map<String,Object>> exportLinks(UUID workspace);
  List<Integration> integrations(UUID workspace);
  Optional<Integration> integration(UUID id);
  void integration(Integration integration);
  void disconnect(UUID workspace,UUID id);
  void integrationError(UUID id,String error,Instant now);
  void integrationDelivery(UUID endpoint,Integer status,Instant now);
  boolean incoming(Incoming incoming);
  List<Incoming> claimIncoming(Instant now,int limit);
  boolean incomingActive(UUID workspace,UUID id,int attempt);
  void incomingResult(UUID id,int attempt,String error,Instant now);
  void replayIncoming(UUID workspace,UUID id,Instant now);
  Map<String,Object> radar(UUID workspace);
  UUID rule(UUID workspace,String kind,long threshold,String channel,String destination,UUID link,UUID endpoint,Instant now);
  void removeRule(UUID workspace,UUID id);
  void evaluateRadar(Instant now);
  List<Probe> claimProbes(Instant now,int limit);
  void probeResult(UUID link,String url,int status,Instant now);
  List<Task> claimTasks(Instant now,int limit);
  void taskResult(UUID id,int attempt,String error,Instant now);
  void report(UUID workspace,String frequency,boolean enabled,Instant now);
  void scheduleReports(Instant now);
  Map<String,Object> insights(UUID workspace,Instant since);
  Map<String,Object> affiliate(UUID user);
  String joinAffiliate(UUID user,Instant now);
  void referral(UUID user,UUID workspace,String code,Instant now);
  void accrueCommissions(Instant now);
  boolean submit(UUID page,String block,String encryptedEmail,String encryptedMessage,Instant now);
  List<Map<String,Object>> submissions(UUID workspace);
  void eraseSubmission(UUID workspace,UUID id);
  Map<String,Object> monitor();
  Map<String,Object> staffWorkspace(UUID workspace);
  Map<String,Object> affiliates();
  void payout(UUID staff,List<UUID> commissions,String reference,Instant now);
  Map<String,Object> content();
  Map<String,Object> publicContent(String kind,String locale,String slug);
  void publish(UUID staff,Map<String,Object> publication,Instant now);
  void retention(Instant now);
}
