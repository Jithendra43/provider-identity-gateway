package chit.tefca.routing.service;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.dto.RouteResponse;
import chit.tefca.routing.engine.EndpointResolver;
import chit.tefca.routing.engine.IdempotencyManager;
import chit.tefca.routing.engine.TransactionForwarder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final EndpointResolver endpointResolver;
    private final TransactionForwarder transactionForwarder;
    private final IdempotencyManager idempotencyManager;
    private final Timer routingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;

    public RoutingService(EndpointResolver endpointResolver,
                          TransactionForwarder transactionForwarder,
                          IdempotencyManager idempotencyManager,
                          MeterRegistry meterRegistry) {
        this.endpointResolver = endpointResolver;
        this.transactionForwarder = transactionForwarder;
        this.idempotencyManager = idempotencyManager;
        this.routingTimer = meterRegistry.timer("routing.transaction.duration");
        this.successCounter = meterRegistry.counter("routing.transactions", "result", "SUCCESS");
        this.failureCounter = meterRegistry.counter("routing.transactions", "result", "FAILURE");
    }

    public RouteResponse routeTransaction(RouteRequest request) {
        log.info("Routing transaction correlationId={} op={} target={}",
                request.getCorrelationId(), request.getOperation(), request.getTargetOrgId());

        // Check idempotency
        if (idempotencyManager.isDuplicate(request.getIdempotencyKey())) {
            log.warn("Duplicate request detected for idempotencyKey={}", request.getIdempotencyKey());
            return RouteResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .httpStatus(409)
                    .responsePayload(Map.of("error", "DUPLICATE_REQUEST"))
                    .completedAt(Instant.now())
                    .build();
        }

        RouteResponse response = routingTimer.record(() -> {
            long start = System.currentTimeMillis();

            // Resolve endpoint
            Endpoint endpoint = endpointResolver.resolve(request);
            long routingDuration = System.currentTimeMillis() - start;

            // Forward transaction
            RouteResponse fwdResponse = transactionForwarder.forward(request, endpoint);
            fwdResponse.setRoutingDurationMs(routingDuration);
            return fwdResponse;
        });

        // Record metrics
        if (response.getHttpStatus() >= 200 && response.getHttpStatus() < 300) {
            successCounter.increment();
        } else {
            failureCounter.increment();
        }

        // Mark processed
        idempotencyManager.markProcessed(request.getIdempotencyKey(), request.getCorrelationId());

        return response;
    }
}
