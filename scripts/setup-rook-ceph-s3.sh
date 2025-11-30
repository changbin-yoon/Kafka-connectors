#!/bin/bash

set -e

# Rook-Ceph S3 설정 스크립트
# 이 스크립트는 Rook-Ceph Object Store에 사용자와 버킷을 생성합니다
# 물리적으로 분리된 클러스터에서 접근 시 NodePort나 Ingress를 통해 접근 가능합니다

NAMESPACE="${ROOK_CEPH_NAMESPACE:-rook-ceph}"
KAFKA_NAMESPACE="${KAFKA_NAMESPACE:-kafka}"
BUCKET_NAME="${BUCKET_NAME:-kafka-data-bucket}"
USER_NAME="${USER_NAME:-kafka-user}"

# 외부 접근 설정 (물리적으로 분리된 클러스터용)
# NodePort를 사용하는 경우: <NODE_IP>:<NODE_PORT> 형식 (예: 192.168.1.100:30080)
# Ingress를 사용하는 경우: <INGRESS_HOST> 형식 (예: ceph-rgw.example.com)
EXTERNAL_RGW_ENDPOINT="${EXTERNAL_RGW_ENDPOINT:-}"

echo "=== Rook-Ceph S3 설정 스크립트 ==="
echo "Rook-Ceph Namespace: $NAMESPACE"
echo "Kafka Namespace: $KAFKA_NAMESPACE"
echo "Bucket Name: $BUCKET_NAME"
echo "User Name: $USER_NAME"
if [ -n "$EXTERNAL_RGW_ENDPOINT" ]; then
  echo "External RGW Endpoint: $EXTERNAL_RGW_ENDPOINT"
fi
echo ""

# Rook-Ceph Tools Pod 찾기
TOOLS_POD=$(kubectl get pods -n $NAMESPACE -l app=rook-ceph-tools -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -z "$TOOLS_POD" ]; then
    echo "Error: Rook-Ceph tools pod not found in namespace $NAMESPACE"
    echo "Please ensure Rook-Ceph is installed and tools pod is running"
    exit 1
fi

echo "Found Rook-Ceph tools pod: $TOOLS_POD"
echo ""

# 1. S3 사용자 생성
echo "1. Creating S3 user: $USER_NAME"
USER_OUTPUT=$(kubectl exec -n $NAMESPACE $TOOLS_POD -- radosgw-admin user create \
    --uid=$USER_NAME \
    --display-name="Kafka User" \
    --email=kafka@example.com 2>&1 || true)

if echo "$USER_OUTPUT" | grep -q "already exists"; then
    echo "User $USER_NAME already exists, skipping creation"
    # 기존 사용자 정보 가져오기
    USER_INFO=$(kubectl exec -n $NAMESPACE $TOOLS_POD -- radosgw-admin user info --uid=$USER_NAME)
else
    echo "User created successfully"
    USER_INFO="$USER_OUTPUT"
fi

# Access Key와 Secret Key 추출
ACCESS_KEY=$(echo "$USER_INFO" | grep -o '"access_key": "[^"]*' | cut -d'"' -f4)
SECRET_KEY=$(echo "$USER_INFO" | grep -o '"secret_key": "[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_KEY" ] || [ -z "$SECRET_KEY" ]; then
    echo "Error: Failed to extract access key or secret key"
    exit 1
fi

echo "Access Key: $ACCESS_KEY"
echo "Secret Key: ${SECRET_KEY:0:10}..." # 보안을 위해 일부만 표시
echo ""

# 2. S3 버킷 생성
echo "2. Creating S3 bucket: $BUCKET_NAME"

# RGW 엔드포인트 설정
# 외부 엔드포인트가 지정된 경우 우선 사용, 아니면 내부 서비스 이름 사용
if [ -n "$EXTERNAL_RGW_ENDPOINT" ]; then
  # 외부 엔드포인트가 http:// 또는 https://로 시작하지 않으면 자동 추가
  if [[ ! "$EXTERNAL_RGW_ENDPOINT" =~ ^https?:// ]]; then
    RGW_ENDPOINT="http://${EXTERNAL_RGW_ENDPOINT}"
  else
    RGW_ENDPOINT="$EXTERNAL_RGW_ENDPOINT"
  fi
  echo "Using external RGW endpoint: $RGW_ENDPOINT"
else
  # 내부 서비스 이름 사용
  RGW_SERVICE=$(kubectl get svc -n $NAMESPACE | grep rook-ceph-rgw | head -1 | awk '{print $1}')
  if [ -z "$RGW_SERVICE" ]; then
    echo "Error: RGW service not found in namespace $NAMESPACE"
    echo "Please set EXTERNAL_RGW_ENDPOINT environment variable for external access"
    exit 1
  fi
  RGW_ENDPOINT="http://${RGW_SERVICE}.${NAMESPACE}.svc:80"
  echo "Using internal RGW endpoint: $RGW_ENDPOINT"
fi

# AWS CLI 또는 s3cmd를 사용하여 버킷 생성
if command -v aws &> /dev/null; then
    echo "Using AWS CLI to create bucket..."
    aws --endpoint-url=$RGW_ENDPOINT s3 mb s3://$BUCKET_NAME \
        --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY 2>&1 || \
    echo "Bucket may already exist or error occurred"
elif command -v s3cmd &> /dev/null; then
    echo "Using s3cmd to create bucket..."
    s3cmd --access_key=$ACCESS_KEY --secret_key=$SECRET_KEY \
        --host=$RGW_ENDPOINT --host-bucket='%(bucket).s3' \
        mb s3://$BUCKET_NAME 2>&1 || \
    echo "Bucket may already exist or error occurred"
else
    echo "Warning: Neither AWS CLI nor s3cmd found. Please create bucket manually:"
    echo "  aws --endpoint-url=$RGW_ENDPOINT s3 mb s3://$BUCKET_NAME --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY"
fi

echo ""

# 3. Kubernetes Secret 생성
echo "3. Creating Kubernetes Secret in namespace $KAFKA_NAMESPACE"

kubectl create namespace $KAFKA_NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic rook-ceph-s3-credentials \
    --from-literal=access-key="$ACCESS_KEY" \
    --from-literal=secret-key="$SECRET_KEY" \
    -n $KAFKA_NAMESPACE \
    --dry-run=client -o yaml | kubectl apply -f -

echo "Secret created successfully"
echo ""

# 4. 설정 정보 출력
echo "=== 설정 완료 ==="
echo ""
echo "다음 정보를 Kafka Connect 설정에 사용하세요:"
echo ""
echo "S3 Endpoint: $RGW_ENDPOINT"
echo "Bucket Name: $BUCKET_NAME"
echo "Access Key: $ACCESS_KEY"
echo "Secret Key: $SECRET_KEY"
echo ""
echo "Kafka Connect Connector 설정 예시 (values-rook-ceph.yaml):"
echo "  s3.bucket: $BUCKET_NAME"
echo "  s3.region: us-east-1"
echo "  s3.endpoint: $RGW_ENDPOINT"
echo "  s3.path.style.access: true"
echo "  aws.access.key.id: $ACCESS_KEY"
echo "  aws.secret.access.key: $SECRET_KEY"
echo ""
if [ -n "$EXTERNAL_RGW_ENDPOINT" ]; then
  echo "⚠️  외부 엔드포인트를 사용 중입니다. Test 클러스터에서 이 엔드포인트로 접근 가능합니다."
else
  echo "💡 물리적으로 분리된 클러스터에서 접근하려면:"
  echo "   1. NodePort 사용: EXTERNAL_RGW_ENDPOINT=<NODE_IP>:<NODE_PORT>"
  echo "   2. Ingress 사용: EXTERNAL_RGW_ENDPOINT=http://<INGRESS_HOST>"
  echo "   자세한 내용은 k8s/rook-ceph-rgw-nodeport.yaml 또는 k8s/rook-ceph-rgw-ingress.yaml 참조"
fi
echo ""

