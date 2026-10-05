package io.github.dgviz.web.api;

import io.github.dgviz.scan.ScanReport;
import io.github.dgviz.scan.ScanRequest;
import io.github.dgviz.scan.ScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class ScanApiController {

    private final ScanService scanService;

    public ScanApiController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping(value = "/{id}/scan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ScanReport scan(
            @PathVariable long id,
            @RequestHeader(value = "X-DGViz-Token", required = false) String token,
            @RequestBody ScanRequest request
    ) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Missing X-DGViz-Token header");
        }
        return scanService.scan(id, token.trim(), request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> unauthorized(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
