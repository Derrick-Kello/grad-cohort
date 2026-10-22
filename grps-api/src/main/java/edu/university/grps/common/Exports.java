package edu.university.grps.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** CSV and PDF export responses chosen by {@code format} parameter or Accept header (FR-RPT-08, FR-ATT-09). */
public final class Exports {

    private Exports() {}

    public enum Format { JSON, CSV, PDF }

    public static Format resolve(String format, String accept) {
        if (format != null) {
            if (format.equalsIgnoreCase("csv")) return Format.CSV;
            if (format.equalsIgnoreCase("pdf")) return Format.PDF;
        }
        if (accept != null) {
            if (accept.contains("text/csv")) return Format.CSV;
            if (accept.contains("application/pdf")) return Format.PDF;
        }
        return Format.JSON;
    }

    public static ResponseEntity<byte[]> file(Format format, String baseName, String title, List<String> subtitle,
                                              List<String> header, List<List<String>> rows) {
        if (format == Format.PDF) {
            byte[] pdf = PdfTables.render(title, subtitle, header, rows);
            return download(pdf, baseName + ".pdf", MediaType.APPLICATION_PDF);
        }
        byte[] csv = ("﻿" + Csv.write(header, rows)).getBytes(StandardCharsets.UTF_8);
        return download(csv, baseName + ".csv", MediaType.parseMediaType("text/csv; charset=utf-8"));
    }

    private static ResponseEntity<byte[]> download(byte[] body, String fileName, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(type).body(body);
    }
}
