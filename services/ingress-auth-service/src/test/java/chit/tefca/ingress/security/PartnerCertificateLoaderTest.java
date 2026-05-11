package chit.tefca.ingress.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link PartnerCertificateLoader} hydrates and refreshes
 * {@link PartnerTrustStore} from {@code ingress.partner_certificates}.
 */
@ExtendWith(MockitoExtension.class)
class PartnerCertificateLoaderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PartnerTrustStore trustStore;

    @InjectMocks
    private PartnerCertificateLoader loader;

    @BeforeEach
    void setUp() {
        trustStore = new PartnerTrustStore();
        ReflectionTestUtils.setField(trustStore, "mtlsEnabled", true);
        ReflectionTestUtils.setField(loader, "partnerTrustStore", trustStore);
    }

    @Test
    void loadOnStartup_populatesTrustStoreFromDb() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                        "1122334455667788990011223344556677889900112233445566778899001122"));

        loader.loadOnStartup();

        assertThat(trustStore.getTrustedThumbprints())
                .hasSize(2)
                .allMatch(t -> t.length() == 64);
    }

    @Test
    void refresh_replacesPriorEntries_atomically() {
        // Seed initial set
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("aaa".repeat(22).substring(0, 64)));
        loader.refresh();
        assertThat(trustStore.getTrustedThumbprints()).hasSize(1);

        // Next refresh returns 3 different ones — old one must vanish.
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "bbb".repeat(22).substring(0, 64),
                        "ccc".repeat(22).substring(0, 64),
                        "ddd".repeat(22).substring(0, 64)));
        loader.refresh();

        assertThat(trustStore.getTrustedThumbprints())
                .hasSize(3)
                .doesNotContain("aaa".repeat(22).substring(0, 64));
    }

    @Test
    void refresh_dbFailureFailsClosed_byDefault() {
        // Pre-populate trust store
        trustStore.addTrustedThumbprint("preexisting".repeat(6).substring(0, 64));
        assertThat(trustStore.getTrustedThumbprints()).hasSize(1);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("aurora down"));

        loader.refresh();

        // Default fail-open-on-load-error=false → trust set is cleared (fail-closed).
        assertThat(trustStore.getTrustedThumbprints()).isEmpty();
    }

    @Test
    void refresh_dbFailureKeepsPriorSet_whenFailOpen() {
        ReflectionTestUtils.setField(loader, "failOpenOnLoadError", true);
        trustStore.addTrustedThumbprint("preexisting".repeat(6).substring(0, 64));

        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("aurora down"));

        loader.refresh();

        assertThat(trustStore.getTrustedThumbprints()).hasSize(1);
    }

    @Test
    void normalisesThumbprints_caseAndWhitespace() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("  ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890  "));

        loader.refresh();

        assertThat(trustStore.getTrustedThumbprints())
                .containsExactly("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
    }
}
