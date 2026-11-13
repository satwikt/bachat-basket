# Stage 1: build the React frontend
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json ./
RUN npm install --no-audit --no-fund
COPY web/ ./
RUN npm run build

# Stage 2: build the Spring Boot jar, with the frontend served from static/
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# Stage 3: runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
