package chit.tefca.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Filesystem-backed audit evidence store for local/dev use.
 * In production, replace with S3-backed implementation.
 */
public class FileAuditEvidenceStore implements AuditEvidenceStore {

    private static final Logger log = LoggerFactory.getLogger(FileAuditEvidenceStore.class);

    private final Path baseDir;
    private final AtomicReference<String> latestHash = new AtomicReference<>();

    public FileAuditEvidenceStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.error("Failed to create evidence store directory: {}", baseDir, e);
        }
    }

    @Override
    public String store(String eventId, String payload, String previousHash) {
        String chainInput = (previousHash != null ? previousHash : "") + payload;
        String hash = sha256(chainInput);

        Path file = baseDir.resolve(eventId + ".json");
        try {
            String content = String.format("{\"eventId\":\"%s\",\"hash\":\"%s\",\"previousHash\":\"%s\",\"payload\":%s}",
                    eventId, hash, previousHash != null ? previousHash : "", payload);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            latestHash.set(hash);
            log.debug("Stored evidence for eventId={}, hash={}", eventId, hash);
        } catch (IOException e) {
            log.error("Failed to store evidence for eventId={}", eventId, e);
        }
        return hash;
    }

    @Override
    public String retrieve(String eventId) {
        Path file = baseDir.resolve(eventId + ".json");
        try {
            if (Files.exists(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to retrieve evidence for eventId={}", eventId, e);
        }
        return null;
    }

    @Override
    public String getLatestHash() {
        return latestHash.get();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
