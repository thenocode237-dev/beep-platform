package io.beep.bo.adapter.in.http;

import io.beep.bo.app.port.CoreGatewayPort;
import io.beep.bo.domain.DomainException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** application/problem+json avec un `code` stable. Les détails techniques restent dans les journaux. */
@RestControllerAdvice
@Profile("!migrate")
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final Map<String, HttpStatus> STATUS = Map.of(
      "invalid_request", HttpStatus.BAD_REQUEST,
      "invalid_email", HttpStatus.BAD_REQUEST,
      "invalid_reason", HttpStatus.BAD_REQUEST,
      "invalid_credentials", HttpStatus.UNAUTHORIZED,
      "unauthorized", HttpStatus.UNAUTHORIZED,
      "forbidden", HttpStatus.FORBIDDEN,
      "reauth_required", HttpStatus.FORBIDDEN,
      "rate_limited", HttpStatus.TOO_MANY_REQUESTS,
      "not_found", HttpStatus.NOT_FOUND,
      "draft_outdated", HttpStatus.CONFLICT);

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e) {
    return problem(STATUS.getOrDefault(e.code(), HttpStatus.UNPROCESSABLE_ENTITY), e.code());
  }

  /** Refus du core (validation, ressource inconnue, conflit) : statut et code relayés au front. */
  @ExceptionHandler(CoreGatewayPort.CoreRejectedException.class)
  ResponseEntity<ProblemDetail> coreRejected(CoreGatewayPort.CoreRejectedException e) {
    HttpStatus status = HttpStatus.resolve(e.status());
    return problem(status == null || status == HttpStatus.UNAUTHORIZED ? HttpStatus.BAD_GATEWAY : status, e.code()); // 401 du core = jeton de service invalide
  }

  @ExceptionHandler(CoreGatewayPort.CoreUnavailableException.class)
  ResponseEntity<ProblemDetail> core(CoreGatewayPort.CoreUnavailableException e) {
    log.warn("beep-core indisponible : {}", e.getMessage());
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "core_unavailable");
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
    log.error("erreur inattendue", e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
  }

  static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
    ProblemDetail p = ProblemDetail.forStatus(status);
    p.setTitle(code);
    p.setProperty("code", code);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
  }
}
