# ===== Стадия 1: Сборка =====
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 1. Копируем обёртку Gradle и файлы конфигурации (кеширование зависимостей)
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Даём права на выполнение скрипта gradlew (на случай, если флаги сбросились)
RUN chmod +x gradlew

# Загружаем зависимости (распакуются в ~/.gradle) – слой закешируется, если не менялись build-файлы
RUN ./gradlew dependencies --no-daemon

# 2. Копируем исходники и собираем приложение
COPY src src
RUN ./gradlew build --no-daemon -x test

# ===== Стадия 2: Выполнение =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Копируем собранный JAR (Spring Boot fat jar)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]