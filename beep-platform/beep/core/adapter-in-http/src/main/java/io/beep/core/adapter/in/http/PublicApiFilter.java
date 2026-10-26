package io.beep.core.adapter.in.http;
import io.beep.core.app.conversion.DeveloperUseCase;
import io.beep.core.app.port.*;
import io.beep.core.app.port.DeveloperRepository.Reply;
import io.beep.core.domain.DomainException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.time.*;
import java.util.*;

@org.springframework.core.annotation.Order(-20)
@Component @Profile("!worker & !migrate")
public class PublicApiFilter extends OncePerRequestFilter {
  private final DeveloperUseCase developer;private final DeveloperRepository data;private final TokenPort tokens;private final TransactionPort tx;private final Clock clock;
  public PublicApiFilter(DeveloperUseCase developer,DeveloperRepository data,TokenPort tokens,TransactionPort tx,Clock clock){this.developer=developer;this.data=data;this.tokens=tokens;this.tx=tx;this.clock=clock;}
  @Override protected boolean shouldNotFilter(HttpServletRequest req){return !req.getRequestURI().startsWith("/v1/")||req.getHeader("Authorization")==null;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    UUID ws=null;
    try{
      String auth=req.getHeader("Authorization"),path=req.getRequestURI();
      if(!auth.startsWith("Bearer sk_"))throw new DomainException("unauthorized");
      // Explicit route list: API credentials never grant account, billing, key or visitor-privacy access.
      if(!path.matches("/v1/(?:links|qr-codes|bio-pages|domains)(?:/[0-9a-fA-F-]{36}(?:/(?:stats(?:\\.csv)?|render|blocks|verify))?)?")&&!path.equals("/v1/events")&&!path.equals("/v1/conversions/analytics"))throw new DomainException("forbidden");
      String scope=path.equals("/v1/events")?"events":Set.of("GET","HEAD").contains(req.getMethod())?"read":"write";
      var principal=developer.authenticate(auth.substring(7),scope,false,null);ws=principal.workspace();req.setAttribute("beep.api.user",principal.user());req.setAttribute("beep.api.workspace",ws);
      String key=req.getHeader("Idempotency-Key");
      if(key!=null&&!Set.of("GET","HEAD").contains(req.getMethod())){
        if(!key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new DomainException("invalid_request");
        byte[] bytes=req.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new DomainException("invalid_request");
        var wrapped=new HttpServletRequestWrapper(req){@Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){throw new UnsupportedOperationException();}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}};
        String fingerprint=tokens.hash(req.getMethod()+":"+path+":"+Objects.toString(req.getQueryString(),"")+":"+Base64.getEncoder().encodeToString(bytes));UUID workspace=ws;
        var response=new ContentCachingResponseWrapper(res);
        tx.run(()->{data.lock(workspace);var saved=data.reply(workspace,key,clock.instant());if(saved.isPresent()){
          var r=saved.get();if(!r.fingerprint().equals(fingerprint))throw new DomainException("conflict");
          response.setStatus(r.status());response.setContentType(r.contentType());try{response.getWriter().write(r.body());}catch(IOException e){throw new UncheckedIOException(e);}response.setHeader("Idempotency-Replayed","true");
        }else{
          try{chain.doFilter(wrapped,response);}catch(IOException e){throw new UncheckedIOException(e);}catch(ServletException e){throw new IllegalStateException(e);}
          // Only successful mutations are cached. A rolled-back attempt can safely be retried.
          if(response.getStatus()>=200&&response.getStatus()<300)data.reply(workspace,key,new Reply(fingerprint,response.getStatus(),response.getContentType(),new String(response.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8)),clock.instant());
        }});response.copyBodyToResponse();
      }else chain.doFilter(req,res);
    }catch(DomainException e){int status=switch(e.code()){case "unauthorized"->401;case "rate_limited"->429;case "conflict"->409;case "invalid_request"->400;default->403;};res.setStatus(status);res.setContentType("application/problem+json");if(status==429)res.setHeader("Retry-After","60");res.getWriter().write("{\"status\":"+status+",\"code\":\""+e.code()+"\"}");}
    finally{if(ws!=null)try{data.apiMetric(ws,res.getStatus(),clock.instant());}catch(RuntimeException ignored){}}
  }
}
