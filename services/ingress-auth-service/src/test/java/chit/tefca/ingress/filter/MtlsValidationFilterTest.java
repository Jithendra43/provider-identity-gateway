package chit.tefca.ingress.filter;

import chit.tefca.ingress.security.PartnerTrustStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MtlsValidationFilterTest {

    @Mock
    private PartnerTrustStore partnerTrustStore;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private MtlsValidationFilter filter;

    @Test
    void trustedCert_shouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        request.addHeader("X-Client-Cert", "CERT_DATA");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(partnerTrustStore.isTrustedCertificate("CERT_DATA")).thenReturn(true);
        when(partnerTrustStore.extractThumbprint("CERT_DATA")).thenReturn("abc123");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void untrustedCert_shouldReturn403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        request.addHeader("X-Client-Cert", "BAD_CERT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(partnerTrustStore.isTrustedCertificate("BAD_CERT")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("UNTRUSTED_CERTIFICATE");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void noCert_shouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void actuatorPath_shouldNotFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    // ── Strict-mode (production) enforcement ────────────────────────────────

    @Test
    void strictMode_missingCert_shouldReturn401MtlsRequired() throws Exception {
        ReflectionTestUtils.setField(filter, "strict", true);
        when(partnerTrustStore.isMtlsEnabled()).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MTLS_REQUIRED");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void strictMode_validCert_shouldPass() throws Exception {
        ReflectionTestUtils.setField(filter, "strict", true);
        when(partnerTrustStore.isMtlsEnabled()).thenReturn(true);
        when(partnerTrustStore.isTrustedCertificate("PEM")).thenReturn(true);
        when(partnerTrustStore.extractThumbprint("PEM")).thenReturn("abc");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        request.addHeader("X-Client-Cert", "PEM");
        request.addHeader("X-Client-Verify", "SUCCESS");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void sidecarReportedFailure_shouldReturn403_evenWithCert() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        request.addHeader("X-Client-Cert", "PEM");
        request.addHeader("X-Client-Verify", "FAILED:expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("MTLS_HANDSHAKE_FAILED");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void permissiveMode_missingCert_shouldStillPassThrough() throws Exception {
        ReflectionTestUtils.setField(filter, "strict", false);
        when(partnerTrustStore.isMtlsEnabled()).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
