FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/workspace-service/pom.xml services/workspace-service/pom.xml
COPY services/auth-service services/auth-service
RUN chmod +x mvnw && ./mvnw package -pl services/auth-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/services/auth-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
