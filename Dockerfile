FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Extract Jackson JARs from the fat JAR so they can be used on the
# compilation classpath when compiling user code (Main.java uses ObjectMapper).
RUN mkdir -p /app/libs && \
    cd /app/libs && \
    jar xf /app/app.jar BOOT-INF/lib/ && \
    mv BOOT-INF/lib/jackson-*.jar . && \
    rm -rf BOOT-INF

EXPOSE 8081

ENTRYPOINT ["java", "-Xmx512m", "-XX:ReservedCodeCacheSize=128m", "-jar", "app.jar"]
