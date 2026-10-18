FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# bootJar nests dependencies inside the executable archive. javac cannot use
# those nested jars, so runner-libs is a normal directory of Jackson jars.
RUN ./gradlew clean bootJar copyRunnerLibs -x test --no-daemon

FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder /app/build/runner-libs/ /app/libs/
RUN chmod -R a+rX /app/libs

EXPOSE 8081

ENTRYPOINT ["java", "-Xmx512m", "-XX:ReservedCodeCacheSize=128m", "-jar", "app.jar"]
