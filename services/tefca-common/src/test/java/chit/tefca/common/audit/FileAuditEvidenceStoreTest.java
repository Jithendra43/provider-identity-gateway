package chit.tefca.common.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileAuditEvidenceStoreTest {

    @TempDir
    Path tempDir;

    private FileAuditEvidenceStore store;

    @BeforeEach
    void setUp() {
        store = new FileAuditEvidenceStore(tempDir);
    }

    @Test
    void store_shouldReturnHash() {
        String hash = store.store("evt-1", "{\"test\":\"data\"}", null);
        assertThat(hash).isNotNull().hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void retrieve_shouldReturnStoredContent() {
        store.store("evt-2", "{\"test\":\"data\"}", null);
        String retrieved = store.retrieve("evt-2");
        assertThat(retrieved).contains("evt-2").contains("test").contains("data");
    }

    @Test
    void retrieve_nonExistent_shouldReturnNull() {
        assertThat(store.retrieve("does-not-exist")).isNull();
    }

    @Test
    void hashChain_shouldLinkRecords() {
        String hash1 = store.store("evt-3", "{\"first\":true}", null);
        String hash2 = store.store("evt-4", "{\"second\":true}", hash1);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(store.getLatestHash()).isEqualTo(hash2);

        String evidence2 = store.retrieve("evt-4");
        assertThat(evidence2).contains(hash1); // should reference previous hash
    }

    @Test
    void getLatestHash_shouldTrackMostRecent() {
        assertThat(store.getLatestHash()).isNull();

        store.store("evt-5", "{}", null);
        String firstHash = store.getLatestHash();
        assertThat(firstHash).isNotNull();

        store.store("evt-6", "{}", firstHash);
        assertThat(store.getLatestHash()).isNotEqualTo(firstHash);
    }

    @Test
    void differentPayloads_shouldProduceDifferentHashes() {
        String hash1 = store.store("evt-7", "{\"a\":1}", null);
        // Reset store for same previousHash=null
        FileAuditEvidenceStore store2 = new FileAuditEvidenceStore(tempDir);
        String hash2 = store2.store("evt-8", "{\"b\":2}", null);

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
