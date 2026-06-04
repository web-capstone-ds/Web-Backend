FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew

COPY src/ ./src/
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
