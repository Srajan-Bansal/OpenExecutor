# OpenExecutor

A sandboxed code execution engine for [HackStack](https://github.com/Srajan-Bansal/HackStack) — built with Spring Boot.

## What it does

1. Consumes code execution requests from Kafka (`code-executor` topic)
2. Loads test cases from Redis (populated from [hackstack-problems](https://github.com/Srajan-Bansal/hackstack-problems) at startup)
3. Compiles and runs code in isolated sandboxes using `isolate`
4. Measures runtime and memory per test case
5. Publishes results to Kafka (`code-results` topic)

## Tech Stack

- Java 17
- Spring Boot 3.5
- Apache Kafka (consumer + producer)
- Redis (test case caching)
- `isolate` (sandbox execution)
- Maven

## Getting Started

### Prerequisites

- Java 17+
- Redis running on `localhost:6379`
- Kafka running on `localhost:9092`
- `isolate` installed (for sandboxed execution)
- [hackstack-problems](https://github.com/Srajan-Bansal/hackstack-problems) cloned as a sibling directory

### Run

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test
```

The service starts on port **8081**.

### Configuration

All configuration is in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | Server port |
| `spring.data.redis.host` | localhost | Redis host |
| `spring.data.redis.port` | 6379 | Redis port |
| `spring.kafka.bootstrap-servers` | localhost:9092 | Kafka broker |
| `basePath` | ../hackstack-problems | Path to problems directory |

### Supported Languages

- Java
- JavaScript

## Related Repositories

- [HackStack](https://github.com/Srajan-Bansal/HackStack) — Parent repository
- [HackStack-monorepo](https://github.com/Srajan-Bansal/HackStack-monorepo) — Web frontend, backend API, webhook
- [hackstack-problems](https://github.com/Srajan-Bansal/hackstack-problems) — Problem definitions and test cases
