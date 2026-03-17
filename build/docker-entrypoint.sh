#!/bin/sh
# Requires: printenv, envsubst, head.

set -e

# Validate required environment variables
echo "Validating required environment variables..."

MISSING_VARS=""

if [ -z "$AUTH_SERVICE_URL" ]; then
  MISSING_VARS="$MISSING_VARS AUTH_SERVICE_URL"
fi

if [ -z "$LOCAL_AUTHENTICATION" ]; then
  MISSING_VARS="$MISSING_VARS LOCAL_AUTHENTICATION"
fi

if [ -z "$STOMP_DEBUG" ]; then
  MISSING_VARS="$MISSING_VARS STOMP_DEBUG"
fi

if [ -z "$APP_PATH" ]; then
  MISSING_VARS="$MISSING_VARS APP_PATH"
fi

if [ -n "$MISSING_VARS" ]; then
  echo "ERROR: Required environment variables are missing:$MISSING_VARS" >&2
  echo "Please set the following environment variables and restart the container." >&2
  exit 1
fi

echo "All required environment variables are present."

# Optional validation: warn if Spring Boot override variables are not set
# These are not hard failures since defaults exist in application.yml
if [ -z "$SPRING_DATASOURCE_URL" ]; then
  echo "WARNING: SPRING_DATASOURCE_URL is not set. Using default from application.yml." >&2
fi

if [ -z "$AUTH_SECURITY_JWT_SECRET" ]; then
  echo "WARNING: AUTH_SECURITY_JWT_SECRET is not set. Using default from application.yml." >&2
fi

if [ -z "$APP_SECURITY_SECRET" ]; then
  echo "WARNING: APP_SECURITY_SECRET is not set. Using default from application.yml." >&2
fi

# Ensure APP_PATH directory exists
if [ ! -d "$APP_PATH" ]; then
  echo "Creating APP_PATH directory: $APP_PATH"
  mkdir -p "$APP_PATH"
fi

echo "Templating appConfig.js"
envsubst < /usr/local/app/templates/appConfig.js.template > $APP_PATH/appConfig.js
echo "Done"

echo "Done docker-entrypoint..."

exec "$@"
