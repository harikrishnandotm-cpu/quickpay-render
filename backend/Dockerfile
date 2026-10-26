ARG SERVICE_MODULE

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE_MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -f pom.xml -pl "${SERVICE_MODULE}" -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE_MODULE
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-1.0.0.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=20 CMD curl -fsS http://localhost:8080/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
