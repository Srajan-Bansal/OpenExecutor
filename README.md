Yes, you can use that simpler approach! For Kafka 3.7.1, KRaft mode is the default, so it's much simpler:

  Simple Kafka Setup

  # Run Redis (Alpine - smallest image ~40MB)
  docker run -d --name hackstack-redis -p 6379:6379 redis:7.2-alpine

  # Run Kafka (smallest available ~400MB)
  docker run -d --name hackstack-kafka -p 9092:9092 apache/kafka:3.7.1

  Working with Kafka

  # Get into the Kafka container
  docker exec -it hackstack-kafka /bin/bash

  # Navigate to Kafka bin directory
  cd /opt/kafka/bin

  # Create the actual topics used by HackStack
  ./kafka-topics.sh --create --topic code-executor --bootstrap-server localhost:9092
  ./kafka-topics.sh --create --topic code-results --bootstrap-server localhost:9092

  # List topics
  ./kafka-topics.sh --list --bootstrap-server localhost:9092

  # Monitor code-executor topic (execution requests)
  ./kafka-console-consumer.sh --topic code-executor --from-beginning --bootstrap-server localhost:9092

  # Monitor code-results topic (execution results) - in another terminal
  docker exec -it hackstack-kafka /bin/bash
  cd /opt/kafka/bin
  ./kafka-console-consumer.sh --topic code-results --from-beginning --bootstrap-server localhost:9092

  Your .env configuration

  REDIS_URL=redis://localhost:6379
  KAFKA_BROKER=localhost:9092

  This simpler approach works perfectly for development! The Kafka 3.7.1 image comes pre-configured with KRaft mode, so no Zookeeper neede

# Kafka Monitorning UI
  docker run -d \
  -p 8080:8080 \
  -e AKHQ_CONFIGURATION='
akhq:
  connections:
    local:
      properties:
        bootstrap.servers: "localhost:9092"
' \
  tchiotludo/akhq
