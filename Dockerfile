# Start with the official OpenJDK 17 base image
FROM openjdk:17-jdk-slim

# Set a working directory inside the container
WORKDIR /app

# Install curl for downloading dependencies
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Download Jackson dependencies from Maven Central
RUN curl -L -o jackson-databind.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.0/jackson-databind-2.17.0.jar && \
    curl -L -o jackson-core.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.0/jackson-core-2.17.0.jar && \
    curl -L -o jackson-annotations.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.0/jackson-annotations-2.17.0.jar

# 💡 This is the crucial part that creates the libs directory and moves the files
RUN mkdir -p libs && \
    mv jackson-databind.jar libs/ && \
    mv jackson-core.jar libs/ && \
    mv jackson-annotations.jar libs/

# Set the CLASSPATH to include the current directory and the new libs directory
ENV CLASSPATH=".:/app/libs/*"