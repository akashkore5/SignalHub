# Stage 1: Build the JAR
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /event
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /event
COPY --from=builder /event/target/*.jar event.jar

# Expose port if needed (optional for consumer; Render ignores for Background Workers)
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "event.jar"]
