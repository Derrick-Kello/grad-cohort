package edu.university.grps.publication.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.grps.common.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** FR-PUB-10: pre-fills publication metadata from Crossref. Falls back to manual entry on any failure. */
@Component
public class DoiResolver {

    private final RestClient client;

    public DoiResolver() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(4).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.client = RestClient.builder().baseUrl("https://api.crossref.org").requestFactory(factory)
                .defaultHeader("User-Agent", "GRPS/1.0 (mailto:graduate-school@university.edu)").build();
    }

    public record DoiMetadata(String doi, String title, String authors, String venueName, Integer year, String volume,
                              String issue, String pages, String issnIsbn, String type, String url) {}

    public DoiMetadata lookup(String rawDoi) {
        String doi = PublicationService.normaliseDoi(rawDoi);
        if (doi == null || !doi.matches("^10\\.\\d{4,9}/\\S+$")) throw ApiException.validation("Enter a DOI such as 10.1234/abcd.");
        JsonNode msg;
        try {
            JsonNode root = client.get().uri("/works/" + URLEncoder.encode(doi, StandardCharsets.UTF_8).replace("%2F", "/"))
                    .retrieve().body(JsonNode.class);
            msg = root == null ? null : root.path("message");
        } catch (RestClientException e) {
            throw ApiException.notFound("DOI metadata (enter the details manually)");
        }
        if (msg == null || msg.isMissingNode()) throw ApiException.notFound("DOI metadata (enter the details manually)");
        List<String> authors = new ArrayList<>();
        for (JsonNode a : msg.path("author")) {
            String name = (a.path("given").asText("") + " " + a.path("family").asText("")).trim();
            if (!name.isEmpty()) authors.add(name);
        }
        Integer year = null;
        JsonNode parts = msg.path("issued").path("date-parts");
        if (parts.isArray() && parts.size() > 0 && parts.get(0).size() > 0) year = parts.get(0).get(0).asInt();
        String crossrefType = msg.path("type").asText("");
        String type = switch (crossrefType) {
            case "journal-article" -> "JOURNAL_ARTICLE";
            case "proceedings-article" -> "CONFERENCE_PAPER";
            case "book-chapter" -> "BOOK_CHAPTER";
            default -> "OTHER";
        };
        String issn = msg.path("ISSN").isArray() && msg.path("ISSN").size() > 0 ? msg.path("ISSN").get(0).asText()
                : msg.path("ISBN").isArray() && msg.path("ISBN").size() > 0 ? msg.path("ISBN").get(0).asText() : null;
        return new DoiMetadata(doi, first(msg.path("title")), String.join(", ", authors), first(msg.path("container-title")),
                year, text(msg, "volume"), text(msg, "issue"), text(msg, "page"), issn, type, "https://doi.org/" + doi);
    }

    private static String first(JsonNode array) {
        return array.isArray() && array.size() > 0 ? array.get(0).asText() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
