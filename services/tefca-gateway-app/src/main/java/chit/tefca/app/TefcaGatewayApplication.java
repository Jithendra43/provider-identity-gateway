package chit.tefca.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Provider;
import java.security.Security;

/**
 * Single-deployable entrypoint for the cost-min AWS Fargate prod profile.
 *
 * <p>This Spring Boot application loads the @Configuration / @ComponentScan tree
 * of all four TEFCA Gateway services (ingress-auth, policy, routing,
 * directory-cache) into <strong>one</strong> Spring context, deployed as a
 * single fat-jar in a single Fargate task. Cross-service HTTP calls
 * (PolicyServiceClient, RoutingServiceClient, etc.) target
 * <code>http://localhost:8080</code> per <code>application-prod.yml</code>,
 * keeping all traffic on the loopback interface — zero network egress, zero
 * extra task cost.
 *
 * <p>The legacy per-service @SpringBootApplication classes
 * (IngressAuthApplication, PolicyServiceApplication, etc.) remain unchanged so
 * each module can still be unit-tested in isolation; they are picked up here
 * as ordinary @Configuration sources via component scan.
 */
@SpringBootApplication(scanBasePackages = "chit.tefca")
@EnableJpaRepositories(basePackages = "chit.tefca")
@EntityScan(basePackages = "chit.tefca")
@ComponentScan(basePackages = "chit.tefca")
@EnableScheduling
@EnableAsync
public class TefcaGatewayApplication {

    public static void main(String[] args) {
        registerFipsProviders();
        SpringApplication.run(TefcaGatewayApplication.class, args);
    }

    /**
     * Registers BouncyCastle FIPS providers programmatically before Spring
     * starts. Required because in a Spring Boot fat-jar layout
     * {@code BOOT-INF/lib/*.jar} entries are NOT on the system classpath at
     * JVM init, so {@code security.provider.N=org.bouncycastle...} entries in
     * {@code java.security} are silently skipped (class not found). Activated
     * only when {@code TEFCA_FIPS_ENABLED=true}.
     */
    private static void registerFipsProviders() {
        if (!"true".equalsIgnoreCase(System.getenv("TEFCA_FIPS_ENABLED"))) {
            return;
        }
        try {
            Class<?> bcFipsCls = Class.forName(
                    "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider");
            Provider bcFips = (Provider) bcFipsCls.getDeclaredConstructor().newInstance();
            Security.insertProviderAt(bcFips, 1);

            Class<?> bcJsseCls = Class.forName(
                    "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider");
            Provider bcJsse = (Provider) bcJsseCls
                    .getDeclaredConstructor(String.class)
                    .newInstance("fips:BCFIPS");
            Security.insertProviderAt(bcJsse, 2);

            System.out.println("[FIPS] Registered BCFIPS + BCJSSE providers (slots 1,2)");
        } catch (Throwable t) {
            System.err.println("[FIPS] Failed to register BC-FIPS providers: " + t);
            t.printStackTrace();
        }
    }
}
