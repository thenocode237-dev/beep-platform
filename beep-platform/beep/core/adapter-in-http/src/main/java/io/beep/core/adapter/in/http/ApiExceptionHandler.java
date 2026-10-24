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
      Map.entry("invalid_email", HttpStatus.BAD_REQUEST),
      Map.entry("weak_password", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_request", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_token", HttpStatus.BAD_REQUEST),
      Map.entry("invalid_credentials", HttpStatus.UNAUTHORIZED),
      Map.entry("unauthorized", HttpStatus.UNAUTHORIZED),
      Map.entry("totp_required", HttpStatus.UNAUTHORIZED),
      Map.entry("invalid_totp", HttpStatus.UNAUTHORIZED),
      Map.entry("email_not_verified", HttpStatus.FORBIDDEN),
      Map.entry("totp_already_enabled", HttpStatus.CONFLICT),
      Map.entry("rate_limited", HttpStatus.TOO_MANY_REQUESTS));

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e) {
    return problem(STATUS.getOrDefault(e.code(), HttpStatus.UNPROCESSABLE_ENTITY), e.code());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
  ResponseEntity<ProblemDetail> badRequest(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid_request");
  }

  @ExceptionHandler(TypeMismatchException.class)
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
