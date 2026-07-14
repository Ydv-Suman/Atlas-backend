FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/workspace-service/pom.xml services/workspace-service/pom.xml
COPY services/api-gateway services/api-gateway
RUN chmod +x mvnw && ./mvnw package -pl services/api-gateway -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
