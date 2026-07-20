package com.example.demo1;

/**
 * JSON payload returned by {@code /upload-xml}.
 *
 * @param fileName         the original uploaded filename
 * @param status           "VALID", "INVALID", or "ERROR"
 * @param validationResult the full Mustang XML report (null on error)
 * @param error            a human-readable error message (null on success)
 */
public record ValidationResponse(
        String fileName,
        String status,
        String validationResult,
        String error
) {
}
