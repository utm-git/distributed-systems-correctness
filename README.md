# Distributed Systems Correctness

A multi-module Java 17 and Spring Boot project providing reference implementations for complex distributed system patterns, focusing on correctness, consistency, and reliability.

## Modules

* **`saga-orchestrator`**: Implementation of the Saga pattern for managing distributed transactions and orchestrating compensating actions across microservices.
* **`rate-limiter`**: A robust rate-limiting service to protect APIs and resources from abuse and overload.
* **`distributed-lock-service`**: A service demonstrating distributed locking mechanisms to ensure mutually exclusive access to shared resources in a clustered environment.

## Technologies Used
* Java 17
* Spring Boot 3.2.3
* Maven
* Docker Compose
