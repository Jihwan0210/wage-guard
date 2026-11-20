# 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon -q

# 실행 (1GB 메모리 기준)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/wageback-0.0.1-SNAPSHOT.jar app.jar
ENV TZ=Asia/Seoul JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
