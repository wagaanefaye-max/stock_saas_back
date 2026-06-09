FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN mkdir -p /data/uploads/subscriptions
VOLUME ["/data/uploads/subscriptions"]

EXPOSE 8080

ENV PORT=8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]