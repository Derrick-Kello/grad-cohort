package edu.university.grps.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Detects a file's type from its content, not its extension (SEC-06). */
public enum FileTypes {
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
    JPG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String mime;
    private final String extension;

    FileTypes(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() {
        return mime;
    }

    public String extension() {
        return extension;
    }

    public boolean previewable() {
        return this == PDF || this == JPG || this == PNG;
    }

    public static FileTypes detect(byte[] b) {
        if (b == null || b.length < 8) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return PDF;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return PNG;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return JPG;
        if (b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) return officeType(b);
        return null;
    }

    private static FileTypes officeType(byte[] b) {
        boolean contentTypes = false;
        FileTypes found = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(b))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < 2000) {
                String name = entry.getName();
                if (name.equals("[Content_Types].xml")) contentTypes = true;
                if (name.equals("word/document.xml")) found = DOCX;
                if (name.equals("ppt/presentation.xml")) found = PPTX;
            }
        } catch (IOException e) {
            return null;
        }
        return contentTypes ? found : null;
    }
}
