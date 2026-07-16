package dev.modcheck.modcheck.web;

import dev.modcheck.modcheck.service.CheckReportService;
import dev.modcheck.modcheck.service.CheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CheckController {

    private final CheckService checkService;
    private final CheckReportService checkReportService;

    @PostMapping("/check")
    public CheckService.IngestResult check(@RequestBody CheckRequest request) {
        return checkService.ingestCollection(request.collectionSlug());
    }


    @GetMapping("/check/{id}/report")
    public CheckReportService.CheckReport report(@PathVariable Long id) {
        return checkReportService.getReport(id);
    }

    public record CheckRequest(String collectionSlug) {}
}
