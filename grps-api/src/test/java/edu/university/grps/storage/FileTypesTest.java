package edu.university.grps.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileTypesTest {

    @Test
    void SEC_06_type_is_detected_from_content_not_extension() {
        assertThat(FileTypes.detect("%PDF-1.4 rest".getBytes(StandardCharsets.US_ASCII))).isEqualTo(FileTypes.PDF);
        assertThat(FileTypes.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo(FileTypes.PNG);
        assertThat(FileTypes.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0})).isEqualTo(FileTypes.JPG);
        assertThat(FileTypes.detect("MZ executable pretending".getBytes(StandardCharsets.US_ASCII))).isNull();
    }

    @Test
    void file_names_are_cleaned_and_get_the_detected_extension() {
        assertThat(UploadChecks.cleanName("../../etc/passwd", FileTypes.PDF)).isEqualTo(".._.._etc_passwd.pdf");
        assertThat(UploadChecks.cleanName("slides.pptx", FileTypes.PPTX)).isEqualTo("slides.pptx");
    }
}
