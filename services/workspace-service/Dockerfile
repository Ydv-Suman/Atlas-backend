FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/agent-service/pom.xml services/agent-service/pom.xml
COPY services/workspace-service services/workspace-service
RUN chmod +x mvnw && ./mvnw package -pl services/workspace-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/workspace-service/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
