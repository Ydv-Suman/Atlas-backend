FROM eclipse-temurin:25-jdk AS build
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

FROM node:22-bookworm-slim AS agent-runtime
WORKDIR /app/runtime
COPY services/agent-service/runtime/package*.json ./
RUN npm ci --omit=dev
COPY services/agent-service/runtime/bridge.mjs services/agent-service/runtime/files.mjs services/agent-service/runtime/git.mjs ./

FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl wget git python3 util-linux ripgrep ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=agent-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=agent-runtime /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
RUN mkdir -p /tmp/atlas/workspaces && chmod 700 /tmp/atlas/workspaces
COPY --from=agent-runtime /app/runtime /app/runtime
COPY --from=build /app/services/agent-service/target/*.jar app.jar
ENV ATLAS_RUNTIME_WORKSPACE_ROOT=/data/agent-sessions
ENV ATLAS_RUNTIME_ISOLATE_USERS=true
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
