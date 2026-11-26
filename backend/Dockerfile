# Multi-stage build for the Spring Boot backend.
# Stage 1: build the jar with the Maven wrapper (no system Maven needed).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# Copy wrapper + pom first so dependency resolution is cached between builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# Stage 2: slim runtime image with just the JRE and the jar.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/hrflow-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
