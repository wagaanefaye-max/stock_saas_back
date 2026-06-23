FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Monter les volumes hôte :
#   /home/ubuntu/justificatifs:/home/ubuntu/justificatifs
#   /home/ubuntu/company-logos:/home/ubuntu/company-logos
ENV APP_UPLOAD_SUBSCRIPTIONS_DIR=/home/ubuntu/justificatifs
ENV APP_UPLOAD_COMPANIES_LOGOS_DIR=/home/ubuntu/company-logos

EXPOSE 8080

ENV PORT=8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]