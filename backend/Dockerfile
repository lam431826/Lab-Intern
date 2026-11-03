# syntax=docker/dockerfile:1
# Image backend Hệ thống Tuyển dụng DX Lab. Build từ thư mục backend:
#   docker build -t dxlab-recruitment-backend backend
# Test không chạy trong bước build (cần Docker cho Testcontainers); chạy "mvnw verify" trước khi build.

# ---------- Build ----------
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /src

# Tải dependency trước, để sửa code không phải tải lại
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && mkdir /out \
    && cd /out \
    && java -Djarmode=tools -jar /src/target/recruitment-*.jar extract --layers --launcher --destination /out/app

# ---------- Runtime ----------
FROM eclipse-temurin:21.0.13_9-jre-noble
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Thứ tự từ ít đổi đến hay đổi để tận dụng cache layer
COPY --from=build /out/app/dependencies/ ./
COPY --from=build /out/app/spring-boot-loader/ ./
COPY --from=build /out/app/snapshot-dependencies/ ./
COPY --from=build /out/app/application/ ./

USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Ho_Chi_Minh \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
