FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache dumb-init wget ca-certificates
RUN addgroup -S tefca && adduser -S tefca -G tefca

WORKDIR /app

# Download the OpenTelemetry Java agent for distributed tracing/metrics.
# Activation is opt-in via OTEL_AGENT_ENABLED=true so local/dev containers do
# not pay the startup cost. ECS task definitions flip it on with the OTLP
# collector endpoint via OTEL_EXPORTER_OTLP_ENDPOINT.
ARG OTEL_AGENT_VERSION=2.10.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /app/otel-javaagent.jar

# Build with `mvn -pl ingress-auth-service -am package -DskipTests` from the
# services/ directory before running `docker compose build`. The compose file
# uses ./services/ingress-auth-service as the build context.
COPY target/ingress-auth-service-*-exec.jar app.jar

# Wrapper that conditionally enables the OTel agent based on env var.
COPY <<'EOF' /app/entrypoint.sh
#!/bin/sh
set -eu
JAVA_AGENT_OPTS=""
if [ "${OTEL_AGENT_ENABLED:-false}" = "true" ]; then
    JAVA_AGENT_OPTS="-javaagent:/app/otel-javaagent.jar"
fi
exec java $JAVA_AGENT_OPTS \
    -XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/app.jar
EOF
RUN chmod +x /app/entrypoint.sh

RUN chown -R tefca:tefca /app
USER tefca

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["dumb-init", "--", "/app/entrypoint.sh"]
