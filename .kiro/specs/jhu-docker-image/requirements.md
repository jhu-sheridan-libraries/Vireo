# Requirements Document

## Introduction

This document specifies requirements for building a production-ready Docker image of the Vireo ETD Management System configured specifically for Johns Hopkins University (JHU) deployment in AWS container orchestration environments (ECS/Fargate or EC2). The image will replace the current SSH-based deployment workflow with a containerized approach optimized for AWS services.

## Glossary

- **Vireo_Application**: The Electronic Thesis and Dissertation Management System Spring Boot application
- **JHU_Docker_Image**: The Docker container image specifically configured for JHU production deployment
- **AWS_Container_Service**: AWS container orchestration services (ECS, Fargate, or EC2 with Docker)
- **Base_Dockerfile**: The existing multi-stage Dockerfile in the repository root
- **Health_Check**: Container health verification mechanism for orchestration platforms
- **Configuration_Manager**: System component responsible for loading environment-specific configuration
- **Build_Process**: The Docker image build and tag workflow
- **Container_Runtime**: The executing Docker container instance in AWS

## Requirements

### Requirement 1: JHU-Specific Docker Image

**User Story:** As a DevOps engineer, I want a JHU-specific Dockerfile, so that I can build production-ready images for AWS deployment.

#### Acceptance Criteria

1. THE Build_Process SHALL create a Dockerfile named `Dockerfile.jhu` based on the Base_Dockerfile
2. THE JHU_Docker_Image SHALL use the existing multi-stage build pattern (Maven stage and JRE stage)
3. THE JHU_Docker_Image SHALL run the Vireo_Application as a non-root user with UID 1000
4. THE JHU_Docker_Image SHALL expose port 9000 for HTTP traffic
5. THE JHU_Docker_Image SHALL include all runtime dependencies from the Base_Dockerfile (gettext for envsubst)

### Requirement 2: AWS-Optimized Configuration

**User Story:** As a DevOps engineer, I want AWS-specific optimizations in the Docker image, so that the container integrates properly with AWS services.

#### Acceptance Criteria

1. THE Configuration_Manager SHALL support loading database connection strings from environment variables
2. THE Configuration_Manager SHALL support loading JWT secrets from environment variables
3. THE Configuration_Manager SHALL support loading application secrets from environment variables
4. WHEN AWS_Container_Service provides environment variables, THE Vireo_Application SHALL use them to override default configuration values
5. THE JHU_Docker_Image SHALL log to STDOUT and STDERR for CloudWatch Logs integration

### Requirement 3: Container Health Checks

**User Story:** As a platform operator, I want container health checks, so that AWS can automatically restart unhealthy containers.

#### Acceptance Criteria

1. THE JHU_Docker_Image SHALL include a HEALTHCHECK instruction in the Dockerfile
2. WHEN the Container_Runtime is running, THE Health_Check SHALL verify the Vireo_Application responds on port 9000
3. THE Health_Check SHALL execute at 30-second intervals
4. THE Health_Check SHALL have a 10-second timeout
5. THE Health_Check SHALL allow 3 consecutive failures before marking the container unhealthy
6. THE Health_Check SHALL have a 60-second start period to allow application initialization

### Requirement 4: Production Build Configuration

**User Story:** As a DevOps engineer, I want production-optimized build arguments, so that the image is suitable for AWS production deployment.

#### Acceptance Criteria

1. THE Build_Process SHALL set NODE_ENV to "production" during the Maven build stage
2. THE Build_Process SHALL use the `-Dproduction` Maven profile flag
3. THE Build_Process SHALL skip tests during image build with `-Dmaven.test.skip=true`
4. THE Build_Process SHALL configure APP_PATH to `/var/vireo` for asset storage
5. THE JHU_Docker_Image SHALL bundle application assets into the WAR file during production build

### Requirement 5: External Volume Support

**User Story:** As a platform operator, I want to mount external volumes for persistent data, so that application data survives container restarts.

#### Acceptance Criteria

1. THE Container_Runtime SHALL support mounting an external volume at `/var/vireo` for application data
2. THE Container_Runtime SHALL support mounting an external volume at `/vireo/logs` for application logs
3. WHEN external volumes are not mounted, THE Vireo_Application SHALL use container-local directories as fallback
4. THE JHU_Docker_Image SHALL create required directories (`/var/vireo`, `/vireo/logs`) with appropriate permissions during build
5. THE Configuration_Manager SHALL write the templated `appConfig.js` file to the APP_PATH directory

### Requirement 6: Database Connection Configuration

**User Story:** As a DevOps engineer, I want flexible database configuration, so that the container can connect to AWS RDS or other database services.

#### Acceptance Criteria

1. WHEN SPRING_DATASOURCE_URL environment variable is provided, THE Configuration_Manager SHALL use it for database connection
2. WHEN SPRING_DATASOURCE_USERNAME environment variable is provided, THE Configuration_Manager SHALL use it for database authentication
3. WHEN SPRING_DATASOURCE_PASSWORD environment variable is provided, THE Configuration_Manager SHALL use it for database authentication
4. THE Configuration_Manager SHALL support PostgreSQL as the primary database platform for production
5. WHEN SPRING_JPA_DATABASE_PLATFORM environment variable is provided, THE Configuration_Manager SHALL use it to configure Hibernate dialect

### Requirement 7: Secrets Management Integration

**User Story:** As a security engineer, I want the container to load secrets from environment variables, so that sensitive data is not baked into the image.

#### Acceptance Criteria

1. WHEN AUTH_SECURITY_JWT_SECRET environment variable is provided, THE Configuration_Manager SHALL use it for JWT token signing
2. WHEN APP_SECURITY_SECRET environment variable is provided, THE Configuration_Manager SHALL use it for cryptographic operations
3. THE JHU_Docker_Image SHALL NOT contain hardcoded secrets in any configuration files
4. THE Configuration_Manager SHALL fail startup with a descriptive error IF required secrets are missing
5. THE Vireo_Application SHALL support loading secrets from AWS Secrets Manager via environment variable injection
6. THE Vireo_Application SHALL support loading secrets from AWS Systems Manager Parameter Store via environment variable injection
7. THE Build_Process SHALL NOT commit secrets (keys, passwords, credentials) directly to GitHub repositories

### Requirement 8: Graceful Shutdown Support

**User Story:** As a platform operator, I want graceful container shutdown, so that in-flight requests complete before the container terminates.

#### Acceptance Criteria

1. WHEN the Container_Runtime receives a SIGTERM signal, THE Vireo_Application SHALL stop accepting new requests
2. WHEN the Container_Runtime receives a SIGTERM signal, THE Vireo_Application SHALL complete processing of in-flight requests
3. THE Vireo_Application SHALL shut down within 30 seconds of receiving SIGTERM
4. THE JHU_Docker_Image SHALL configure Spring Boot shutdown grace period to 25 seconds
5. WHEN graceful shutdown exceeds the timeout, THE Container_Runtime SHALL forcefully terminate with SIGKILL

### Requirement 9: Image Tagging and Versioning

**User Story:** As a DevOps engineer, I want consistent image tagging, so that I can track and deploy specific versions.

#### Acceptance Criteria

1. THE Build_Process SHALL tag the JHU_Docker_Image with the Maven project version from pom.xml
2. THE Build_Process SHALL tag the JHU_Docker_Image with "latest" for the most recent build
3. THE Build_Process SHALL tag the JHU_Docker_Image with "jhu-aws-{version}" to distinguish it from generic builds
4. THE Build_Process SHALL include the full Git commit SHA in image labels for traceability
5. THE Build_Process SHALL include the build timestamp in image labels
6. THE Build_Process SHALL use 12-character short Git commit SHAs in Docker image tags
7. THE Build_Process SHALL create image labels following the naming convention: `[Vireo-Head-SHA]-config-[Deployment-Head-SHA]`
8. THE Build_Process SHALL embed the full commit SHA into the Docker image as a label named `org.opencontainers.image.revision`

### Requirement 10: Container Orchestration Compatibility

**User Story:** As a platform operator, I want ECS/Fargate compatibility, so that the container runs in AWS container services.

#### Acceptance Criteria

1. THE JHU_Docker_Image SHALL be compatible with AWS ECS task definitions
2. THE JHU_Docker_Image SHALL be compatible with AWS Fargate launch type
3. THE JHU_Docker_Image SHALL be compatible with AWS ECS EC2 launch type
4. THE JHU_Docker_Image SHALL support AWS CloudWatch Logs via awslogs log driver
5. THE Container_Runtime SHALL expose port 9000 for Application Load Balancer health checks

### Requirement 11: Build Documentation

**User Story:** As a developer, I want clear build instructions, so that I can build and test the JHU Docker image locally.

#### Acceptance Criteria

1. THE Build_Process SHALL provide a README.md file documenting the Docker build command
2. THE Build_Process SHALL document all required build arguments and their purposes
3. THE Build_Process SHALL document all required environment variables for runtime configuration
4. THE Build_Process SHALL provide example docker run commands for local testing
5. THE Build_Process SHALL document the differences between Dockerfile and Dockerfile.jhu
6. THE Build_Process SHALL document how to run Docker locally for development environments
7. THE Build_Process SHALL document how to run Docker for production environments with appropriate configuration
8. THE Build_Process SHALL document how to manually push images to GHCR and ECR

### Requirement 12: Container Registry Support

**User Story:** As a DevOps engineer, I want to push Docker images to container registries, so that images can be deployed to AWS environments.

#### Acceptance Criteria

1. THE JHU_Docker_Image SHALL be compatible with GitHub Container Registry (GHCR)
2. THE JHU_Docker_Image SHALL be compatible with Amazon Elastic Container Registry (ECR)
3. THE Build_Process SHALL support tagging images for GHCR format: `ghcr.io/{org}/{repo}:{tag}`
4. THE Build_Process SHALL support tagging images for ECR format: `{account}.dkr.ecr.{region}.amazonaws.com/{repo}:{tag}`
5. THE JHU_Docker_Image SHALL include metadata labels compatible with both GHCR and ECR
