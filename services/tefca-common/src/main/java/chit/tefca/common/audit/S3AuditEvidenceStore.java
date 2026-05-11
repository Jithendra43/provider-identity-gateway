package chit.tefca.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * S3-backed {@link AuditEvidenceStore} implementing a tamper-evident SHA-256 hash chain.
 *
 * <p>Each evidence record is written as a separate S3 object whose key is partitioned by
 * date for efficient lifecycle management and Athena queries:
 * <pre>
 *   {prefix}/{yyyy}/{MM}/{dd}/{eventId}.json
 * </pre>
 *
 * <p>The previous hash is stored both as object metadata ({@code x-amz-meta-prev-hash})
 * and as a marker object ({@code {prefix}/_latest_hash}) which is updated on every write.
 * This lets new records discover the chain head without scanning the bucket.
 *
 * <p>Activated when:
 * <ul>
 *   <li>The AWS S3 SDK is on the classpath</li>
 *   <li>{@code tefca.audit.evidence.s3.enabled=true}</li>
 *   <li>An {@link S3Client} bean is provided by the consuming service</li>
 * </ul>
 *
 * <p>Required properties:
 * <pre>
 *   tefca.audit.evidence.s3.enabled = true
 *   tefca.audit.evidence.s3.bucket  = my-audit-bucket
 *   tefca.audit.evidence.s3.prefix  = audit-evidence  (optional, default "audit-evidence")
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(value = "tefca.audit.evidence.s3.enabled", havingValue = "true")
public class S3AuditEvidenceStore implements AuditEvidenceStore {

    private static final String LATEST_HASH_KEY_SUFFIX = "/_latest_hash";
    private static final String META_PREV_HASH = "prev-hash";
    private static final String META_EVENT_ID = "event-id";
    private static final DateTimeFormatter PARTITION =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3AuditEvidenceStore(
            S3Client s3,
            @Value("${tefca.audit.evidence.s3.bucket}") String bucket,
            @Value("${tefca.audit.evidence.s3.prefix:audit-evidence}") String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        log.info("S3AuditEvidenceStore initialized (bucket={}, prefix={})", bucket, this.prefix);
    }

    @Override
    public String store(String eventId, String payload, String previousHash) {
        String prev = previousHash != null ? previousHash : "";
        String currentHash = sha256(prev + payload);

        String objectKey = "%s/%s/%s.json".formatted(prefix, PARTITION.format(Instant.now()), eventId);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("application/json")
                .metadata(Map.of(
                        META_EVENT_ID, eventId,
                        META_PREV_HASH, prev,
                        "this-hash", currentHash
                ))
                .build();
        s3.putObject(put, RequestBody.fromString(payload, StandardCharsets.UTF_8));

        // Update the chain head marker object. Concurrent writes would race here,
        // but for HIPAA evidence the chain only needs to be verifiable by walking
        // backward via prev-hash metadata — the marker is a convenience.
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(prefix + LATEST_HASH_KEY_SUFFIX)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromString(currentHash, StandardCharsets.UTF_8)
        );

        log.debug("Stored audit evidence: eventId={} hash={} prev={}", eventId, currentHash, prev);
        return currentHash;
    }

    @Override
    public String retrieve(String eventId) {
        // Retrieval scans the date partitions backwards from today. This is
        // acceptable for forensic queries; for high-volume retrieval add a
        // DynamoDB index keyed by eventId.
        Instant now = Instant.now();
        for (int i = 0; i < 30; i++) {
            String day = PARTITION.format(now.minusSeconds(i * 86400L));
            String key = "%s/%s/%s.json".formatted(prefix, day, eventId);
            try (ResponseInputStream<GetObjectResponse> in = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build())) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (NoSuchKeyException ignored) {
                // try next day
            } catch (Exception e) {
                log.error("Error retrieving audit evidence eventId={}: {}", eventId, e.getMessage());
                return null;
            }
        }
        log.warn("Audit evidence not found within 30-day partition scan: eventId={}", eventId);
        return null;
    }

    @Override
    public String getLatestHash() {
        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(prefix + LATEST_HASH_KEY_SUFFIX).build())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            log.error("Error retrieving latest hash: {}", e.getMessage());
            return null;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
