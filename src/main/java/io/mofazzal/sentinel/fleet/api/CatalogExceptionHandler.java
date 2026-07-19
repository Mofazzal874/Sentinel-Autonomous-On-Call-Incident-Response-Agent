package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.fleet.application.CatalogConflictException;
import io.mofazzal.sentinel.fleet.application.CatalogNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogAdministrationController.class)
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    CatalogContracts.Problem notFound(CatalogNotFoundException exception) {
        return new CatalogContracts.Problem("NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({CatalogConflictException.class, ObjectOptimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    CatalogContracts.Problem conflict(Exception exception) {
        String message = exception instanceof CatalogConflictException
                ? exception.getMessage()
                : "the resource conflicts with current database state";
        return new CatalogContracts.Problem("CONFLICT", message);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    CatalogContracts.Problem badRequest(RuntimeException exception) {
        return new CatalogContracts.Problem("INVALID_REQUEST", exception.getMessage());
    }
}
