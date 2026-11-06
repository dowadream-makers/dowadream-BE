# Render 배포용 (멀티스테이지: Gradle 빌드 → JRE 21)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Render 무료 플랜은 RAM 512MB라 힙을 작게 잡습니다.
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:+UseSerialGC"
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
