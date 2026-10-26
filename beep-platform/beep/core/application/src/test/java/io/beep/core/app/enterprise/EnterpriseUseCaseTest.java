package io.beep.core.app.enterprise;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.port.*;
import io.beep.core.domain.user.*;
import io.beep.core.domain.workspace.Workspace;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class EnterpriseUseCaseTest {
 final UUID workspace=UUID.randomUUID(),app=UUID.randomUUID();final Instant now=Instant.parse("2026-10-26T12:00:00Z");Map<String,Object> contract=new HashMap<>(Map.of("version",2,"status","active","startsAt",now.minusSeconds(60).toString(),"endsAt",now.plusSeconds(86400).toString(),"ipAllowlist",List.of("192.0.2.0/24"),"sso",Map.of("required",true)));int issued,consumed;boolean proof;
 @SuppressWarnings("unchecked") <T>T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> call){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{type},(p,m,a)->call.apply(m.getName(),a));}
 final TokenPort tokens=new TokenPort(){public String newToken(){return "A".repeat(43);}public String hash(String token){return "hash:"+token;}};
 final SecretCipherPort cipher=new SecretCipherPort(){public String encrypt(String v){return "encrypted:"+v;}public String decrypt(String v){return v.substring(10);}};
 EnterpriseUseCase service(){EnterpriseRepository data=proxy(EnterpriseRepository.class,(name,args)->switch(name){case "contract"->Optional.of(contract);case "hasProof"->proof;case "app"->Optional.of(Map.of("id",app,"workspaceId",workspace.toString(),"scheme","acme","enabled",true,"androidStore","https://play.google.com/store/apps/details?id=org.acme","iosStore","https://apps.apple.com/app/id123"));case "deferred"->{issued++;assertThat(args[0]).isEqualTo("hash:"+"A".repeat(43));assertThat(args[2]).isEqualTo("encrypted:acme://product/42");yield null;}case "consumeState"->{consumed++;yield Optional.empty();}default->null;});WorkspaceRepository ws=proxy(WorkspaceRepository.class,(name,args)->name.equals("findById")?Optional.of(Workspace.personal(workspace,UUID.randomUUID(),"Enterprise",now)):null);return new EnterpriseUseCase(data,null,null,ws,null,null,null,tokens,cipher,null,null,Clock.fixed(now,ZoneOffset.UTC),null);}
 @Test void consentIsRequiredBeforeIssuingADeferredToken(){assertThatThrownBy(()->service().issue(app,Map.of("platform","android","destination","acme://product/42","consent",false))).hasMessage("consent_required");assertThat(issued).isZero();var result=service().issue(app,Map.of("platform","android","destination","acme://product/42","consent",true));assertThat(issued).isEqualTo(1);assertThat(result.get("storeUrl").toString()).contains("referrer=beep_token%3D");}
 @Test void foreignSchemesAndInactiveContractsCannotIssueTokens(){for(String destination:List.of("https://evil.example","javascript:alert(1)","other://product/42"))assertThatThrownBy(()->service().issue(app,Map.of("platform","ios","destination",destination,"consent",true))).hasMessage("invalid_request");contract.put("status","expired");assertThatThrownBy(()->service().issue(app,Map.of("platform","ios","destination","acme://product/42","consent",true))).hasMessage("feature_unavailable");}
 @Test void ipRestrictionAppliesToApiKeysAndCookieSessions(){for(boolean api:List.of(true,false))assertThatThrownBy(()->service().checkAccess(workspace,"203.0.113.1","session",api)).hasMessage("ip_not_allowed");assertThatCode(()->service().checkAccess(workspace,"192.0.2.8",null,true)).doesNotThrowAnyException();}
 @Test void requiredSsoProofIsNotSilentlyRemovedAtContractExpiry(){assertThatThrownBy(()->service().checkAccess(workspace,"192.0.2.8","session",false)).hasMessage("sso_required");contract.put("status","expired");assertThatThrownBy(()->service().checkAccess(workspace,"192.0.2.8","session",false)).hasMessage("sso_required");proof=true;assertThatCode(()->service().checkAccess(workspace,"192.0.2.8","session",false)).doesNotThrowAnyException();}
 @Test void callbackRejectsBrowserStateMismatchBeforeConsumingServerState(){assertThatThrownBy(()->service().complete(workspace,"state","other","code","192.0.2.8")).hasMessage("sso_failed");assertThat(consumed).isZero();}
}
