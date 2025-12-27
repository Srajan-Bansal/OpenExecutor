# OpenExecutor

A high-performance code execution engine built with Spring Boot for the HackStack competitive programming platform. OpenExecutor processes code submissions, runs test cases, and returns execution results through Kafka-based asynchronous processing.

## Overview

OpenExecutor is part of the HackStack ecosystem, serving as the core execution engine that:
- Consumes code execution requests from Kafka topics
- Executes user-submitted code in isolated environments
- Runs multiple test cases against submissions
- Produces execution results back to Kafka for result processing
- Caches test case data using Redis for optimal performance

## Tech Stack

- **Java 17** - Programming language
- **Spring Boot 3.5.6** - Application framework
- **Spring Kafka** - Kafka integration for message streaming
- **Spring Data Redis** - Caching layer
- **PostgreSQL** - Database (optional for future features)
- **Lombok** - Boilerplate code reduction
- **Maven** - Dependency management

## Features

- **Asynchronous Execution**: Kafka-based message processing for scalable code execution
- **Multi-Language Support**: Execute Java and JavaScript code submissions
- **Test Case Management**: Load and execute multiple test cases per submission
- **Redis Caching**: Fast test case retrieval and result caching
- **Concurrent Processing**: Configurable concurrent execution (default: 2 concurrent executions)
- **Error Handling**: Comprehensive error handling and logging
- **Resource Management**: Memory and runtime tracking for submissions

## Prerequisites

Before running OpenExecutor locally, ensure you have the following installed:

- **Java 17** or higher ([Download](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html))
- **Maven 3.6+** (included via Maven Wrapper)
- **Docker** (for running Kafka and Redis)
- **Git** (for cloning the repository)

## Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Srajan-Bansal/OpenExecutor.git
cd OpenExecutor
```

### 2. Start Required Services (Docker)

OpenExecutor requires Kafka and Redis to be running. Use Docker to start these services:

#### Start Redis

```bash
docker run -d --name hackstack-redis -p 6379:6379 redis:7.2-alpine
```

#### Start Kafka (KRaft mode - no Zookeeper needed)

```bash
docker run -d --name hackstack-kafka -p 9092:9092 apache/kafka:3.7.1
```

#### Create Required Kafka Topics

```bash
# Access Kafka container
docker exec -it hackstack-kafka /bin/bash
cd /opt/kafka/bin

# Create code-executor topic (receives execution requests)
./kafka-topics.sh --create --topic code-executor --bootstrap-server localhost:9092

# Create code-results topic (sends execution results)
./kafka-topics.sh --create --topic code-results --bootstrap-server localhost:9092

# Verify topics were created
./kafka-topics.sh --list --bootstrap-server localhost:9092

# Exit the container
exit
```

### 3. Configure Application Properties

Update `src/main/resources/application.properties` if needed:

```properties
# Server Configuration
server.port=8081

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=mypassword

# Problems Base Path (adjust to your hackstack-problems directory)
basePath=../hackstack-problems

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=code-executor-group
spring.kafka.listener.concurrency=2
```

### 4. Ensure hackstack-problems Repository is Available

The executor needs access to problem test cases. Make sure the `hackstack-problems` repository is cloned at the path specified in `basePath`:

```bash
# From the parent directory (same level as executor)
git clone https://github.com/Srajan-Bansal/hackstack-problems.git
```

Your directory structure should look like:

```
HackStack/
├── executor/           # This repository
├── hackstack-problems/ # Problem definitions and test cases
└── HackStack-server/   # Main application server
```

## Running Locally

### Option 1: Using Maven Wrapper (Recommended)

```bash
# Build and run the application
./mvnw spring-boot:run
```

### Option 2: Using Maven

```bash
# Build the application
mvn clean package

# Run the JAR file
java -jar target/executor-0.0.1-SNAPSHOT.jar
```

### Option 3: Using Your IDE

1. Open the project in IntelliJ IDEA or Eclipse
2. Locate `HackstackExecutionEngineApplication.java`
3. Right-click and select "Run"

### Verify the Application is Running

Once started, you should see:

```
Started HackstackExecutionEngineApplication in X.XXX seconds
```

The executor will:
- Start on port `8081`
- Connect to Redis at `localhost:6379`
- Connect to Kafka at `localhost:9092`
- Start consuming messages from the `code-executor` topic

## Testing the Executor

### Monitor Kafka Topics

To see execution requests and results:

```bash
# Monitor incoming execution requests
docker exec -it hackstack-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --topic code-executor --from-beginning --bootstrap-server localhost:9092

# Monitor execution results
docker exec -it hackstack-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --topic code-results --from-beginning --bootstrap-server localhost:9092
```

### Send Test Messages

You can send test execution requests using the HackStack backend server or manually via Kafka console producer.

## Project Structure

```
executor/
├── src/
│   ├── main/
│   │   ├── java/com/example/executor/
│   │   │   ├── configurations/    # Redis and other configs
│   │   │   ├── constants/         # Application constants
│   │   │   ├── enums/            # Language types enum
│   │   │   ├── model/            # Data models (ExecutorInput, etc.)
│   │   │   ├── service/          # Business logic
│   │   │   │   ├── ExecutorService.java      # Core execution logic
│   │   │   │   └── TestCaseLoader.java       # Test case management
│   │   │   └── utility/          # Response utilities
│   │   └── resources/
│   │       └── application.properties  # Configuration
│   └── test/                     # Unit tests
├── mvnw                          # Maven wrapper (Unix)
├── mvnw.cmd                      # Maven wrapper (Windows)
└── pom.xml                       # Maven dependencies
```

## Configuration Options

### Redis Configuration

```properties
spring.data.redis.host=localhost        # Redis host
spring.data.redis.port=6379             # Redis port
spring.data.redis.password=mypassword   # Redis password (if set)
```

### Kafka Configuration

```properties
spring.kafka.bootstrap-servers=localhost:9092           # Kafka broker
spring.kafka.consumer.group-id=code-executor-group      # Consumer group
spring.kafka.listener.concurrency=2                     # Concurrent executions
```

### Execution Configuration

```properties
basePath=../hackstack-problems          # Path to test cases
server.port=8081                        # Server port
```

## Troubleshooting

### Issue: Connection refused to Kafka

**Solution**: Ensure Kafka container is running:

```bash
docker ps | grep hackstack-kafka
# If not running, start it:
docker start hackstack-kafka
```

### Issue: Connection refused to Redis

**Solution**: Ensure Redis container is running:

```bash
docker ps | grep hackstack-redis
# If not running, start it:
docker start hackstack-redis
```

### Issue: Test cases not found

**Solution**: Verify `basePath` in `application.properties` points to the correct `hackstack-problems` directory.

### Issue: Port 8081 already in use

**Solution**: Change the port in `application.properties`:

```properties
server.port=8082
```

## Development

### Running Tests

```bash
./mvnw test
```

### Building for Production

```bash
./mvnw clean package -DskipTests
```

The JAR file will be created in `target/executor-0.0.1-SNAPSHOT.jar`

### Code Style

This project uses Lombok for reducing boilerplate code. Ensure your IDE has Lombok plugin installed:
- **IntelliJ IDEA**: Preferences → Plugins → Search "Lombok" → Install
- **Eclipse**: Download Lombok JAR and run it

## Integration with HackStack

OpenExecutor works as part of the HackStack ecosystem:

1. **HackStack Backend** sends code execution requests to Kafka topic `code-executor`
2. **OpenExecutor** consumes requests, executes code, and publishes results to `code-results`
3. **Submission Webhook** consumes results and updates submission status in the database

## Environment Variables (Optional)

You can override properties using environment variables:

```bash
export SERVER_PORT=8081
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export BASE_PATH=../hackstack-problems

./mvnw spring-boot:run
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is part of the HackStack platform.

## Related Repositories

- [HackStack Server](https://github.com/Srajan-Bansal/HackStack) - Main application with frontend and backend
- [HackStack Problems](https://github.com/Srajan-Bansal/hackstack-problems) - Problem definitions and test cases

## Support

For issues and questions, please open an issue on the [GitHub repository](https://github.com/Srajan-Bansal/OpenExecutor/issues).
