package chit.tefca.ingress.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Builds the mTLS material used for OUTBOUND HTTPS calls — partner QHIN
 * forwarding and inter-service communication when {@code tefca.mtls.enabled=true}.
 *
 * <p>Inbound mTLS termination is handled at the load balancer (ALB) which forwards
 * the validated client certificate to the application via the {@code X-Client-Cert}
 * header — see {@code MtlsValidationFilter}.
 *
 * <p>Configuration properties:
 * <pre>
 *   tefca.mtls.enabled              boolean, default false
 *   tefca.mtls.keystore.path        classpath: or file: URI to PKCS12 keystore
 *   tefca.mtls.keystore.password    keystore password
 *   tefca.mtls.keystore.type        PKCS12 (default) | JKS
 *   tefca.mtls.truststore.path      classpath: or file: URI to PKCS12 truststore
 *   tefca.mtls.truststore.password  truststore password
 *   tefca.mtls.truststore.type      PKCS12 (default) | JKS
 * </pre>
 *
 * <p>In AWS, the keystore/truststore should be retrieved from Secrets Manager and
 * written to a temp file by the container entrypoint, then referenced via {@code
 * file:/var/run/secrets/...}.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "tefca.mtls.outbound.enabled", havingValue = "true")
public class TlsConfig {

    @Value("${tefca.mtls.keystore.path}")
    private Resource keystoreResource;

    @Value("${tefca.mtls.keystore.password}")
    private String keystorePassword;

    @Value("${tefca.mtls.keystore.type:PKCS12}")
    private String keystoreType;

    @Value("${tefca.mtls.truststore.path}")
    private Resource truststoreResource;

    @Value("${tefca.mtls.truststore.password}")
    private String truststorePassword;

    @Value("${tefca.mtls.truststore.type:PKCS12}")
    private String truststoreType;

    /**
     * Reactor-Netty SslContext built from the configured keystore + truststore.
     */
    @Bean
    public SslContext partnerSslContext() throws Exception {
        KeyStore keyStore = loadKeyStore(keystoreResource, keystorePassword, keystoreType);
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        KeyStore trustStore = loadKeyStore(truststoreResource, truststorePassword, truststoreType);
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        log.info("Built partner SslContext (keystore={}, truststore={})",
                keystoreResource.getDescription(), truststoreResource.getDescription());

        return SslContextBuilder.forClient()
                .keyManager(kmf)
                .trustManager(tmf)
                .build();
    }

    /**
     * {@link WebClient.Builder} configured with the partner SslContext. Inject as
     * {@code @Qualifier("mtlsWebClientBuilder")} for any outbound mTLS call.
     */
    @Bean("mtlsWebClientBuilder")
    public WebClient.Builder mtlsWebClientBuilder(SslContext partnerSslContext) {
        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(partnerSslContext));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    private KeyStore loadKeyStore(Resource resource, String password, String type) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = resource.getInputStream()) {
            ks.load(in, password.toCharArray());
        }
        return ks;
    }
}
