# Stage 1: Build with Maven & Java 22
FROM maven:3.9.8-eclipse-temurin-22 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight runtime image with JRE 22
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV PORT=10000
ENV SPRING_PROFILES_ACTIVE=render
EXPOSE 10000

ENTRYPOINT ["java", "-XX:InitialRAMPercentage=15.0", "-XX:MaxRAMPercentage=60.0", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-Xss512k", "-jar", "app.jar"]
