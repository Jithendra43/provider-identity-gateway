package chit.tefca.app.fips;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;

/**
 * Logs the registered JCA / JSSE provider stack at startup and asserts that
 * BC-FIPS is present. The verdict is also exposed as a structured log line
 * (FIPS_STATUS …) that the deploy pipeline greps for as a post-deploy gate.
 *
 * <p>Activated whenever the {@code TEFCA_FIPS_ENABLED} environment variable is
 * {@code true} (set by Terraform on the ECS task definition).</p>
 */
@Slf4j
@Component
public class FipsSelfTestRunner {

    @EventListener(ApplicationReadyEvent.class)
    public void verifyFipsProviders() {
        log.info("=== FIPS provider self-test ===");

        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            Provider p = providers[i];
            log.info("  [{}] {} v{} — {}", i + 1, p.getName(), p.getVersionStr(), p.getInfo());
        }

        boolean bcfipsPresent = false;
        boolean bcjssePresent = false;
        for (Provider p : providers) {
            if ("BCFIPS".equals(p.getName())) {
                bcfipsPresent = true;
            }
            if ("BCJSSE".equals(p.getName())) {
                bcjssePresent = true;
            }
        }

        boolean approvedOnly = "true".equalsIgnoreCase(
                System.getProperty("org.bouncycastle.fips.approved_only"));
        boolean envFipsFlag = "true".equalsIgnoreCase(System.getenv("TEFCA_FIPS_ENABLED"));

        String preferred = providers.length > 0 ? providers[0].getName() : "<none>";
        String strongAlg;
        try {
            strongAlg = SecureRandom.getInstanceStrong().getAlgorithm()
                    + "/" + SecureRandom.getInstanceStrong().getProvider().getName();
        } catch (Exception e) {
            strongAlg = "<unavailable: " + e.getMessage() + ">";
        }

        log.info("FIPS_STATUS bcfipsRegistered={} bcjsseRegistered={} approvedOnlyMode={} "
                        + "envFlag={} preferredProvider={} strongRandom={}",
                bcfipsPresent, bcjssePresent, approvedOnly, envFipsFlag, preferred, strongAlg);

        if (envFipsFlag && !bcfipsPresent) {
            log.error("FIPS_STATUS FAILED — TEFCA_FIPS_ENABLED=true but BCFIPS provider is "
                    + "not registered. Verify -Djava.security.properties=/app/fips.security "
                    + "and that bc-fips is on the classpath.");
        }
    }
}
