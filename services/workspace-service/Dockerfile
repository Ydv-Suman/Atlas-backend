FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared-lib shared-lib
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/workspace-service services/workspace-service
RUN chmod +x mvnw && ./mvnw package -pl services/workspace-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/workspace-service/target/*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
