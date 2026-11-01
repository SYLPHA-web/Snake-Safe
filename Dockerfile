# Builds and runs the SnakeSafe backend (Spring Boot, Java 25). Works on any host that runs Docker images:
# Google Cloud Run, Fly.io, Railway, Koyeb, Hugging Face Spaces, a VPS, ...
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/snakesafe-1.0.0.jar app.jar
USER app
# Settings kept small so the app fits in 512 MB hosts. Real data lives in Supabase, so the production profile is the default here
ENV SPRING_PROFILES_ACTIVE=supabase \
    TRUST_PROXY=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
# The app listens on $PORT (hosts set it) and falls back to 8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
