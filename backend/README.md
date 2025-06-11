# GymTracker Backend

This is a Spring Boot backend for the GymTracker app. It provides REST API endpoints for user registration and login with JWT authentication.

## Features
- User registration (`/api/auth/register`)
- User login (`/api/auth/login`)
- JWT-based authentication
- H2 in-memory database for development

## Getting Started

### Prerequisites
- Java 17+
- Maven

### Running the Application

```
mvn spring-boot:run
```

### API Endpoints
- `POST /api/auth/register` — Register a new user (body: `{ "username": "user", "password": "pass" }`)
- `POST /api/auth/login` — Login and receive a JWT token (body: `{ "username": "user", "password": "pass" }`)

### H2 Console
- Access at `/h2-console` (JDBC URL: `jdbc:h2:mem:testdb`)

## License
MIT
