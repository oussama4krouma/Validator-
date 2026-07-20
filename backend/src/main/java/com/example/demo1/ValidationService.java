package com.example.demo1;

import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the Mustang {@link ZUGFeRDValidator}.
 *
 * <p>The verdict comes from {@link ZUGFeRDValidator#wasCompletelyValid()} rather than from
 * string-matching the report XML, so it stays correct even if the report format changes in a
 * future Mustang release. The raw report is still returned unchanged for display.</p>
 */
@Service
public class ValidationService {

    public Result validate(byte[] content, String fileName) {
        // ZUGFeRDValidator is stateful (per-run context), so a fresh instance per request.
        // Do NOT share it as a singleton bean.
        ZUGFeRDValidator validator = new ZUGFeRDValidator();

        String report = validator.validate(content, fileName);
        String status = validator.wasCompletelyValid() ? "VALID" : "INVALID";

        return new Result(fileName, status, report);
    }

    /** Immutable result of one validation run. */
    public record Result(String fileName, String status, String report) {
    }
}
