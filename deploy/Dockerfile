# ============================================================
# 后端 Dockerfile（多阶段构建）
# 用法：
#   docker build -t fitpilot-backend:1.0 -f deploy/Dockerfile .
# ============================================================

# ── 阶段 1: 用 Maven 编译 ────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制 pom.xml，依赖单独一层（依赖不变就不会重新下载，Docker 缓存友好）
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline || true

# 再复制源码
COPY src ./src
RUN mvn -B -ntp -DskipTests package && \
    cp target/fitpilot-backend-*.jar /build/app.jar

# ── 阶段 2: 运行时镜像（瘦镜像，只有 JRE + jar）────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区设成上海
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 日志目录
RUN mkdir -p /var/log/fitpilot

COPY --from=builder /build/app.jar /app/fitpilot.jar

# 健康检查（容器内）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/requirements/latest || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/fitpilot.jar"]