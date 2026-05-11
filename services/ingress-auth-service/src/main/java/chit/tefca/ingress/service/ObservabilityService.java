package chit.tefca.ingress.service;

import chit.tefca.ingress.config.AdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory rolling-window aggregator over each downstream service's
 * {@code /actuator/prometheus} endpoint. Polls every 15s, keeps the last 30
 * minutes (= 120 samples) per service. Exposes computed rates / averages.
 *
 * <p>This is intentionally simple — no Prometheus dependency, no histogram
 * quantile interpolation. Production deployments should scrape the same
 * endpoints with a real Prometheus + Grafana stack; this service exists so the
 * built-in admin UI can render a useful dashboard out of the box.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObservabilityService {

    private static final int WINDOW_SAMPLES = 120; // 30 minutes at 15s
    private static final List<String> SERVICES = List.of("ingress", "policy", "routing", "directory");

    private final AdminProperties adminProperties;
    private final WebClient.Builder webClientBuilder;

    /** snapshot of cumulative counters at a point in time. */
    private record RawSnapshot(
            Instant ts,
            double httpRequestCount,
            double httpRequestErrorCount,
            double httpRequestSumSeconds,
            double cpuUsage,
            double memoryUsedBytes,
            double dbPoolActive
    ) {}

    /** computed delta sample emitted to UI. */
    public record Sample(
            Instant ts,
            double rps,
            double errorRate,
            double avgLatencyMs,
            double cpuPct,
            double memoryUsedMb,
            double dbPoolActive
    ) {}

    public record ServiceSummary(
            String service,
            boolean up,
            double rps,
            double errorRate,
            double avgLatencyMs,
            double cpuPct,
            double memoryUsedMb,
            double dbPoolActive,
            List<Sample> series
    ) {}

    private final Map<String, Deque<Sample>> series = new ConcurrentHashMap<>();
    private final Map<String, RawSnapshot> last = new ConcurrentHashMap<>();
    private final Map<String, Boolean> upStatus = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT5S")
    public void tick() {
        for (String svc : SERVICES) {
            try {
                pollOne(svc);
            } catch (Exception e) {
                log.debug("observability poll failed for {}: {}", svc, e.toString());
                upStatus.put(svc, false);
            }
        }
    }

    private void pollOne(String service) {
        String baseUrl = adminProperties.getProxy().getServices().get(service);
        if (baseUrl == null) return;

        WebClient client = webClientBuilder.build();
        String text;
        try {
            text = client.get()
                    .uri(baseUrl + "/actuator/prometheus")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception e) {
            upStatus.put(service, false);
            return;
        }
        if (text == null) {
            upStatus.put(service, false);
            return;
        }
        upStatus.put(service, true);

        RawSnapshot snap = parse(text);
        RawSnapshot prev = last.put(service, snap);
        if (prev == null) return; // need two samples to compute deltas

        double dt = Math.max(0.001, Duration.between(prev.ts, snap.ts).toMillis() / 1000.0);
        double dCount = Math.max(0, snap.httpRequestCount - prev.httpRequestCount);
        double dErrors = Math.max(0, snap.httpRequestErrorCount - prev.httpRequestErrorCount);
        double dSum = Math.max(0, snap.httpRequestSumSeconds - prev.httpRequestSumSeconds);

        double rps = dCount / dt;
        double errorRate = dCount > 0 ? dErrors / dCount : 0.0;
        double avgLatencyMs = dCount > 0 ? (dSum / dCount) * 1000.0 : 0.0;

        Sample s = new Sample(snap.ts, rps, errorRate, avgLatencyMs,
                snap.cpuUsage * 100.0,
                snap.memoryUsedBytes / (1024.0 * 1024.0),
                snap.dbPoolActive);

        Deque<Sample> q = series.computeIfAbsent(service, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(s);
            while (q.size() > WINDOW_SAMPLES) q.pollFirst();
        }
    }

    /**
     * Returns a curated current + history view for every known service. Safe to
     * call from any thread; produces a stable defensive copy.
     */
    public List<ServiceSummary> snapshot() {
        List<ServiceSummary> out = new ArrayList<>();
        for (String svc : SERVICES) {
            Deque<Sample> q = series.get(svc);
            List<Sample> hist;
            Sample latest;
            synchronized (q == null ? new Object() : q) {
                hist = q == null ? List.of() : new ArrayList<>(q);
            }
            latest = hist.isEmpty() ? null : hist.get(hist.size() - 1);

            out.add(new ServiceSummary(
                    svc,
                    upStatus.getOrDefault(svc, false),
                    latest != null ? latest.rps() : 0.0,
                    latest != null ? latest.errorRate() : 0.0,
                    latest != null ? latest.avgLatencyMs() : 0.0,
                    latest != null ? latest.cpuPct() : 0.0,
                    latest != null ? latest.memoryUsedMb() : 0.0,
                    latest != null ? latest.dbPoolActive() : 0.0,
                    hist
            ));
        }
        return out;
    }

    // ── prometheus text parser ────────────────────────────────────────────

    /** Matches a prometheus sample line: {@code metric{labels} value [ts]}. */
    private static final Pattern LINE = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{[^}]*\\})?\\s+([0-9eE+\\-.NaN]+)");

    private static RawSnapshot parse(String text) {
        double httpCount = 0, httpSum = 0, httpErrors = 0;
        double cpu = 0, memUsed = 0, dbPool = 0;

        // memory_used: sum across heap areas only
        Map<String, Double> memByArea = new HashMap<>();

        for (String raw : text.split("\n")) {
            if (raw.isEmpty() || raw.charAt(0) == '#') continue;
            Matcher m = LINE.matcher(raw);
            if (!m.find()) continue;
            String name = m.group(1);
            String labels = m.group(2);
            double value;
            try { value = Double.parseDouble(m.group(3)); } catch (NumberFormatException e) { continue; }

            switch (name) {
                case "http_server_requests_seconds_count" -> {
                    httpCount += value;
                    if (labels != null && (labels.contains("status=\"5") || labels.contains("status=\"4"))) {
                        httpErrors += value;
                    }
                }
                case "http_server_requests_seconds_sum" -> httpSum += value;
                case "system_cpu_usage" -> cpu = value;
                case "jvm_memory_used_bytes" -> {
                    if (labels != null && labels.contains("area=\"heap\"")) {
                        String key = labels;
                        memByArea.merge(key, value, Double::sum);
                    }
                }
                case "hikaricp_connections_active" -> dbPool = value;
                default -> { /* ignore */ }
            }
        }
        for (double v : memByArea.values()) memUsed += v;

        return new RawSnapshot(Instant.now(), httpCount, httpErrors, httpSum, cpu, memUsed, dbPool);
    }
}
