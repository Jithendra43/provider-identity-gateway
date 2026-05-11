package chit.tefca.ingress.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that the trust store extracts SHA-256 fingerprints in the
 * exact format produced by {@code openssl x509 -fingerprint -sha256 -noout}
 * — i.e. lowercase hex, 64 chars, no colons, computed over the DER encoding.
 */
class PartnerTrustStoreCertParsingTest {

    private static final String TEST_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDLTCCAhWgAwIBAgIURkKlkycO1qLQEgRbDcmzvDWmGq8wDQYJKoZIhvcNAQEL
            BQAwJTETMBEGA1UEAwwKdGVmY2EtdGVzdDEOMAwGA1UECgwFTG9jYWwwIBcNMjYw
            NDI2MjEwMDI3WhgPMjEyNjA0MDIyMTAwMjdaMCUxEzARBgNVBAMMCnRlZmNhLXRl
            c3QxDjAMBgNVBAoMBUxvY2FsMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
            AQEAs1gyD8VsWcwGlc2I9hr8TY5EXHxjNcjjcjqx7lX5TbNbFQ0jUXndROB0nv3l
            8bSFPUKRJyN1jO+wZr68Uux9g8UWQCVLBzz92WteUukYifVjlJeX76eahxOUp886
            AcabrN1thKZP93qYAbOh3oIHocypApzMJ4QOAlRJLmOyscId8My+CTx0iQ6Bw/Hw
            H8BZCl8TTPQQEx1Sg8opThHkXdlLUyvBxsJWThLJg3M9d8ueGqU3Tc2mO4dApCqq
            CXN7c1kJQhjxqvS0Qa6N4NBmMEoF8lRchaD8dMA72Bu+Bc6njYkOO7A1H7qBuzPi
            MYAufsmVr7kyWtK7fvD1XfwiQwIDAQABo1MwUTAdBgNVHQ4EFgQUaniUUEBeWFUm
            v4K5ssqFh4y5XN8wHwYDVR0jBBgwFoAUaniUUEBeWFUmv4K5ssqFh4y5XN8wDwYD
            VR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAg+nO3PW2HMkEMBYwaIdL
            YrLLwqOZmApJ0YBBubTRSSYmXIsBmW3T4VSc0smIf6uRGWjl3xljTn2b7Q8N2dNQ
            OL7fnqefyVqg8d8J5YnRBXhFxSRukFC4TF0SAzk9TXc12/PqzXG9m5pNDnm4u70X
            YbYlMjgJ8yu74RAUrjQKCoPc4SvsRS1EooA9S3/lxYyn0rBh6r13X+MYWtUyRj46
            HoQ/DoEFooA/PoAns9Jmu8rCSJPnXpDrhhFMql2V+NpwLisI5/NI4RulkiI4vcsz
            YmIa11K31G6pW3w0+H/yJvvtXCINi+FetZtzAOoYF7yiJwawy+PwSbzMTrpjnVa7
            yg==
            -----END CERTIFICATE-----
            """;

    private static final String EXPECTED_SHA256 =
            "952aed65667632af58b0de2451e19646a2f512ec7a604cc98a5be24b13075b5f";

    @Test
    void extractThumbprint_fromRealPem_matchesOpensslSha256() {
        PartnerTrustStore store = new PartnerTrustStore();
        ReflectionTestUtils.setField(store, "mtlsEnabled", true);
        assertThat(store.extractThumbprint(TEST_PEM)).isEqualTo(EXPECTED_SHA256);
    }

    @Test
    void extractThumbprint_fromUrlEncodedPem_matchesPlainPem() {
        String urlEncoded = URLEncoder.encode(TEST_PEM, StandardCharsets.UTF_8);
        PartnerTrustStore store = new PartnerTrustStore();
        assertThat(store.extractThumbprint(urlEncoded)).isEqualTo(EXPECTED_SHA256);
    }

    @Test
    void isTrustedCertificate_whenThumbprintLoaded_acceptsRealCert() {
        PartnerTrustStore store = new PartnerTrustStore();
        ReflectionTestUtils.setField(store, "mtlsEnabled", true);
        store.addTrustedThumbprint(EXPECTED_SHA256);
        assertThat(store.isTrustedCertificate(TEST_PEM)).isTrue();
    }

    @Test
    void isTrustedCertificate_whenThumbprintMissing_rejects() {
        PartnerTrustStore store = new PartnerTrustStore();
        ReflectionTestUtils.setField(store, "mtlsEnabled", true);
        store.addTrustedThumbprint("0".repeat(64));
        assertThat(store.isTrustedCertificate(TEST_PEM)).isFalse();
    }

    @Test
    void isTrustedCertificate_whenMtlsDisabled_alwaysTrue() {
        PartnerTrustStore store = new PartnerTrustStore();
        assertThat(store.isTrustedCertificate(TEST_PEM)).isTrue();
    }
}
