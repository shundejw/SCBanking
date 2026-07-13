package com.scb.trade.lcdocchecker.exception;

import com.scb.trade.lcdocchecker.domain.ErrorResponse;
import com.scb.trade.lcdocchecker.util.FlowLog;
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
        FlowLog.warn(log, GlobalExceptionHandler.class, "handleInvalidMt700",
                "stage", "ERROR",
                "errorType", "InvalidMt700Exception",
                "errorMessage", ex.getMessage());
        return unprocessable("Invalid MT700 format: " + ex.getMessage());
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ErrorResponse> handleExtraction(DocumentExtractionException ex) {
        FlowLog.warn(log, GlobalExceptionHandler.class, "handleExtraction",
                "stage", "ERROR",
                "errorType", "DocumentExtractionException",
                "errorMessage", ex.getMessage());
        return unprocessable(ex.getMessage());
    }

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUpload(UploadRejectedException ex) {
        FlowLog.warn(log, GlobalExceptionHandler.class, "handleUpload",
                "stage", "ERROR",
                "errorType", "UploadRejectedException",
                "errorMessage", ex.getMessage());
        return unprocessable(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        FlowLog.warn(log, GlobalExceptionHandler.class, "handleNotFound",
                "stage", "ERROR",
                "errorType", "NotFoundException",
                "errorMessage", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMissingPart(Exception ex) {
        FlowLog.warn(log, GlobalExceptionHandler.class, "handleMissingPart",
                "stage", "ERROR",
                "errorType", ex.getClass().getSimpleName(),
                "errorMessage", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "Required request part or parameter is missing: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        FlowLog.error(log, GlobalExceptionHandler.class, "handleUnexpected", ex,
                "stage", "ERROR",
                "errorType", ex.getClass().getSimpleName(),
                "errorMessage", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred while processing the request."));
    }

    private static ResponseEntity<ErrorResponse> unprocessable(String message) {
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse("UNPROCESSABLE_ENTITY", message));
    }
}
