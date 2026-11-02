FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 copilot \
    && mkdir -p /models /data/uploads \
    && chown -R copilot:copilot /models /data
WORKDIR /app
COPY --from=build /app/target/copilot-0.1.0.jar app.jar
USER copilot
ENV MODEL_CACHE_DIR=/models \
    UPLOADS_DIR=/data/uploads \
    CORPUS_DIR=/corpus
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
