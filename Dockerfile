# Stage 1: Build the Spring Boot jar
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies first (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/

RUN ./mvnw clean package -DskipTests -B

# Stage 2: Build isolate sandbox (on Alpine to match runner)
FROM alpine:3.20 AS isolate-builder

RUN apk add --no-cache git gcc make musl-dev libcap-dev

RUN git clone https://github.com/ioi/isolate.git /isolate \
    && cd /isolate \
    && git checkout v2.0 \
    && make isolate

# Stage 3: Production runtime
FROM eclipse-temurin:17-jdk-alpine AS runner

WORKDIR /app

# Install Node.js and libcap (required by isolate)
RUN apk add --no-cache nodejs libcap

# Copy isolate binary and config
COPY --from=isolate-builder /isolate/isolate /usr/local/bin/isolate
COPY --from=isolate-builder /isolate/default.cf /usr/local/etc/isolate

# isolate needs setuid root to create sandboxes
RUN chmod u+s /usr/local/bin/isolate

# Create isolate box directory
RUN mkdir -p /var/local/lib/isolate

# Copy the application jar
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]
