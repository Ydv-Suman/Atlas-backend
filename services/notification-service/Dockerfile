FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/workspace-service/pom.xml services/workspace-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/agent-service/pom.xml services/agent-service/pom.xml
COPY services/notification-service services/notification-service
RUN chmod +x mvnw && ./mvnw package -pl services/notification-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl wget
COPY --from=build /app/services/notification-service/target/*.jar app.jar
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "app.jar"]
