# Stage 1: Build with Maven & Java 22
FROM maven:3.9.8-eclipse-temurin-22 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests
RUN java -Djarmode=tools -jar target/*.jar extract --destination /app/extracted --application-filename app.jar

# Stage 2: Lightweight runtime image with JRE 22
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app
COPY --from=build /app/extracted/ ./

ENV PORT=10000
ENV SPRING_PROFILES_ACTIVE=render
EXPOSE 10000

# Leave room for metaspace, threads and native buffers in a 512 MiB container.
# C1 compilation reduces startup CPU work on small Render instances.
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=10.0", "-XX:MaxRAMPercentage=50.0", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxDirectMemorySize=32m", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-Xss512k", "-jar", "app.jar"]
