FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache dumb-init wget ca-certificates
RUN addgroup -S tefca && adduser -S tefca -G tefca

WORKDIR /app

ARG OTEL_AGENT_VERSION=2.10.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /app/otel-javaagent.jar

COPY target/directory-cache-service-*-exec.jar app.jar

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

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["dumb-init", "--", "/app/entrypoint.sh"]
