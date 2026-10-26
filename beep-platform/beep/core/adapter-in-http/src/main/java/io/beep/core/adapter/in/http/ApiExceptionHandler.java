package io.beep.core.adapter.in.http;

import io.beep.core.domain.DomainException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs au format application/problem+json, avec un `code` stable consommé par les fronts. */
@RestControllerAdvice
@Profile("!worker & !migrate")
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final Map<String, HttpStatus> STATUS = Map.ofEntries(
      Map.entry("sso_required", HttpStatus.FORBIDDEN),
      Map.entry("ip_not_allowed", HttpStatus.FORBIDDEN),
      Map.entry("sso_failed", HttpStatus.BAD_REQUEST),
      Map.entry("sso_host_not_allowed", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_csv", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_role", HttpStatus.BAD_REQUEST),
      Map.entry("quota_reached", HttpStatus.CONFLICT),
      Map.entry("forbidden", HttpStatus.FORBIDDEN),
      Map.entry("consent_required", HttpStatus.FORBIDDEN),
      Map.entry("invalid_click_id", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_email", HttpStatus.BAD_REQUEST),
      Map.entry("weak_password", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_request", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_host", HttpStatus.BAD_REQUEST),
      Map.entry("destination_blocked", HttpStatus.FORBIDDEN),
      Map.entry("conflict", HttpStatus.CONFLICT),
      Map.entry("invalid_token", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_credentials", HttpStatus.UNAUTHORIZED),
      Map.entry("unauthorized", HttpStatus.UNAUTHORIZED),
      Map.entry("totp_required", HttpStatus.UNAUTHORIZED),
      Map.entry("invalid_totp", HttpStatus.UNAUTHORIZED),
      Map.entry("email_not_verified", HttpStatus.FORBIDDEN),
      Map.entry("totp_already_enabled", HttpStatus.CONFLICT),
      Map.entry("rate_limited", HttpStatus.TOO_MANY_REQUESTS),
      // phase 1 : validation
      Map.entry("invalid_slug", HttpStatus.BAD_REQUEST),
      Map.entry("reserved_slug", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_destination", HttpStatus.BAD_REQUEST),
      Map.entry("destination_loop", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_title", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_description", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_reason", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_qr", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_qr_logo", HttpStatus.BAD_REQUEST),
      Map.entry("qr_low_contrast", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_format", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_block", HttpStatus.BAD_REQUEST),
      Map.entry("too_many_blocks", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_theme", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_seo", HttpStatus.BAD_REQUEST),
      Map.entry("unknown_entitlement", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_entitlement", HttpStatus.BAD_REQUEST),
      Map.entry("oauth_failed", HttpStatus.BAD_REQUEST),
      Map.entry("oauth_email_unverified", HttpStatus.BAD_REQUEST),
      // phase 2 : facturation
      Map.entry("invalid_cycle", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_method", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_country", HttpStatus.BAD_REQUEST),
      Map.entry("unsupported_country", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_phone", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_coupon", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_amount", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_plan", HttpStatus.BAD_REQUEST),
      Map.entry("coupon_inactive", HttpStatus.CONFLICT),
      Map.entry("coupon_expired", HttpStatus.CONFLICT),
      Map.entry("coupon_exhausted", HttpStatus.CONFLICT),
      Map.entry("coupon_not_applicable", HttpStatus.CONFLICT),
      Map.entry("coupon_exists", HttpStatus.CONFLICT),
      Map.entry("plan_not_purchasable", HttpStatus.CONFLICT),
      Map.entry("plan_change_at_period_end", HttpStatus.CONFLICT),
      Map.entry("trial_already_used", HttpStatus.CONFLICT),
      Map.entry("subscription_active", HttpStatus.CONFLICT),
      Map.entry("payment_unavailable", HttpStatus.BAD_GATEWAY),
      // phase 2 : règles de lien et catalogue
      Map.entry("invalid_expiry", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_rule", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_utm", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_password", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_app", HttpStatus.BAD_REQUEST),
      // phase 2 : domaines personnalisés
      Map.entry("invalid_hostname", HttpStatus.BAD_REQUEST),
      Map.entry("reserved_hostname", HttpStatus.BAD_REQUEST),
      Map.entry("domain_taken", HttpStatus.CONFLICT),
      Map.entry("domain_in_use", HttpStatus.CONFLICT),
      Map.entry("domain_not_verified", HttpStatus.CONFLICT),
      // phase 1 : droits et état
      Map.entry("quota_exceeded", HttpStatus.FORBIDDEN),
      Map.entry("workspace_suspended", HttpStatus.FORBIDDEN),
      Map.entry("feature_unavailable", HttpStatus.FORBIDDEN),
      Map.entry("invite_required", HttpStatus.FORBIDDEN),
      Map.entry("not_found", HttpStatus.NOT_FOUND),
      Map.entry("slug_taken", HttpStatus.CONFLICT),
      Map.entry("link_in_use", HttpStatus.CONFLICT),
      Map.entry("status_unchanged", HttpStatus.CONFLICT),
      Map.entry("already_registered", HttpStatus.CONFLICT));

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e) {
    return problem(STATUS.getOrDefault(e.code(), HttpStatus.UNPROCESSABLE_ENTITY), e.code());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
  ResponseEntity<ProblemDetail> badRequest(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid_request");
  }

  @ExceptionHandler({TypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
  ResponseEntity<ProblemDetail> typeMismatch(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid_request");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    // Erreurs HTTP portées par Spring (route inconnue, méthode non permise…) : on garde leur statut, pas un 500.
    if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
      HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
      return problem(status, status == HttpStatus.NOT_FOUND ? "not_found" : status == HttpStatus.METHOD_NOT_ALLOWED ? "method_not_allowed" : "invalid_request");
    }
    log.error("erreur inattendue", e); // le détail reste dans les logs, jamais dans la réponse
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
  }

  static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
    ProblemDetail p = ProblemDetail.forStatus(status);
    p.setTitle(code);
    p.setProperty("code", code);
    return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(p);
  }
}
