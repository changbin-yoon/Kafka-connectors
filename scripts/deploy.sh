#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

NAMESPACE="${NAMESPACE:-kafka}"
RELEASE_NAME="${RELEASE_NAME:-kafka-connectors}"

cd "$PROJECT_ROOT"

echo "=== Kafka Connectors 배포 스크립트 ==="
echo "Namespace: $NAMESPACE"
echo "Release Name: $RELEASE_NAME"
echo ""

# Namespace 생성
echo "1. Namespace 생성 중..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Helm Chart 배포
echo "2. Helm Chart 배포 중..."
helm upgrade --install "$RELEASE_NAME" ./helm/kafka-connectors \
  --namespace "$NAMESPACE" \
  --wait \
  --timeout 10m

# 배포 상태 확인
echo "3. 배포 상태 확인 중..."
kubectl get kafkaconnect -n "$NAMESPACE"
kubectl get kafkaconnector -n "$NAMESPACE"
kubectl get pods -n "$NAMESPACE" -l app=kafka-connect

echo ""
echo "=== 배포 완료 ==="
echo "로그 확인: kubectl logs -f <pod-name> -n $NAMESPACE"
echo "상태 확인: kubectl get kafkaconnector -n $NAMESPACE"

