package io.beep.core.adapter.in.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protège /internal/admin/** par jeton de service. Accepte plusieurs jetons simultanément :
 * pendant une rotation (trimestrielle), l'ancien et le nouveau sont valides, puis l'ancien est retiré.
 * Chaque appel est journalisé (qui, quoi) ; le back office journalise de son côté.
 */
public class AdminTokenFilter extends OncePerRequestFilter {
  private static final Logger audit = LoggerFactory.getLogger("admin-audit");

  private final List<byte[]> tokens;

  public AdminTokenFilter(List<String> acceptedTokens) {
    this.tokens = acceptedTokens.stream().filter(t -> t != null && !t.isBlank()).map(t -> t.getBytes(StandardCharsets.UTF_8)).toList();
    if (tokens.isEmpty()) throw new IllegalStateException("ADMIN_API_TOKEN est obligatoire");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    boolean ok = false;
    if (header != null && header.startsWith("Bearer ")) {
      byte[] presented = header.substring(7).getBytes(StandardCharsets.UTF_8);
      for (byte[] t : tokens) ok |= MessageDigest.isEqual(t, presented); // temps constant, tous les jetons évalués
    }
    String staff = String.valueOf(req.getHeader("X-Staff-Id")).replaceAll("[^A-Za-z0-9-]", "");
    if (!ok) {
      audit.warn("admin call denied method={} path={} staff={}", req.getMethod(), req.getRequestURI(), staff);
      res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      res.setContentType("application/problem+json");
      res.getWriter().write("{\"status\":401,\"title\":\"unauthorized\",\"code\":\"unauthorized\"}");
      return;
    }
    audit.info("admin call method={} path={} staff={}", req.getMethod(), req.getRequestURI(), staff);
    chain.doFilter(req, res);
  }
}
