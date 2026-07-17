package io.mofazzal.sentinel.alert.api;

import io.mofazzal.sentinel.alert.messaging.AlertPublishException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AlertController.class)
public class AlertExceptionHandler {

    @ExceptionHandler(AlertPublishException.class)
    ResponseEntity<ProblemDetail> handlePublishFailure(AlertPublishException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The alert could not be durably queued; retry the request."
        );
        detail.setTitle("Alert queue unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }
}
