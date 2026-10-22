package edu.university.grps.storage;

import edu.university.grps.common.ApiException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Serves files only through valid, unexpired signed links. */
@RestController
public class FileDownloadController {

    private final SignedLinks links;
    private final FileStore store;

    public FileDownloadController(SignedLinks links, FileStore store) {
        this.links = links;
        this.store = store;
    }

    @GetMapping("/api/v1/files/{token}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String token) {
        SignedLinks.Payload payload = links.verify(token);
        if (payload == null || !store.exists(payload.key())) throw ApiException.notFound("File link");
        ContentDisposition disposition = (payload.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(payload.fileName(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .body(new InputStreamResource(store.get(payload.key())));
    }
}
