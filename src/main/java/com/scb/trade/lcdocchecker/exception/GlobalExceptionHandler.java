package com.scb.trade.lcdocchecker.exception;

import com.scb.trade.lcdocchecker.domain.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Resilient global exception handler. Always returns a standard {@link ErrorResponse}
 * ({@code {"error": "<CODE>", "message": "<detail>"}}) and never leaks raw stack traces.
 *
 * <ul>
 *   <li>{@link InvalidMt700Exception}, {@link DocumentExtractionException},
 *       {@link UploadRejectedException} → 422 UNPROCESSABLE_ENTITY.</li>
 *   <li>{@link NotFoundException} → 404.</li>
 *   <li>missing multipart parts/params → 400.</li>
 *   <li>anything else → 500 (message generic; detail logged).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidMt700Exception.class)
    public ResponseEntity<ErrorResponse> handleInvalidMt700(InvalidMt700Exception ex) {
        return unprocessable("Invalid MT700 format: " + ex.getMessage());
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ErrorResponse> handleExtraction(DocumentExtractionException ex) {
        return unprocessable(ex.getMessage());
    }

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUpload(UploadRejectedException ex) {
        return unprocessable(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMissingPart(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "Required request part or parameter is missing: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error during check processing", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred while processing the request."));
    }

    private static ResponseEntity<ErrorResponse> unprocessable(String message) {
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse("UNPROCESSABLE_ENTITY", message));
    }
}
