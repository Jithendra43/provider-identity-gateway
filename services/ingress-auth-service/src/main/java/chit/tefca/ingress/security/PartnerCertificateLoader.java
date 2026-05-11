package chit.tefca.ingress.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads trusted partner certificate thumbprints from
 * {@code ingress.partner_certificates} into {@link PartnerTrustStore}.
 *
 * <p>Runs once at boot (after the datasource is ready) and then on a
 * configurable cadence so cert rotations made via SQL or the admin API are
 * picked up without a redeploy. Only certificates with {@code active = true}
 * and {@code not_after &gt; now()} are considered trusted.</p>
 *
 * <p>The loader is gated on {@code tefca.mtls.enabled=true} so disabled
 * environments never even hit the database for this — keeps boot cheap and
 * keeps the unit-test profile from needing schema migrations.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.mtls.enabled", havingValue = "true")
public class PartnerCertificateLoader {

    private static final String TRUSTED_THUMBPRINTS_SQL = """
            SELECT thumbprint
              FROM ingress.partner_certificates
             WHERE active = TRUE
               AND not_after > now()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PartnerTrustStore partnerTrustStore;

    @Value("${tefca.mtls.fail-open-on-load-error:false}")
    private boolean failOpenOnLoadError;

    @PostConstruct
    public void loadOnStartup() {
        log.info("PartnerCertificateLoader: loading partner trust material on startup");
        refresh();
    }

    /**
     * Re-reads the trust store from the database. Default cadence is every
     * 5 minutes — small enough to react to a rotation, large enough not to
     * slam Aurora.
     */
    @Scheduled(fixedDelayString = "${tefca.mtls.refresh-interval-ms:300000}",
               initialDelayString = "${tefca.mtls.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            List<String> rows = jdbcTemplate.queryForList(TRUSTED_THUMBPRINTS_SQL, String.class);
            Set<String> next = new HashSet<>(rows);
            partnerTrustStore.replaceTrustedThumbprints(next);
        } catch (Exception ex) {
            // Don't crash the app — but be loud, and fail closed by default.
            // Operators can flip tefca.mtls.fail-open-on-load-error=true if they
            // want to keep serving the previous in-memory set on a transient
            // DB outage.
            if (failOpenOnLoadError) {
                log.error("PartnerCertificateLoader refresh failed; keeping previous trust set ({} entries)",
                        partnerTrustStore.getTrustedThumbprints().size(), ex);
            } else {
                log.error("PartnerCertificateLoader refresh failed; clearing trust set (fail-closed)", ex);
                partnerTrustStore.replaceTrustedThumbprints(Set.of());
            }
        }
    }
}
