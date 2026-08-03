FROM maven:3.9.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY --from=dependencies /root/.m2 /root/.m2
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl tzdata \
    && addgroup -S worknest \
    && adduser -S worknest -G worknest
WORKDIR /app
COPY --from=build --chown=worknest:worknest /app/target/*.jar /app/app.jar
USER worknest
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness || curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
