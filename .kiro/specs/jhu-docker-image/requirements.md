# Requirements Document

## Introduction

This document specifies requirements for building a Docker image of the Vireo ETD Management System configured specifically for Johns Hopkins University (JHU) local development and testing. The image enables developers to build and run the full Vireo application stack locally using Docker, with support for both H2 and PostgreSQL databases. This image is intended solely for local testing and is not part of the greater CI/CD pipeline or production deployment infrastructure.

## Glossary

- **Vireo_Application**: The Electronic Thesis and Dissertation Management System Spring Boot application
- **JHU_Docker_Image**: The Docker container image specifically configured for JHU local development and testing
- **Base_Dockerfile**: The existing multi-stage Dockerfile in the repository root
- **Health_Check**: Container health verification mechanism used by docker-compose to determine service readiness
- **Configuration_Manager**: System component responsible for loading environment-specific configuration via environment variables and the docker-entrypoint.sh script
- **Build_Process**: The Docker image build workflow using `docker build` with `Dockerfile.jhu`
- **Container_Runtime**: The executing Docker container instance on a developer's local machine
- **Compose_Stack**: The docker-compose service definition for running Vireo and its dependencies locally

## Requirements

### Requirement 1: JHU-Specific Docker Image for Local Testing

**User Story:** As a developer, I want a JHU-specific Dockerfile, so that I can build and run the Vireo application locally in a containerized environment for testing.

#### Acceptance Criteria

1. THE Build_Process SHALL create a Dockerfile named `Dockerfile.jhu` based on the Base_Dockerfile
2. THE JHU_Docker_Image SHALL use the existing multi-stage build pattern (Maven stage and JRE stage)
3. THE JHU_Docker_Image SHALL run the Vireo_Application as a non-root user with UID 1000
4. THE JHU_Docker_Image SHALL expose port 9000 for HTTP traffic
5. THE JHU_Docker_Image SHALL include all runtime dependencies from the Base_Dockerfile (gettext for envsubst)

### Requirement 2: Environment Variable Configuration

**User Story:** As a developer, I want to configure the application through environment variables, so that I can easily switch between database backends and authentication modes during local testing.

#### Acceptance Criteria

1. THE Configuration_Manager SHALL support loading database connection strings from environment variables
2. THE Configuration_Manager SHALL support loading JWT secrets from environment variables
3. THE Configuration_Manager SHALL support loading application secrets from environment variables
4. WHEN environment variables are provided, THE Vireo_Application SHALL use them to override default configuration values from application.yml
5. THE JHU_Docker_Image SHALL log application output to STDOUT and STDERR for visibility in docker-compose logs

### Requirement 3: Container Health Checks

**User Story:** As a developer, I want container health checks, so that docker-compose can determine when the application is ready to accept requests.

#### Acceptance Criteria

1. THE JHU_Docker_Image SHALL include a HEALTHCHECK instruction in the Dockerfile
2. WHEN the Container_Runtime is running, THE Health_Check SHALL verify the Vireo_Application responds on port 9000
3. THE Health_Check SHALL execute at 30-second intervals
4. THE Health_Check SHALL have a 10-second timeout
5. THE Health_Check SHALL allow 3 consecutive failures before marking the container unhealthy
6. THE Health_Check SHALL have a 60-second start period to allow application initialization

### Requirement 4: Production Build Configuration

**User Story:** As a developer, I want the Docker image to use a production build profile, so that the locally tested image reflects the same build output that would be deployed.

#### Acceptance Criteria

1. THE Build_Process SHALL set NODE_ENV to "production" during the Maven build stage
2. THE Build_Process SHALL use the `-Pproduction` Maven profile flag
3. THE Build_Process SHALL skip tests during image build with `-Dmaven.test.skip=true`
4. THE Build_Process SHALL configure APP_PATH to `/var/vireo` for asset storage

### Requirement 5: External Volume Support

**User Story:** As a developer, I want to mount local directories into the container, so that application data persists across container restarts during testing.

#### Acceptance Criteria

1. THE Container_Runtime SHALL support mounting an external volume at `/var/vireo` for application data
2. THE Container_Runtime SHALL support mounting an external volume at `/vireo/logs` for application logs
3. WHEN external volumes are not mounted, THE Vireo_Application SHALL use container-local directories as fallback

### Requirement 6: Database Connection Configuration

**User Story:** As a developer, I want flexible database configuration, so that I can test with either H2 in-memory or a local PostgreSQL database.

#### Acceptance Criteria

1. WHEN SPRING_DATASOURCE_URL environment variable is provided, THE Configuration_Manager SHALL use it for database connection
2. WHEN SPRING_DATASOURCE_USERNAME environment variable is provided, THE Configuration_Manager SHALL use it for database authentication
3. WHEN SPRING_DATASOURCE_PASSWORD environment variable is provided, THE Configuration_Manager SHALL use it for database authentication
4. THE Configuration_Manager SHALL support both H2 and PostgreSQL as database platforms for local testing
5. WHEN SPRING_JPA_DATABASE_PLATFORM environment variable is provided, THE Configuration_Manager SHALL use it to configure Hibernate dialect

### Requirement 7: Secrets Handling

**User Story:** As a developer, I want the container to load secrets from environment variables, so that sensitive data is not baked into the image.

#### Acceptance Criteria

1. WHEN AUTH_SECURITY_JWT_SECRET environment variable is provided, THE Configuration_Manager SHALL use it for JWT token signing
2. WHEN APP_SECURITY_SECRET environment variable is provided, THE Configuration_Manager SHALL use it for cryptographic operations
3. THE JHU_Docker_Image SHALL NOT contain hardcoded secrets in any layer of the built image
4. IF required environment variables (AUTH_SERVICE_URL, LOCAL_AUTHENTICATION, STOMP_DEBUG, APP_PATH) are missing, THEN THE Configuration_Manager SHALL exit with a descriptive error message

### Requirement 8: Graceful Shutdown Support

**User Story:** As a developer, I want graceful container shutdown, so that stopping docker-compose does not corrupt in-flight requests or data.

#### Acceptance Criteria

1. WHEN the Container_Runtime receives a SIGTERM signal, THE Vireo_Application SHALL stop accepting new requests
2. WHEN the Container_Runtime receives a SIGTERM signal, THE Vireo_Application SHALL complete processing of in-flight requests
3. THE Vireo_Application SHALL shut down within 30 seconds of receiving SIGTERM
4. THE JHU_Docker_Image SHALL configure Spring Boot shutdown grace period to 25 seconds

### Requirement 9: Image Tagging for Local Use

**User Story:** As a developer, I want a simple image tagging convention, so that I can identify locally built images.

#### Acceptance Criteria

1. THE Build_Process SHALL support tagging the JHU_Docker_Image with a developer-specified tag (e.g., `vireo-jhu:local`)
2. THE Build_Process SHALL include the Git commit SHA in image labels for traceability
3. THE Build_Process SHALL include the build timestamp in image labels

### Requirement 10: Docker Compose for Local Testing

**User Story:** As a developer, I want a docker-compose file, so that I can start the full Vireo stack (application and database) with a single command.

#### Acceptance Criteria

1. THE Compose_Stack SHALL define a `docker-compose-test.yml` file for local integration testing
2. THE Compose_Stack SHALL include a PostgreSQL service with a preconfigured test database
3. THE Compose_Stack SHALL include the Vireo service built from Dockerfile.jhu
4. THE Compose_Stack SHALL configure the Vireo service to depend on the PostgreSQL service being healthy before starting
5. THE Compose_Stack SHALL pass all required environment variables (database, secrets, frontend config) to the Vireo service
6. THE Compose_Stack SHALL expose port 9000 on the host for browser access

### Requirement 11: Build and Usage Documentation

**User Story:** As a developer, I want clear documentation, so that I can build and run the JHU Docker image locally without prior Docker expertise.

#### Acceptance Criteria

1. THE Build_Process SHALL provide documentation describing the Docker build command with required build arguments
2. THE Build_Process SHALL document all required and optional environment variables for runtime configuration
3. THE Build_Process SHALL provide example `docker run` commands for standalone local testing
4. THE Build_Process SHALL document how to use `docker-compose-test.yml` for local stack testing
5. THE Build_Process SHALL document the differences between `Dockerfile` and `Dockerfile.jhu`
6. THE Build_Process SHALL document how to switch between H2 and PostgreSQL database backends
