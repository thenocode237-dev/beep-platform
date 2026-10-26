package io.beep.core.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.ecosystem.EcosystemUseCase;
import io.beep.core.app.workspace.WorkspaceScope;
import io.beep.core.domain.team.WorkspaceRole;
import io.beep.core.domain.user.*;
import io.beep.core.app.common.PublicSite;
import java.time.*;
import java.util.*;
import java.lang.reflect.Proxy;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
class WorkspaceAccessTest {
  final UUID personal=UUID.randomUUID(),keyWorkspace=UUID.randomUUID(),cookieWorkspace=UUID.randomUUID();WorkspaceRole role=WorkspaceRole.VIEWER;UUID observed;int status;boolean reached;Map<String,Object> attributes=new HashMap<>();Map<String,String> headers=new HashMap<>();
  final User user=User.register(UUID.randomUUID(),new Email("viewer@example.org"),"hash","fr",Instant.now()).verifyEmail(Instant.now());
  final EcosystemUseCase service=new EcosystemUseCase(null,null,null,null,null,null,null,null,null,null){@Override public UUID workspace(User u){observed=WorkspaceScope.selected();return observed==null?personal:observed;}@Override public WorkspaceRole role(User u){return role;}};
  void request(String method,String path)throws Exception{status=200;reached=false;var cookies=new SessionCookies(null,null){@Override public User requireUser(HttpServletRequest r){return user;}@Override public String token(HttpServletRequest r){return null;}};var filter=new WorkspaceAccessFilter(cookies,service,null,Clock.systemUTC(),new PublicSite("http://beep.localhost"),new io.beep.core.app.enterprise.EnterpriseUseCase(null,null,null,null,null,null,null,null,null,null,null,null,null){@Override public void checkAccess(UUID w,String ip,String token,boolean api){}});
    var req=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){case "getMethod"->method;case "getRequestURI"->path;case "getHeader"->headers.get(a[0]);case "getCookies"->new Cookie[]{new Cookie("beep_workspace",cookieWorkspace.toString())};case "getAttribute"->attributes.get(a[0]);case "setAttribute"->{attributes.put(a[0].toString(),a[1]);yield null;}case "removeAttribute"->{attributes.remove(a[0]);yield null;}case "getDispatcherType"->DispatcherType.REQUEST;case "isAsyncStarted","isAsyncSupported"->false;default->null;});
    var res=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->switch(m.getName()){case "setStatus"->{status=(Integer)a[0];yield null;}case "getStatus"->status;case "getWriter"->new PrintWriter(new StringWriter());case "isCommitted"->false;default->null;});filter.doFilter(req,res,(r,s)->reached=true);
  }
  @Test void viewersCannotMutateAnyExistingResourceController()throws Exception{for(String path:List.of("/v1/links","/v1/qr-codes","/v1/bio-pages","/v1/domains","/v1/events","/v1/integrations")){request("POST",path);assertThat(status).as(path).isEqualTo(403);assertThat(reached).isFalse();assertThat(WorkspaceScope.selected()).isNull();}}
  @Test void editorsCannotManageCredentialsOrBilling()throws Exception{role=WorkspaceRole.EDITOR;for(String p:List.of("/v1/developer/keys","/v1/webhooks","/v1/postbacks","/v1/billing/checkout")){request("POST",p);assertThat(status).as(p).isEqualTo(403);assertThat(reached).isFalse();}}
  @Test void privateBillingReadsAreOwnerOnly()throws Exception{role=WorkspaceRole.ADMIN;request("GET","/v1/billing");assertThat(status).isEqualTo(403);assertThat(reached).isFalse();}
  @Test void bearerKeyWorkspaceTakesPrecedenceOverUntrustedSelection()throws Exception{role=WorkspaceRole.OWNER;attributes.put("beep.api.workspace",keyWorkspace);headers.put("X-Workspace-Id",UUID.randomUUID().toString());request("GET","/v1/links");assertThat(reached).isTrue();assertThat(observed).isEqualTo(keyWorkspace);assertThat(WorkspaceScope.selected()).isNull();}
  @Test void switchingRemainsPossibleAfterLosingAccessToASelectedWorkspace()throws Exception{request("POST","/v1/workspaces/select");assertThat(reached).isTrue();assertThat(observed).isNull();}
  @Test void sessionMutationsRejectCrossOriginAndPublicPlansNeedNoSession()throws Exception{role=WorkspaceRole.OWNER;headers.put("Origin","https://evil.example");request("POST","/v1/links");assertThat(status).isEqualTo(403);request("GET","/v1/public/plans");assertThat(reached).isTrue();}
}
