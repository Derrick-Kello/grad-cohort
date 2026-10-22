package edu.university.grps.storage;

import edu.university.grps.common.ApiException;
import edu.university.grps.common.ErrorCode;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Shared upload validation: size limit, content-based type check and virus scan before storage. */
@Component
public class UploadChecks {

    public record Checked(byte[] bytes, FileTypes type, String fileName, String sha256, boolean pendingScan) {}

    private final VirusScanner scanner;

    public UploadChecks(VirusScanner scanner) {
        this.scanner = scanner;
    }

    public Checked check(MultipartFile file, Set<FileTypes> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) throw ApiException.validation("Choose a file to upload.");
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "The file is larger than the " + (maxBytes / (1024 * 1024)) + " MB limit.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("The file could not be read.");
        }
        FileTypes type = FileTypes.detect(bytes);
        if (type == null || !allowed.contains(type)) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "This file type is not accepted. Allowed: "
                    + String.join(", ", allowed.stream().map(t -> t.extension().toUpperCase()).sorted().toList()) + ".");
        }
        VirusScanner.Result scan = scanner.scan(bytes);
        if (scan == VirusScanner.Result.INFECTED) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "The virus scan flagged this file. It was not stored.");
        }
        String name = cleanName(file.getOriginalFilename(), type);
        return new Checked(bytes, type, name, sha256(bytes), scan == VirusScanner.Result.UNAVAILABLE);
    }

    static String cleanName(String original, FileTypes type) {
        String base = original == null ? "file" : original.replaceAll("[\\\\/]", "_").replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (base.isEmpty()) base = "file";
        if (base.length() > 200) base = base.substring(0, 200);
        if (!base.toLowerCase().endsWith("." + type.extension()) && !(type == FileTypes.JPG && base.toLowerCase().endsWith(".jpeg"))) {
            base = base + "." + type.extension();
        }
        return base;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
