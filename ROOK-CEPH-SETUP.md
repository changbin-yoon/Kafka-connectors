# Rook-Ceph S3 설정 가이드

이 가이드는 Kafka Sink Connector를 사용하여 Rook-Ceph Object Store (S3 호환)에 데이터를 저장하는 방법을 설명합니다.

## 사전 요구사항

1. **Rook-Ceph 클러스터** 실행 중
2. **Kafka 클러스터** 실행 중 (Strimzi 사용)
3. **Kafka Connect** 배포 준비 완료

## 1. Rook-Ceph Object Store 확인

### RGW 서비스 확인

```bash
kubectl get svc -n rook-ceph | grep rook-ceph-rgw
```

출력 예시:
```
rook-ceph-rgw-my-store    ClusterIP   10.96.x.x   <none>    80/TCP
```

### RGW 엔드포인트 확인

RGW 서비스 이름을 기반으로 엔드포인트를 구성합니다:
```
http://rook-ceph-rgw-my-store.rook-ceph.svc:80
```

## 2. Rook-Ceph S3 사용자 및 버킷 생성

### 자동 설정 스크립트 사용 (권장)

```bash
cd /Users/ycb/study/kafka-connectors
./scripts/setup-rook-ceph-s3.sh
```

### 수동 설정

#### 2.1 Rook-Ceph Tools Pod 찾기

```bash
kubectl get pods -n rook-ceph -l app=rook-ceph-tools
```

#### 2.2 S3 사용자 생성

```bash
TOOLS_POD=$(kubectl get pods -n rook-ceph -l app=rook-ceph-tools -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n rook-ceph -it $TOOLS_POD -- radosgw-admin user create \
  --uid=kafka-user \
  --display-name="Kafka User" \
  --email=kafka@example.com
```

출력에서 `access_key`와 `secret_key`를 기록해두세요.

#### 2.3 S3 버킷 생성

AWS CLI 또는 s3cmd를 사용하여 버킷을 생성합니다:

**AWS CLI 사용:**
```bash
RGW_ENDPOINT="http://rook-ceph-rgw-my-store.rook-ceph.svc:80"
ACCESS_KEY="your-access-key"
SECRET_KEY="your-secret-key"

aws --endpoint-url=$RGW_ENDPOINT s3 mb s3://kafka-data-bucket \
  --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY
```

**s3cmd 사용:**
```bash
s3cmd --access_key=$ACCESS_KEY --secret_key=$SECRET_KEY \
  --host=$RGW_ENDPOINT --host-bucket='%(bucket).s3' \
  mb s3://kafka-data-bucket
```

## 3. Kubernetes Secret 생성

```bash
kubectl create secret generic rook-ceph-s3-credentials \
  --from-literal=access-key="YOUR_ACCESS_KEY" \
  --from-literal=secret-key="YOUR_SECRET_KEY" \
  -n kafka
```

## 4. Kafka Connect 배포

### 4.1 Helm Chart 사용

```bash
helm install kafka-connectors ./helm/kafka-connectors \
  --namespace kafka \
  --create-namespace \
  -f helm/kafka-connectors/values-rook-ceph.yaml
```

### 4.2 Kubernetes 매니페스트 사용

먼저 `k8s/s3-sink-connector-rook-ceph.yaml` 파일을 수정하여 실제 RGW 엔드포인트를 입력합니다:

```yaml
s3.endpoint: "http://rook-ceph-rgw-my-store.rook-ceph.svc:80"
```

그리고 배포:

```bash
# KafkaConnect 배포
kubectl apply -f k8s/kafka-connect.yaml

# S3 Sink Connector 배포
kubectl apply -f k8s/s3-sink-connector-rook-ceph.yaml
```

## 5. Kafka Connect 설정 (환경 변수 주입)

KafkaConnect 리소스에 Secret을 환경 변수로 주입하려면 `kafka-connect.yaml`을 수정합니다:

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnect
metadata:
  name: kafka-connect-cluster
  namespace: kafka
spec:
  # ... 기존 설정 ...
  template:
    connectContainer:
      env:
        - name: ROOK_CEPH_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: rook-ceph-s3-credentials
              key: access-key
        - name: ROOK_CEPH_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: rook-ceph-s3-credentials
              key: secret-key
```

그리고 KafkaConnector에서 환경 변수를 참조:

```yaml
config:
  aws.access.key.id: "${ROOK_CEPH_ACCESS_KEY}"
  aws.secret.access.key: "${ROOK_CEPH_SECRET_KEY}"
```

## 6. 테스트

### 6.1 Kafka 토픽 생성

```bash
kubectl exec -it my-cluster-kafka-0 -n kafka -- \
  bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic kafka-data-topic \
  --partitions 3 \
  --replication-factor 1
```

### 6.2 테스트 메시지 전송

**Python 예제 사용:**
```bash
# Python kafka 라이브러리 설치
pip install kafka-python

# 예제 실행
python examples/kafka-producer-python.py
```

**Java 예제 사용:**
```bash
# Maven으로 컴파일 및 실행
cd examples
mvn compile exec:java -Dexec.mainClass="com.kafka.examples.KafkaProducerExample"
```

**kafka-console-producer 사용:**
```bash
kubectl exec -it my-cluster-kafka-0 -n kafka -- \
  bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic kafka-data-topic
```

메시지 입력 예시:
```json
{"id":1,"timestamp":"2024-01-01T00:00:00","value":100.5,"status":"active"}
```

### 6.3 Rook-Ceph에서 데이터 확인

**AWS CLI 사용:**
```bash
RGW_ENDPOINT="http://rook-ceph-rgw-my-store.rook-ceph.svc:80"
ACCESS_KEY="your-access-key"
SECRET_KEY="your-secret-key"

aws --endpoint-url=$RGW_ENDPOINT s3 ls s3://kafka-data-bucket/kafka-data/ --recursive \
  --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY
```

**s3cmd 사용:**
```bash
s3cmd --access_key=$ACCESS_KEY --secret_key=$SECRET_KEY \
  --host=$RGW_ENDPOINT --host-bucket='%(bucket).s3' \
  ls s3://kafka-data-bucket/kafka-data/
```

## 7. Connector 상태 확인

```bash
# Connector 상태 확인
kubectl get kafkaconnector s3-sink-connector-rook-ceph -n kafka

# 상세 정보 확인
kubectl describe kafkaconnector s3-sink-connector-rook-ceph -n kafka

# Kafka Connect Pod 로그 확인
kubectl logs -f kafka-connect-cluster-connect-0 -n kafka
```

## 8. 트러블슈팅

### 문제: Connector가 시작되지 않음

1. **RGW 엔드포인트 확인:**
   ```bash
   kubectl get svc -n rook-ceph | grep rgw
   ```

2. **네트워크 연결 확인:**
   ```bash
   kubectl exec -it kafka-connect-cluster-connect-0 -n kafka -- \
     curl -v http://rook-ceph-rgw-my-store.rook-ceph.svc:80
   ```

3. **Secret 확인:**
   ```bash
   kubectl get secret rook-ceph-s3-credentials -n kafka -o yaml
   ```

### 문제: 버킷에 데이터가 저장되지 않음

1. **버킷 존재 확인:**
   ```bash
   aws --endpoint-url=$RGW_ENDPOINT s3 ls s3://kafka-data-bucket \
     --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY
   ```

2. **권한 확인:**
   - 사용자에게 버킷에 대한 쓰기 권한이 있는지 확인

3. **Connector 로그 확인:**
   ```bash
   kubectl logs -f kafka-connect-cluster-connect-0 -n kafka | grep -i s3
   ```

### 문제: Path-style access 오류

Rook-Ceph는 일반적으로 path-style access를 사용합니다. 설정에서 다음을 확인하세요:

```yaml
s3.path.style.access: "true"
```

## 참고 자료

- [Rook-Ceph Documentation](https://rook.github.io/docs/rook/latest/)
- [Strimzi Kafka Connect Documentation](https://strimzi.io/docs/operators/latest/using.html#type-KafkaConnect-reference)
- [AWS S3 SDK for Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)

