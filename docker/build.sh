#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "Building Maven projects..."
mvn clean package -DskipTests

echo "Building MSSQL Sink Connector Docker image..."
docker build -f docker/Dockerfile.mssql-sink \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    -t mssql-sink-connector:1.0.0 \
    .

echo "Building S3 Sink Connector Docker image..."
docker build -f docker/Dockerfile.s3-sink \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    -t s3-sink-connector:1.0.0 \
    .

echo "Docker images built successfully!"
echo "  - mssql-sink-connector:1.0.0"
echo "  - s3-sink-connector:1.0.0"

