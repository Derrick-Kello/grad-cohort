package edu.university.grps.storage;

import edu.university.grps.common.GrpsProperties;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VirusScannerConfig {

    private static final Logger log = LoggerFactory.getLogger(VirusScannerConfig.class);
    static final String EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";

    @Bean
    public VirusScanner virusScanner(GrpsProperties props) {
        if ("clamav".equalsIgnoreCase(props.virusScan().mode())) {
            return new ClamAvScanner(props.virusScan().host(), props.virusScan().port());
        }
        log.info("Virus scanning uses the stub scanner (flags only the EICAR test string).");
        return content -> new String(content, StandardCharsets.ISO_8859_1).contains(EICAR_MARKER)
                ? VirusScanner.Result.INFECTED : VirusScanner.Result.CLEAN;
    }

    /** clamd INSTREAM protocol over TCP. */
    static class ClamAvScanner implements VirusScanner {

        private static final int CHUNK = 64 * 1024;
        private final String host;
        private final int port;

        ClamAvScanner(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public Result scan(byte[] content) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
                socket.setSoTimeout(60_000);
                OutputStream out = socket.getOutputStream();
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                for (int offset = 0; offset < content.length; offset += CHUNK) {
                    int len = Math.min(CHUNK, content.length - offset);
                    out.write(ByteBuffer.allocate(4).putInt(len).array());
                    out.write(content, offset, len);
                }
                out.write(new byte[] {0, 0, 0, 0});
                out.flush();
                byte[] reply = new DataInputStream(socket.getInputStream()).readAllBytes();
                String response = new String(reply, StandardCharsets.US_ASCII).trim();
                if (response.endsWith("OK")) return Result.CLEAN;
                if (response.contains("FOUND")) return Result.INFECTED;
                log.warn("Unexpected clamd reply: {}", response);
                return Result.UNAVAILABLE;
            } catch (IOException e) {
                log.warn("ClamAV unavailable at {}:{}: {}", host, port, e.getMessage());
                return Result.UNAVAILABLE;
            }
        }
    }
}
