# syntax=docker/dockerfile:1
FROM gradle:9.6.0-jdk17 AS build
WORKDIR /src
COPY . .
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 riceguard
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER riceguard
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
