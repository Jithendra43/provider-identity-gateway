package chit.tefca.common.security;

/**
 * Security-related constants shared across gateway services.
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    // Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String MTLS_CLIENT_CERT_HEADER = "X-Client-Cert";
    /** AWS ALB mTLS passthrough mode: full URL-encoded chain PEM. */
    public static final String MTLS_CLIENT_CERT_HEADER_ALB = "X-Amzn-Mtls-Clientcert";
    /** AWS ALB mTLS verify mode: URL-encoded leaf cert PEM. */
    public static final String MTLS_CLIENT_CERT_HEADER_ALB_LEAF = "X-Amzn-Mtls-Clientcert-Leaf";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    // JWT Claims
    public static final String CLAIM_ORG_ID = "org_id";
    public static final String CLAIM_NODE_ID = "node_id";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SCOPES = "scope";

    // API paths
    public static final String API_V1_PREFIX = "/api/v1";
    public static final String TEFCA_PREFIX = API_V1_PREFIX + "/tefca";
    public static final String ADMIN_PREFIX = API_V1_PREFIX + "/admin";
    public static final String ACTUATOR_PREFIX = "/actuator";

    // Rate limit
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 100;
    public static final int MAX_REQUEST_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
}
