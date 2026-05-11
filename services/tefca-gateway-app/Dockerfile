# syntax=docker/dockerfile:1.7
# ==============================================================================
# TEFCA Gateway — single fat-jar container (EKS Fargate amd64)
# Build context = repo root: tefca-gateway/
#   docker buildx build --platform linux/amd64 \
#     -t <ECR>/tefca-gateway:latest --push \
#     -f services/tefca-gateway-app/Dockerfile .
# ==============================================================================

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Cache deps
COPY services/pom.xml services/pom.xml
COPY services/tefca-common/pom.xml services/tefca-common/pom.xml
COPY services/ingress-auth-service/pom.xml services/ingress-auth-service/pom.xml
COPY services/policy-service/pom.xml services/policy-service/pom.xml
COPY services/routing-service/pom.xml services/routing-service/pom.xml
COPY services/directory-cache-service/pom.xml services/directory-cache-service/pom.xml
COPY services/tefca-gateway-app/pom.xml services/tefca-gateway-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f services/pom.xml -DskipTests dependency:go-offline || true

COPY services/ services/
COPY apps/ apps/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f services/pom.xml -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app && apk add --no-cache unzip
WORKDIR /app

COPY --from=build /src/services/tefca-gateway-app/target/tefca-gateway.jar app.jar
# FIPS provider order override — single '=' on -Djava.security.properties below
# MERGES with the JDK's default file (so we keep all defaults except providers).
COPY --from=build /src/services/tefca-gateway-app/src/main/resources/fips.security /app/fips.security

# Extract BouncyCastle FIPS jars to /app/lib so they sit on the SYSTEM
# classloader. Required because BC-FIPS performs an integrity self-test that
# locates its own jar by URL — that lookup fails when the jar is nested inside
# a Spring Boot fat-jar (BOOT-INF/lib/bc-fips-*.jar). With them on -cp the
# system classloader resolves them first; the duplicates inside BOOT-INF/lib
# are shadowed by the parent-first delegation.
RUN mkdir -p /app/lib && cd /app/lib && \
    unzip -j /app/app.jar 'BOOT-INF/lib/bc-fips-*.jar' \
                          'BOOT-INF/lib/bctls-fips-*.jar' \
                          'BOOT-INF/lib/bcpkix-fips-*.jar' \
                          'BOOT-INF/lib/bcutil-fips-*.jar' && \
    chown -R app:app /app/lib && ls -l /app/lib

USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError \
        -Djava.security.properties=/app/fips.security \
        -Dorg.bouncycastle.fips.approved_only=true \
        -Dcom.amazonaws.sdk.useFipsEndpoint=true \
        -Dloader.main=chit.tefca.app.TefcaGatewayApplication"

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "exec java -cp '/app/lib/*:/app/app.jar' org.springframework.boot.loader.launch.PropertiesLauncher"]
