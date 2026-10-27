FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /workspace/target/HrsBlog-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 27000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
