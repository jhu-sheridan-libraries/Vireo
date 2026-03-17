# Vireo ETD Management System — JHU AWS Docker Image

This document covers building, configuring, and deploying the JHU-specific Docker image (`Dockerfile.jhu`) for the Vireo ETD Management System in AWS container environments (ECS/Fargate).

## Purpose

`Dockerfile.jhu` extends the base `Dockerfile` with AWS-specific enhancements for production deployment at Johns Hopkins University. Both files use the same multi-stage build pattern (Maven + JRE), but `Dockerfile.jhu` adds:

- **HEALTHCHECK** instruction for ECS/Fargate container health monitoring
- **Image labels** following OCI standards and JHU naming conventions for traceability
- **wget** installed in the JRE stage (used by the health check)
- **Graceful shutdown** support (30s stop timeout, 25s Spring Boot grace period)
- **Environment variable validation** with warnings for missing Spring Boot overrides

The base `Dockerfile` remains unchanged and is intended for local development.

## Image Labeling Convention

Images are labeled with a combined build label following this format:

```
[Vireo-Head-SHA]-config-[Deployment-Head-SHA]
```

- Both SHAs use **12-character short format** in image tags for readability
- Full 40-character SHAs are embedded in image labels for complete traceability

Example:
```
Tag:   jhu-aws-a1b2c3d4e5f6
Label: a1b2c3d4e5f6-config-z9y8x7w6v5u4
```

Labels embedded in the image:

| Label | Description |
|-------|-------------|
| `org.opencontainers.image.version` | Maven project version (e.g., `4.3.2`) |
| `org.opencontainers.image.revision` | Full 40-char Vireo Git SHA |
| `org.opencontainers.image.created` | Build timestamp |
| `org.opencontainers.image.vendor` | `Johns Hopkins University` |
| `edu.jhu.vireo.config-sha` | Full 40-char deployment config Git SHA |
| `edu.jhu.vireo.build-label` | `{vireo-short-sha}-config-{deploy-short-sha}` |

## Building the Docker Image

### Prerequisites

- Docker 20.10+
- Git (for SHA extraction)

### Extract Build Arguments

```bash
# Version from pom.xml
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# Vireo application Git SHAs
VIREO_GIT_SHA_FULL=$(git rev-parse HEAD)
VIREO_GIT_SHA_SHORT=$(git rev-parse --short=12 HEAD)

# Deployment config Git SHAs (from your deployment config repo, if separate)
DEPLOYMENT_CONFIG_SHA_FULL=$(git rev-parse HEAD)
DEPLOYMENT_CONFIG_SHA_SHORT=$(git rev-parse --short=12 HEAD)

# Build timestamp
BUILD_TIMESTAMP=$(date -u +%Y%m%d-%H%M%S)
```

### Build Command

```bash
docker build -f Dockerfile.jhu \
  --build-arg VERSION=$VERSION \
  --build-arg VIREO_GIT_SHA_FULL=$VIREO_GIT_SHA_FULL \
  --build-arg VIREO_GIT_SHA_SHORT=$VIREO_GIT_SHA_SHORT \
  --build-arg DEPLOYMENT_CONFIG_SHA_FULL=$DEPLOYMENT_CONFIG_SHA_FULL \
  --build-arg DEPLOYMENT_CONFIG_SHA_SHORT=$DEPLOYMENT_CONFIG_SHA_SHORT \
  --build-arg BUILD_TIMESTAMP=$BUILD_TIMESTAMP \
  -t ghcr.io/jhu-sheridan-libraries/vireo:latest \
  -t ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$VERSION \
  -t ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$VIREO_GIT_SHA_SHORT \
  -t ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$BUILD_TIMESTAMP \
  .
```

### Build Arguments Reference

| Argument | Description | Example |
|----------|-------------|---------|
| `VERSION` | Maven project version | `4.3.2` |
| `VIREO_GIT_SHA_FULL` | Full 40-char Vireo Git SHA | `a1b2c3d4e5f6...` |
| `VIREO_GIT_SHA_SHORT` | 12-char short Vireo Git SHA | `a1b2c3d4e5f6` |
| `DEPLOYMENT_CONFIG_SHA_FULL` | Full 40-char deployment config SHA | `z9y8x7w6v5u4...` |
| `DEPLOYMENT_CONFIG_SHA_SHORT` | 12-char short deployment config SHA | `z9y8x7w6v5u4` |
| `BUILD_TIMESTAMP` | UTC build timestamp | `20260317-143000` |
| `USER_ID` | Container user UID (default: `1000`) | `1000` |
| `APP_PATH` | Application data directory (default: `/var/vireo`) | `/var/vireo` |
| `NODE_ENV` | Node environment (default: `production`) | `production` |


## Pushing to Container Registries

### GitHub Container Registry (GHCR)

```bash
# Authenticate
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Push all tags
docker push ghcr.io/jhu-sheridan-libraries/vireo:latest
docker push ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$VERSION
docker push ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$VIREO_GIT_SHA_SHORT
docker push ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$BUILD_TIMESTAMP
```

### Amazon Elastic Container Registry (ECR)

```bash
# Authenticate (replace <account-id> and <region>)
aws ecr get-login-password --region <region> | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

# Tag for ECR
docker tag ghcr.io/jhu-sheridan-libraries/vireo:latest \
  <account-id>.dkr.ecr.<region>.amazonaws.com/vireo:latest

docker tag ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-$VERSION \
  <account-id>.dkr.ecr.<region>.amazonaws.com/vireo:jhu-aws-$VERSION

# Push
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/vireo:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/vireo:jhu-aws-$VERSION
```

## Environment Variables

### Required Variables (entrypoint validation — container will not start without these)

| Variable | Description | Example |
|----------|-------------|---------|
| `AUTH_SERVICE_URL` | JavaScript expression for auth service endpoint | `window.location.protocol + '//' + window.location.host + window.location.base` |
| `LOCAL_AUTHENTICATION` | Auth mode: `true`, `false`, or `'alternate'` | `true` |
| `STOMP_DEBUG` | WebSocket debug flag | `false` |
| `APP_PATH` | Application data directory path | `/var/vireo` |

### Spring Boot Override Variables (warnings if missing, defaults from application.yml)

| Variable | Spring Property | Description | Default |
|----------|----------------|-------------|---------|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | JDBC connection string | `jdbc:h2:mem:AZ;...` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | Database username | `vireo` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | Database password | `vireo` |
| `SPRING_JPA_DATABASE_PLATFORM` | `spring.jpa.database-platform` | Hibernate dialect | (auto-detected) |
| `SPRING_DATASOURCE_DRIVERCLASSNAME` | `spring.datasource.driverClassName` | JDBC driver class | `org.h2.Driver` |
| `AUTH_SECURITY_JWT_SECRET` | `auth.security.jwt.secret` | JWT signing key | `verysecretsecret` |
| `APP_SECURITY_SECRET` | `app.security.secret` | Crypto service key | `verysecretsecret` |
| `APP_EMAIL_HOST` | `app.email.host` | SMTP server | `relay.tamu.edu` |
| `APP_EMAIL_FROM` | `app.email.from` | Email sender address | `noreply@library.tamu.edu` |
| `LOGGING_LEVEL_ORG_TDL` | `logging.level.org.tdl` | Application log level | `INFO` |

### Optional Variables

| Variable | Spring Property | Description | Default |
|----------|----------------|-------------|---------|
| `SERVER_PORT` | `server.port` | HTTP port | `9000` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `spring.jpa.hibernate.ddl-auto` | DDL strategy | `update` |
| `AUTH_SECURITY_JWT_ISSUER` | `auth.security.jwt.issuer` | JWT issuer | `localhost` |
| `AUTH_SECURITY_JWT_DURATION` | `auth.security.jwt.duration` | JWT duration (hours) | `5` |
| `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` | `spring.lifecycle.timeout-per-shutdown-phase` | Graceful shutdown grace period | `25s` |

Spring Boot automatically maps environment variables to properties: underscores become dots, uppercase becomes lowercase (e.g., `SPRING_DATASOURCE_URL` → `spring.datasource.url`).


## Running Locally

### Using docker-compose-test.yml

The `docker-compose-test.yml` file provides a local testing environment with PostgreSQL:

```bash
docker-compose -f docker-compose-test.yml up
```

This starts the Vireo application and a PostgreSQL database, pre-configured for local development. The application will be available at `http://localhost:9000`.

### Using docker run (H2 in-memory database)

```bash
docker run -p 9000:9000 \
  -e AUTH_SERVICE_URL="window.location.protocol + '//' + window.location.host + window.location.base + '/mock/auth'" \
  -e LOCAL_AUTHENTICATION=true \
  -e STOMP_DEBUG=false \
  -e APP_PATH=/var/vireo \
  -v $(pwd)/data:/var/vireo \
  ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-latest
```

### Using docker run (local PostgreSQL)

```bash
docker run -p 9000:9000 \
  -e AUTH_SERVICE_URL="window.location.protocol + '//' + window.location.host + window.location.base + '/mock/auth'" \
  -e LOCAL_AUTHENTICATION=true \
  -e STOMP_DEBUG=false \
  -e APP_PATH=/var/vireo \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/vireo \
  -e SPRING_DATASOURCE_USERNAME=vireo \
  -e SPRING_DATASOURCE_PASSWORD=vireo \
  -e SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect \
  -e SPRING_DATASOURCE_DRIVERCLASSNAME=org.postgresql.Driver \
  -e AUTH_SECURITY_JWT_SECRET=local-dev-secret \
  -e APP_SECURITY_SECRET=local-dev-secret \
  -v $(pwd)/data:/var/vireo \
  ghcr.io/jhu-sheridan-libraries/vireo:jhu-aws-latest
```

## Production Deployment

### Local Development vs Production

| Aspect | Local Development | Production (AWS) |
|--------|------------------|------------------|
| Database | H2 in-memory or local PostgreSQL | AWS RDS PostgreSQL |
| Secrets | Plain-text environment variables | AWS Secrets Manager / Parameter Store |
| Authentication | `LOCAL_AUTHENTICATION=true` | `LOCAL_AUTHENTICATION=false` (Shibboleth) |
| Port exposure | Direct on host (9000) | Behind Application Load Balancer (80/443) |
| Logging | Console output | CloudWatch Logs via `awslogs` driver |
| Storage | Local volume mounts | Amazon EFS / EBS |
| Scaling | Single container | ECS auto-scaling |

The same `Dockerfile.jhu` works for both scenarios — all differences are handled through environment variables.

### ECS Deployment with Task Definition

1. Copy `build/ecs-task-definition.json` and replace all `<placeholder>` values with your AWS account details.

2. Register the task definition:
   ```bash
   aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json
   ```

3. Create or update an ECS service to use the new task definition.

The task definition template configures:
- **Fargate** compatibility with 1 vCPU / 2 GB memory
- Port 9000 mapping for ALB health checks
- CloudWatch Logs via `awslogs` driver
- EFS volume mount at `/var/vireo`
- Health check: `wget --no-verbose --tries=1 --spider http://localhost:9000/`
- 30-second stop timeout for graceful shutdown

### AWS Secrets Manager vs Parameter Store

The ECS task definition `secrets` field supports both services:

**Secrets Manager** (recommended for sensitive data):
```json
{
  "name": "SPRING_DATASOURCE_PASSWORD",
  "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:vireo/db-password-AbCdEf"
}
```
Use for: passwords, API keys, database credentials. Supports automatic rotation and encryption at rest.

**Systems Manager Parameter Store** (for configuration values):
```json
{
  "name": "APP_EMAIL_HOST",
  "valueFrom": "arn:aws:ssm:us-east-1:123456789012:parameter/vireo/email-host"
}
```
Use for: connection strings, non-sensitive settings. Simpler and lower cost.

Both integrate with ECS task definitions via the `secrets` field. The ECS task execution role needs `secretsmanager:GetSecretValue` and/or `ssm:GetParameters` permissions.

See `.env.aws.example` for a complete list of variables with placeholder values showing which should come from Secrets Manager vs Parameter Store.


## Troubleshooting

### Container fails to start: "Required environment variables are missing"

The entrypoint script validates that `AUTH_SERVICE_URL`, `LOCAL_AUTHENTICATION`, `STOMP_DEBUG`, and `APP_PATH` are set. Ensure all four are provided in your ECS task definition or `docker run` command.

### Container starts but health check fails

- The health check has a 60-second start period. Spring Boot may need this time to initialize.
- Verify the application is listening on port 9000: check container logs for `Started Application in X seconds`.
- If using ECS, ensure the security group allows traffic on port 9000 from the ALB.

### "WARNING: SPRING_DATASOURCE_URL is not set"

This is a non-fatal warning. The application will use the default H2 in-memory database from `application.yml`. For production, set `SPRING_DATASOURCE_URL` to your RDS endpoint.

### Permission denied on /var/vireo

The container runs as UID 1000 (non-root). When using EFS, configure an EFS access point with POSIX user UID 1000 and appropriate directory permissions.

### Graceful shutdown timeout

If the container doesn't exit within 30 seconds of receiving SIGTERM, Docker sends SIGKILL. Spring Boot has a 25-second grace period configured. If you have long-running requests, consider increasing `stopTimeout` in the ECS task definition and `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE`.

### envsubst fails during startup

The `appConfig.js` template uses `${VARIABLE}` syntax. If you see template errors, verify the template file at `build/appConfig.js.template` has valid syntax and all referenced variables are set.

### ECR authentication failure

Ensure the ECS task execution role has `ecr:GetAuthorizationToken` and `ecr:BatchGetImage` permissions. For GHCR, ensure the image is public or the task has proper credentials configured.

## Security Best Practices

- **No hardcoded secrets**: All secrets are injected via environment variables at runtime. The image contains no credentials.
- **Non-root user**: The container runs as UID 1000 (`vireo` user), not root.
- **Secrets Manager**: Use AWS Secrets Manager for passwords and keys — never commit them to the repository.
- **Image scanning**: Scan images for vulnerabilities before pushing to ECR (ECR has built-in scanning).
- **Minimal base image**: The JRE stage uses `eclipse-temurin:11-alpine` for a small attack surface.
- **No secrets in build args**: Build arguments contain only version/SHA metadata, never credentials.
- **Transit encryption**: EFS volume configuration enables transit encryption by default.

## File Reference

| File | Purpose |
|------|---------|
| `Dockerfile.jhu` | JHU-specific multi-stage Dockerfile for AWS deployment |
| `Dockerfile` | Base Dockerfile for local development |
| `build/docker-entrypoint.sh` | Container entrypoint: validates env vars, templates appConfig.js |
| `build/appConfig.js.template` | Frontend configuration template (envsubst) |
| `build/ecs-task-definition.json` | ECS/Fargate task definition template |
| `docker-compose-JHU.yml` | Docker Compose for JHU deployment with httpd |
| `.env` | Default environment variables for local development |
| `.env.aws.example` | Example AWS environment variables with placeholder values |
| `src/main/resources/application.yml` | Spring Boot configuration (overridable via env vars) |
