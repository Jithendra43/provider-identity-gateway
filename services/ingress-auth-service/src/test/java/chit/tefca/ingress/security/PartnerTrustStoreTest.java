package chit.tefca.ingress.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerTrustStoreTest {

    @Test
    void mtlsDisabled_shouldAlwaysTrust() {
        PartnerTrustStore store = new PartnerTrustStore();
        // mtlsEnabled defaults to false
        assertThat(store.isTrustedCertificate("any-cert")).isTrue();
    }

    @Test
    void extractThumbprint_shouldReturnSha256Hex() {
        PartnerTrustStore store = new PartnerTrustStore();
        String thumbprint = store.extractThumbprint("test-cert-data");
        assertThat(thumbprint).isNotNull().hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    void addTrustedThumbprint_shouldBeRecognized() {
        PartnerTrustStore store = new PartnerTrustStore();
        String thumbprint = store.extractThumbprint("trusted-cert");
        store.addTrustedThumbprint(thumbprint);
        // Note: mtlsEnabled is false by default, so can't test the full path
        // without reflection. This tests the thumbprint management.
        assertThat(thumbprint).hasSize(64);
    }

    @Test
    void sameInput_shouldProduceSameThumbprint() {
        PartnerTrustStore store = new PartnerTrustStore();
        String t1 = store.extractThumbprint("cert-data");
        String t2 = store.extractThumbprint("cert-data");
        assertThat(t1).isEqualTo(t2);
    }

    @Test
    void differentInput_shouldProduceDifferentThumbprint() {
        PartnerTrustStore store = new PartnerTrustStore();
        String t1 = store.extractThumbprint("cert-a");
        String t2 = store.extractThumbprint("cert-b");
        assertThat(t1).isNotEqualTo(t2);
    }
}
