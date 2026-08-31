# ---- Stage 1: 构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: 运行 ----
# Adoptium 无 arm64 alpine 变体，改用 jammy 基础镜像（多架构兼容）
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && groupadd -r app && useradd -r -g app app \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

USER app
EXPOSE 9090

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:9090/smart-router/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
