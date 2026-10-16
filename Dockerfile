# How Railway (or any Docker host) builds and runs the site.

# 1) Build the jar with Maven + Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests

# 2) Run it on a slim Java 21 runtime.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# The database lives in /data, which should be a persistent volume so tasks survive redeploys.
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/data/personalsite
# Voice recordings from the Notes page live on the same volume.
ENV NOTES_STORAGE_DIR=/data/recordings
# Cache templates in production (local dev turns this off so edits show on refresh).
ENV SPRING_THYMELEAF_CACHE=true

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
