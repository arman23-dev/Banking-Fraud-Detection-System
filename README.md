# Banking & Fraud Detection System

A microservices-based banking backend application built using **Java and Spring Boot**, designed to manage banking operations, process transactions asynchronously, and support fraud detection through independent services.

The project follows a distributed microservices architecture, with Apache Kafka for event-driven communication and Redis for caching.

## 🚀 Features

- **Account Management:** Dedicated service for managing banking accounts.
- **Transaction Management:** Handles banking transaction operations.
- **Payment Processing:** Separate service for managing payment-related operations.
- **Fraud Detection:** Independent service for transaction fraud analysis.
- **Notification Management:** Dedicated service for notification-related operations.
- **API Gateway:** Centralized entry point for routing client requests.
- **Event-Driven Communication:** Uses Apache Kafka for asynchronous communication between services.
- **Caching:** Integrates Redis for faster data access.
- **Database Persistence:** Uses PostgreSQL for relational data storage.
- **Containerization:** Uses Docker Compose to manage infrastructure dependencies.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java | Backend programming |
| Spring Boot | Microservices development |
| Spring Data JPA | Database operations |
| PostgreSQL | Relational database |
| Apache Kafka | Asynchronous messaging |
| Redis | Caching |
| Docker | Containerization |
| Docker Compose | Infrastructure management |
| REST APIs | Service communication |

## 🏗️ Project Architecture

The application is organized into independent services:

```text
Banking-Fraud-Detection-System/
│
├── account-service/
│
├── api-gateway/
│
├── fraud-detection-service/
│
├── notification-service/
│
├── payment-service/
│
├── transaction-service/
│
└── docker-compose.yml
```

### Microservices Overview

| Service | Responsibility |
|---|---|
| Account Service | Account-related operations |
| API Gateway | Request routing |
| Transaction Service | Transaction processing |
| Payment Service | Payment operations |
| Fraud Detection Service | Fraud analysis |
| Notification Service | Notification handling |

## ⚙️ Prerequisites

Make sure you have the following installed:

- Java (version required by the project)
- Docker Desktop
- Git
- Maven or Gradle, depending on the service build configuration
- IDE such as IntelliJ IDEA

## 📥 Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/arman23-dev/Banking-Fraud-Detection-System.git
```

### 2. Navigate to the Project

```bash
cd Banking-Fraud-Detection-System
```

### 3. Start Infrastructure Services

Run the following command:

```bash
docker compose up -d
```

This starts the infrastructure configured in Docker Compose:

- PostgreSQL
- Redis
- Apache Kafka
- Zookeeper

### 4. Run the Microservices

Open the individual service directories in your IDE and configure their application properties.

Start each Spring Boot service using its configured build tool and application entry point.

### 5. Verify the Application

Once all required services are running, verify their configured endpoints and check the application logs for successful startup.

## 🔄 Application Architecture

The application separates banking responsibilities into independent microservices.

The API Gateway acts as the entry point for incoming requests, while individual services handle account, payment, transaction, fraud detection, and notification responsibilities.

Apache Kafka provides asynchronous event communication, Redis supports caching, and PostgreSQL provides persistent data storage.

## 🔮 Future Enhancements

- Implement advanced fraud detection rules.
- Introduce authentication and authorization using Spring Security and JWT.
- Add centralized logging and monitoring.
- Implement distributed tracing.
- Add automated unit and integration testing.
- Improve service resilience and fault tolerance.

## 👨‍💻 Author

**Md Arman Ansari**

- GitHub: https://github.com/arman23-dev
- Project Repository: https://github.com/arman23-dev/Banking-Fraud-Detection-System

---

⭐ If you find this project useful, consider giving it a star!
