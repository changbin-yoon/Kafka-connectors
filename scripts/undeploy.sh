#!/bin/bash

set -e

NAMESPACE="${NAMESPACE:-kafka}"
RELEASE_NAME="${RELEASE_NAME:-kafka-connectors}"

echo "=== Kafka Connectors 삭제 스크립트 ==="
echo "Namespace: $NAMESPACE"
echo "Release Name: $RELEASE_NAME"
echo ""

# Helm Chart 삭제
echo "1. Helm Chart 삭제 중..."
helm uninstall "$RELEASE_NAME" --namespace "$NAMESPACE" || true

# KafkaConnector 리소스 삭제
echo "2. KafkaConnector 리소스 삭제 중..."
kubectl delete kafkaconnector --all -n "$NAMESPACE" || true

# KafkaConnect 리소스 삭제
echo "3. KafkaConnect 리소스 삭제 중..."
kubectl delete kafkaconnect --all -n "$NAMESPACE" || true

echo ""
echo "=== 삭제 완료 ==="

