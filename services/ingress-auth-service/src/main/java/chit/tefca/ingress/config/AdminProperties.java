package chit.tefca.ingress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "tefca.admin")
public class AdminProperties {

    /** "mock" (uses mockTokenUrl) or "oidc" (production). */
    private String authMode = "mock";

    private String mockTokenUrl = "http://localhost:8090/token";

    private String cookieName = "tefca_admin_token";

    private boolean cookieSecure = false;

    /**
     * SameSite policy for the admin cookie. {@code Strict} is the production
     * default and prevents the cookie from being sent on any cross-site
     * request (mitigates CSRF and OAuth-style cross-site logins). {@code Lax}
     * permits top-level GET navigation and is acceptable for SPAs hosted on
     * the same origin.
     */
    private String cookieSameSite = "Strict";

    private long cookieMaxAgeSeconds = 7200;

    private String requiredRole = "QHIN_ADMIN";

    /** Pre-provisioned dev operators. Keyed by Operator.username. */
    private java.util.List<Operator> operators = new java.util.ArrayList<>();

    private Proxy proxy = new Proxy();

    /**
     * Identity used when an admin Cognito session (OAuth2AuthenticationToken)
     * invokes a /api/v1/tefca/** endpoint via the Test Console. Cognito ID
     * tokens carry no orgId/nodeId/roles claims, so {@link chit.tefca.ingress.filter.JwtAuthenticationFilter}
     * does not populate a {@code RequesterIdentity}; without this fallback
     * the orchestrator would forward {@code "unknown"} to the policy engine
     * and every TREATMENT request from the Test Console would be denied by
     * {@code RequesterOrgValidator}.
     */
    private TestHarness testHarness = new TestHarness();

    @Data
    public static class Proxy {
        /** service-name -> base URL. */
        private Map<String, String> services = new HashMap<>();
    }

    @Data
    public static class TestHarness {
        /** Org id presented to the policy engine for admin-console requests. Must be ACTIVE in directory. */
        private String orgId = "ORG-ADMIN-CONSOLE";
        /** Node id presented to the policy engine for admin-console requests. */
        private String nodeId = "NODE-ADMIN-CONSOLE";
        /** Roles asserted on behalf of the admin Cognito session. */
        private java.util.List<String> roles = new java.util.ArrayList<>(java.util.List.of("QHIN_ADMIN"));
    }

    @Data
    public static class Operator {
        private String username;
        /** Plaintext password for dev. Replace with hash + real IdP in prod. */
        private String password;
        private String orgId;
        private String nodeId;
        /** Comma-separated roles, e.g. "QHIN_ADMIN". */
        private String roles;
        private String displayName;
    }
}
