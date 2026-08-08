FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/workspace-service/pom.xml services/workspace-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/agent-service services/agent-service
RUN chmod +x mvnw && ./mvnw package -pl services/agent-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl git
COPY --from=build /app/services/agent-service/target/*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
