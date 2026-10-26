package io.beep.core.adapter.in.http;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
/** Reject requests outside this process role even when a proxy is bypassed. */
@Component @Profile("!migrate") @Order(-100)
public class ServiceRoleFilter extends OncePerRequestFilter {
 private final String role;public ServiceRoleFilter(@Value("${BEEP_SERVICE_ROLE:all}")String role){if(!Set.of("all","api","redirector","worker").contains(role))throw new IllegalArgumentException("BEEP_SERVICE_ROLE");this.role=role;}
 public static boolean allowed(String role,String path){if(role.equals("all")||path.startsWith("/actuator/")||path.equals("/healthz"))return true;if(role.equals("worker"))return false;boolean api=path.startsWith("/v1/")||path.startsWith("/internal/admin/");return role.equals("api")?api||path.startsWith("/internal/tls/"):!api;}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{if(!allowed(role,r.getRequestURI())){s.setStatus(404);return;}c.doFilter(r,s);}
}
