package chit.tefca.routing.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Tuned outbound HTTP stack used by {@link chit.tefca.routing.engine.TransactionForwarder}
 * to forward partner-bound traffic. Sized for the 500&nbsp;ms PA budget:
 *
 * <ul>
 *   <li>Connect timeout 250&nbsp;ms — fast TLS handshake or fail.</li>
 *   <li>Per-request idle/read timeout enforced by the per-call
 *       {@code .timeout(Duration)} in the forwarder (modality-aware).</li>
 *   <li>Pooled TCP connections (200) so the next PA call reuses an existing
 *       handshake and skips ~100&nbsp;ms of TLS setup.</li>
 *   <li>Pending-acquire 300&nbsp;ms cap so a saturated pool fails fast rather
 *       than blowing past the partner deadline.</li>
 * </ul>
 */
@Configuration
public class PartnerForwarderWebClientConfig {

    @Bean(name = "partnerWebClient")
    public WebClient partnerWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("partner-pool")
                .maxConnections(200)
                .pendingAcquireTimeout(Duration.ofMillis(300))
                .pendingAcquireMaxCount(500)
                .maxIdleTime(Duration.ofSeconds(60))
                .maxLifeTime(Duration.ofMinutes(10))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .compress(true)
                .responseTimeout(Duration.ofMillis(450))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 250)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(450, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(250, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
