package dev.modcheck.modcheck.web;

import dev.modcheck.modcheck.service.CheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckController {

    private final CheckService checkService;

    @PostMapping("/check")
    public CheckService.IngestResult check(@RequestBody CheckRequest request) {
        return checkService.ingestCollection(request.collectionSlug());
    }

    public record CheckRequest(String collectionSlug) {}
}
