# Multi-stage build: stage 1 has the full JDK + Maven to compile, stage 2 only the JRE to run.
# The final image contains no source code, no build tools and no Maven cache, so it is smaller and has less to attack.

# ---------- Stage 1: build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy only the build definition first and download dependencies. Docker caches this layer, so later builds that
# only change source code skip the (slow) download step.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI and locally (they need Docker for Testcontainers, which isn't available inside this build).
RUN ./mvnw -B -q package -DskipTests && cp target/fincore-*.jar app.jar

# ---------- Stage 2: run ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Never run the app as root inside the container.
RUN groupadd --system fincore && useradd --system --gid fincore --no-create-home fincore
COPY --from=build /workspace/app.jar app.jar
USER fincore

# Hosting platforms pass PORT; locally the app falls back to 8083 (see application.properties).
EXPOSE 8083
# Size the heap from the container's memory limit instead of the host machine's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
