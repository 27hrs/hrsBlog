FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

# 静态资源根目录预建在镜像里，配合 compose 的命名卷使用：
# 命名卷首次创建时会继承镜像内目录的属主，非 root 的 app 用户因此可以直接写入。
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/storage/assets \
    && chown -R app:app /app/storage
COPY --from=build --chown=app:app /workspace/target/HrsBlog-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 27000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
