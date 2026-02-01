# Morphy Service

Spring Boot REST API service wrapping the morphy-cbh library for ChessBase database access.

## Build and Run

```bash
# Build the service
mvn clean install -pl morphy-service -am

# Run the service
mvn spring-boot:run -pl morphy-service

# Or run the JAR directly
java -jar morphy-service/target/morphy-service-0.1-SNAPSHOT.jar
```

## API Endpoints

### Health Check
```
GET http://localhost:8080/api/health
```

Returns service health status.

## Configuration

Application properties are in `src/main/resources/application.properties`.

Default configuration:
- Port: 8080
- Application name: morphy-service
- Log level: INFO (DEBUG for se.yarin.morphy packages)

## Testing

```bash
mvn test -pl morphy-service
```
