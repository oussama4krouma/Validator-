package com.example.demo1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST entry point for the XRechnung validator.
 *
 * <p>{@code POST /upload-xml} -> JSON with the verdict and the full Mustang report.</p>
 *
 * CORS is configured globally in {@link WebConfig}.
 */
@RestController
public class XmlUploadController {

    private static final Logger log = LoggerFactory.getLogger(XmlUploadController.class);

    private final ValidationService validationService;

    public XmlUploadController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(value = "/upload-xml",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResponse> uploadXml(@RequestParam("file") MultipartFile file) {
        String fileName = resolveName(file);

        String rejection = reject(file, fileName);
        if (rejection != null) {
            return ResponseEntity.badRequest()
                    .body(new ValidationResponse(fileName, "ERROR", null, rejection));
        }

        try {
            ValidationService.Result result = validationService.validate(file.getBytes(), fileName);
            return ResponseEntity.ok(
                    new ValidationResponse(fileName, result.status(), result.report(), null));
        } catch (IOException e) {
            log.error("Could not read uploaded file {}", fileName, e);
            return ResponseEntity.internalServerError()
                    .body(new ValidationResponse(fileName, "ERROR", null, "File read error"));
        } catch (Exception e) {
            log.error("Validation failed for {}", fileName, e);
            return ResponseEntity.internalServerError()
                    .body(new ValidationResponse(fileName, "ERROR", null, "Validator error"));
        }
    }

    /** Never trust the client filename to be present; fall back to a neutral default. */
    private String resolveName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            return "unknown.xml";
        }
        return file.getOriginalFilename();
    }

    /** Returns an error message if the upload should be rejected, or {@code null} if acceptable. */
    private String reject(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            return "File is empty";
        }
        if (!fileName.toLowerCase().endsWith(".xml")) {
            return "Only XML files are supported";
        }
        return null;
    }
}
