# JHU Docker Image Build and Registry Push Guide

This document covers building the JHU-specific Docker image (`Dockerfile.jhu`) and pushing it to container registries (GHCR and ECR).

## Prerequisites

- Docker 20.10+
- Git
- AWS CLI v2 (for ECR push only)
- Access to the target container registry

## Extract Build Variables

### Version (from pom.xml)

```bash
VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')
echo $VERSION
# Example output: 4.3.2
```

### Git SHAs

```bash
# Full 40-character SHA (for image labels)
VIREO_GIT_SHA_FULL=$(git rev-parse HEAD)
echo $VIREO_GIT_SHA_FULL
# Example: a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2

# Short 12-character SHA (for image tags)
VIREO_GIT_SHA_SHORT=$(git rev-parse --short=12 HEAD)
echo $VIREO_GIT_SHA_SHORT
# Example: a1b2c3d4e5f6
```

### Deployment Config SHAs

If you maintain a separate deployment config repository, extract its SHAs the same way from that repo. Otherwise, use the Vireo repo SHAs:

```bash
# From the deployment config repo directory:
DEPLOYMENT_CONFIG_SHA_FULL=$(git rev-parse HEAD)
DEPLOYMENT_CONFIG_SHA_SHORT=$(git rev-parse --short=12 HEAD)

# Or if using the same repo:
DEPLOYMENT_CONFIG_SHA_FULL=$VIREO_GIT_SHA_FULL
DEPLOYMENT_CONFIG_SHA_SHORT=$VIREO_GIT_SHA_SHORT
```

### Build Timestamp

```bash
BUILD_TIMESTAMP=$(date -u +%Y%m%d-%H%M%S)
echo $BUILD_TIMESTAMP
# Example: 20260316-143022
```

## Build Arguments Reference

| Argument | Description | Example |
|---|---|---|
| `VERSION` | Maven project version from pom.xml | `4.3.2` |
| `VIREO_GIT_SHA_FULL` | Full 40-char Vireo Git commit SHA | `a1b2c3d4e5f6...` (40 chars) |
| `VIREO_GIT_SHA_SHORT` | 12-char short Vireo Git commit SHA | `a1b2c3d4e5f6` |
| `DEPLOYMENT_CONFIG_SHA_FULL` | Full 40-char deployment config Git SHA | `z9y8x7w6v5u4...` (40 chars) |
| `DEPLOYMENT_CONFIG_SHA_SHORT` | 12-char short deployment config Git SHA | `z9y8x7w6v5u4` |
| `BUILD_TIMESTAMP` | UTC build timestamp | `20260316-143022` |

## Build the Docker Image

The image uses a multi-stage build: Maven compiles the WAR with the production profile (`-Pproduction -Dmaven.test.skip=true`), then a minimal JRE image packages the runtime. Maven runs inside the container, so no local Maven installation is required.

```bash
docker build -f Dockerfile.jhu \
  --build-arg VERSION=$VERSION \
  --build-arg VIREO_GIT_SHA_FULL=$VIREO_GIT_SHA_FULL \
  --build-arg VIREO_GIT_SHA_SHORT=$VIREO_GIT_SHA_SHORT \
  --build-arg DEPLOYMENT_CONFIG_SHA_FULL=$DEPLOYMENT_CONFIG_SHA_FULL \
  --build-arg DEPLOYMENT_CONFIG_SHA_SHORT=$DEPLOYMENT_CONFIG_SHA_SHORT \
  --build-arg BUILD_TIMESTAMP=$BUILD_TIMESTAMP \
  -t vireo-jhu-aws:latest \
  .
```

## Image Tagging Strategy

Each build should produce multiple tags for flexibility:

| Tag Format | Example | Purpose |
|---|---|---|
| `latest` | `latest` | Most recent build |
| `jhu-aws-{version}` | `jhu-aws-4.3.2` | Semantic version from pom.xml |
| `jhu-aws-{short-sha}` | `jhu-aws-a1b2c3d4e5f6` | 12-char Git SHA for traceability |
| `jhu-aws-{timestamp}` | `jhu-aws-20260316-143022` | Build timestamp for ordering |

### Apply All Tags

```bash
REGISTRY=ghcr.io/jhu-sheridan-libraries/vireo

docker tag vireo-jhu-aws:latest $REGISTRY:latest
docker tag vireo-jhu-aws:latest $REGISTRY:jhu-aws-$VERSION
docker tag vireo-jhu-aws:latest $REGISTRY:jhu-aws-$VIREO_GIT_SHA_SHORT
docker tag vireo-jhu-aws:latest $REGISTRY:jhu-aws-$BUILD_TIMESTAMP
```

Or build with all tags in one command:

```bash
docker build -f Dockerfile.jhu \
  --build-arg VERSION=$VERSION \
  --build-arg VIREO_GIT_SHA_FULL=$VIREO_GIT_SHA_FULL \
  --build-arg VIREO_GIT_SHA_SHORT=$VIREO_GIT_SHA_SHORT \
  --build-arg DEPLOYMENT_CONFIG_SHA_FULL=$DEPLOYMENT_CONFIG_SHA_FULL \
  --build-arg DEPLOYMENT_CONFIG_SHA_SHORT=$DEPLOYMENT_CONFIG_SHA_SHORT \
  --build-arg BUILD_TIMESTAMP=$BUILD_TIMESTAMP \
  -t $REGISTRY:latest \
  -t $REGISTRY:jhu-aws-$VERSION \
  -t $REGISTRY:jhu-aws-$VIREO_GIT_SHA_SHORT \
  -t $REGISTRY:jhu-aws-$BUILD_TIMESTAMP \
  .
```

## Image Labels

The built image includes OCI-standard and JHU-specific labels:

| Label | Value | Description |
|---|---|---|
| `org.opencontainers.image.version` | `4.3.2` | Maven project version |
| `org.opencontainers.image.revision` | Full 40-char Vireo SHA | Complete Git commit hash |
| `org.opencontainers.image.created` | `20260316-143022` | Build timestamp |
| `org.opencontainers.image.vendor` | `Johns Hopkins University` | Organization |
| `edu.jhu.vireo.config-sha` | Full 40-char deployment config SHA | Deployment config commit hash |
| `edu.jhu.vireo.build-label` | `{vireo-short-sha}-config-{config-short-sha}` | Combined 12-char SHAs |

Inspect labels on a built image:

```bash
docker inspect --format='{{json .Config.Labels}}' vireo-jhu-aws:latest | python -m json.tool
```

## Push to GitHub Container Registry (GHCR)

```bash
# Authenticate (use a Personal Access Token with write:packages scope)
echo $GHCR_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Push all tags
docker push $REGISTRY:latest
docker push $REGISTRY:jhu-aws-$VERSION
docker push $REGISTRY:jhu-aws-$VIREO_GIT_SHA_SHORT
docker push $REGISTRY:jhu-aws-$BUILD_TIMESTAMP
```

> Store your GHCR token securely. Never commit tokens or credentials to the repository.

## Push to Amazon Elastic Container Registry (ECR)

```bash
# Set your AWS account and region
AWS_ACCOUNT_ID=123456789012
AWS_REGION=us-east-1
ECR_REPO=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/vireo

# Authenticate Docker to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Tag for ECR
docker tag vireo-jhu-aws:latest $ECR_REPO:latest
docker tag vireo-jhu-aws:latest $ECR_REPO:jhu-aws-$VERSION
docker tag vireo-jhu-aws:latest $ECR_REPO:jhu-aws-$VIREO_GIT_SHA_SHORT
docker tag vireo-jhu-aws:latest $ECR_REPO:jhu-aws-$BUILD_TIMESTAMP

# Push to ECR
docker push $ECR_REPO:latest
docker push $ECR_REPO:jhu-aws-$VERSION
docker push $ECR_REPO:jhu-aws-$VIREO_GIT_SHA_SHORT
docker push $ECR_REPO:jhu-aws-$BUILD_TIMESTAMP
```

> ECR authentication tokens expire after 12 hours. Re-authenticate if pushes fail with auth errors.
> Ensure the ECR repository exists before pushing. Create it via the AWS Console or CLI:
> `aws ecr create-repository --repository-name vireo --region $AWS_REGION`

## Security Notes

- Never hardcode secrets, tokens, or credentials in build scripts or documentation
- Use environment variables or secret management tools for sensitive values
- GHCR tokens should have minimal required scopes (`write:packages`)
- ECR authentication uses IAM roles — follow least-privilege principles
- The Docker image runs as non-root user (UID 1000) by default
