package com.researchagent.controller;

import com.researchagent.dto.*;
import com.researchagent.entity.ClaimEntity;
import com.researchagent.entity.SourceEntity;
import com.researchagent.service.PdfReportService;
import com.researchagent.service.RedisSseBridgeService;
import com.researchagent.service.RunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/runs")
@CrossOrigin(origins = "*")
public class RunController {

    private final RunService runService;
    private final RedisSseBridgeService sseBridgeService;
    private final PdfReportService pdfReportService;

    public RunController(
            RunService runService,
            RedisSseBridgeService sseBridgeService,
            PdfReportService pdfReportService) {
        this.runService = runService;
        this.sseBridgeService = sseBridgeService;
        this.pdfReportService = pdfReportService;
    }

    /**
     * POST /api/runs
     * Enqueues a new research job.
     */
    @PostMapping
    public ResponseEntity<RunResponse> createRun(
            @Valid @RequestBody CreateRunRequest request,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        RunResponse response = runService.createAndEnqueueRun(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/runs
     * Lists past research runs with pagination.
     */
    @GetMapping
    public ResponseEntity<List<RunResponse>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(runService.listRuns(page, size));
    }

    /**
     * GET /api/runs/{id}
     * Retrieves run metadata, status, cost, and tokens.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> getRun(@PathVariable String id) {
        return ResponseEntity.ok(runService.getRun(id));
    }

    /**
     * GET /api/runs/{id}/stream
     * Server-Sent Events endpoint streaming live agent thought and progress events.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRunEvents(@PathVariable String id) {
        return sseBridgeService.registerClient(id);
    }

    /**
     * POST /api/runs/{id}/cancel
     * Halts research agent execution immediately.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<RunResponse> cancelRun(@PathVariable String id) {
        return ResponseEntity.ok(runService.cancelRun(id));
    }

    /**
     * GET /api/runs/{id}/report
     * Returns final Markdown report, confidence score, and draft diff if revised.
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String id) {
        return ResponseEntity.ok(runService.getReport(id));
    }

    /**
     * GET /api/runs/{id}/trace
     * Returns full step-by-step trace with prompt, LLM response, tokens, and duration.
     */
    @GetMapping("/{id}/trace")
    public ResponseEntity<List<StepResponse>> getTrace(@PathVariable String id) {
        return ResponseEntity.ok(runService.getSteps(id));
    }

    /**
     * GET /api/runs/{id}/sources
     * Returns discovered candidate web sources and extracted content status.
     */
    @GetMapping("/{id}/sources")
    public ResponseEntity<List<SourceEntity>> getSources(@PathVariable String id) {
        return ResponseEntity.ok(runService.getSources(id));
    }

    /**
     * GET /api/runs/{id}/claims
     * Returns verified and unverified claims with cross-domain corroboration notes.
     */
    @GetMapping("/{id}/claims")
    public ResponseEntity<List<ClaimEntity>> getClaims(@PathVariable String id) {
        return ResponseEntity.ok(runService.getClaims(id));
    }

    /**
     * GET /api/runs/{id}/pdf
     * Generates and downloads a publication-grade PDF report.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        ReportResponse report = runService.getReport(id);
        byte[] pdfBytes = pdfReportService.generatePdfBytes(report);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "research-brief-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
