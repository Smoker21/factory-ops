# ======================================================
# Factory Ops Backend — Production Dockerfile
# Multi-stage: Gradle build → Eclipse Temurin JRE slim
# ======================================================

# Stage 1: Build
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy build scripts first for better layer caching
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

# Copy full source and build (skip tests — run separately in CI)
COPY . .
RUN ./gradlew build -x test -Dquarkus.package.type=jar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /deployments

# Copy compiled Quarkus fast-jar
COPY --from=build /app/build/quarkus-app/ ./

# Remove dev PEM keys that were packaged onto the classpath.
# Production must inject JWT_PUBLIC_KEY_PATH and JWT_PRIVATE_KEY_PATH via env.
RUN find /deployments -name "*.pem" -delete

# Install curl for healthcheck (not present in temurin:21-jre by default)
# Non-root user for security
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system appgroup \
    && useradd --system --gid appgroup --uid 1001 appuser \
    && chown -R appuser:appgroup /deployments
USER 1001

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/q/health/ready || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
