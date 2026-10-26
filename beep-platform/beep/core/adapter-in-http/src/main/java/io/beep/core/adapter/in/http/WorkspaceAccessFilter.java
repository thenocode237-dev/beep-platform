package io.beep.core.adapter.in.http;
import io.beep.core.app.ecosystem.EcosystemUseCase;
import io.beep.core.app.port.EcosystemRepository;
import io.beep.core.app.workspace.WorkspaceScope;
import io.beep.core.domain.DomainException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Profile("!worker & !migrate") @Order(-10)
public class WorkspaceAccessFilter extends OncePerRequestFilter {
  private final io.beep.core.app.enterprise.EnterpriseUseCase enterprise;private final SessionCookies cookies;private final EcosystemUseCase service;private final EcosystemRepository data;private final Clock clock;private final String origin;
  public WorkspaceAccessFilter(SessionCookies cookies,EcosystemUseCase service,EcosystemRepository data,Clock clock,io.beep.core.app.common.PublicSite site,io.beep.core.app.enterprise.EnterpriseUseCase enterprise){this.enterprise=enterprise;this.cookies=cookies;this.service=service;this.data=data;this.clock=clock;var u=java.net.URI.create(site.url());this.origin=u.getScheme()+"://"+u.getRawAuthority();}
  @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=r.getRequestURI();return !p.startsWith("/v1/")||p.startsWith("/v1/auth/")||p.startsWith("/v1/mobile/")||p.startsWith("/v1/integrations/hooks/")||p.equals("/v1/content")||p.equals("/v1/public/plans")||p.equals("/v1/reports")||p.equals("/v1/openapi.json")||p.equals("/v1/billing/webhook");}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    try{
      UUID selected=req.getAttribute("beep.api.workspace") instanceof UUID id?id:null;
      if(selected==null){String raw=req.getHeader("X-Workspace-Id");if(raw==null&&req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals("beep_workspace"))raw=c.getValue();if(raw!=null&&!raw.isBlank())try{selected=UUID.fromString(raw);}catch(IllegalArgumentException e){throw new DomainException("invalid_request");}}
      String route=req.getRequestURI();if((Set.of("/v1/workspaces","/v1/workspaces/select","/v1/team/accept").contains(route)||route.startsWith("/v1/account/"))&&req.getAttribute("beep.api.workspace")==null)selected=null;
      res.setHeader("Cache-Control","private, no-store");
      try(var scope=WorkspaceScope.open(selected)){
        var user=cookies.requireUser(req);UUID wid=service.workspace(user);enterprise.checkAccess(wid,req.getRemoteAddr(),cookies.token(req),req.getAttribute("beep.api.workspace")!=null);var role=service.role(user);String path=req.getRequestURI();boolean write=!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod());
        if(path.startsWith("/v1/billing")&&role!=io.beep.core.domain.team.WorkspaceRole.OWNER)throw new DomainException("forbidden");
        if(write){String from=req.getHeader("Origin");if(req.getAttribute("beep.api.workspace")==null&&from!=null&&!from.equals(origin))throw new DomainException("forbidden");if(!role.writes()&&!path.equals("/v1/team/accept")&&!path.equals("/v1/workspaces/select"))throw new DomainException("forbidden");
          if((path.startsWith("/v1/developer/")||path.startsWith("/v1/webhooks")||path.startsWith("/v1/postbacks"))&&!role.manages())throw new DomainException("forbidden");
          if(path.startsWith("/v1/billing")&&role!=io.beep.core.domain.team.WorkspaceRole.OWNER)throw new DomainException("forbidden");}
        chain.doFilter(req,res);
        if(write&&res.getStatus()>=200&&res.getStatus()<300&&path.matches("/v1/(links|qr-codes|bio-pages|domains)(/.*)?"))data.audit(wid,user.id(),req.getMethod().toLowerCase(Locale.ROOT),path,clock.instant());
      }
    }catch(DomainException e){int status=switch(e.code()){case "unauthorized"->401;case "not_found"->404;case "invalid_request"->400;default->403;};res.setStatus(status);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":"+status+",\"code\":\""+e.code()+"\"}");}
    catch(Exception e){if(e instanceof IOException x)throw x;if(e instanceof ServletException x)throw x;throw new ServletException(e);}
  }
}
