FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

RUN mvn --no-transfer-progress -f pom.xml package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
 && apt-get install -y curl \
 && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 10001 appuser

COPY --from=build /app/target/social-posting-0.1.0.jar /app/app.jar

EXPOSE 8080

VOLUME ["/app/data"]

USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
