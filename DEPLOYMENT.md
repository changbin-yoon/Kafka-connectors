# 배포 가이드

이 문서는 Kafka Connect Sink Connectors를 Kubernetes 클러스터에 배포하는 방법을 설명합니다.

## 사전 요구사항

1. **Kubernetes 클러스터** (버전 1.33.4)
2. **Strimzi Operator** 설치 및 실행 중
3. **Kafka 클러스터** 실행 중 (Strimzi로 배포)
4. **kubectl** 및 **helm** CLI 도구 설치
5. **Docker** 이미지 레지스트리 접근 권한

## 1. Strimzi Operator 설치 확인

```bash
kubectl get pods -n kafka
# 또는 Strimzi가 설치된 네임스페이스 확인
```

Strimzi Operator가 설치되어 있지 않은 경우:

```bash
kubectl create namespace kafka
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
```

## 2. Kafka 클러스터 확인

Kafka 클러스터가 실행 중인지 확인:

```bash
kubectl get kafka -n kafka
kubectl get pods -n kafka -l strimzi.io/kind=Kafka
```

## 3. 프로젝트 빌드

### Maven 빌드

```bash
cd kafka-connectors
mvn clean package -DskipTests
```

### Docker 이미지 빌드

```bash
# 빌드 스크립트 실행
./docker/build.sh

# 또는 개별 빌드
docker build -f docker/Dockerfile.mssql-sink -t mssql-sink-connector:1.0.0 .
docker build -f docker/Dockerfile.s3-sink -t s3-sink-connector:1.0.0 .
```

### 이미지 레지스트리에 푸시

```bash
# 예: Docker Hub
docker tag mssql-sink-connector:1.0.0 <registry>/mssql-sink-connector:1.0.0
docker push <registry>/mssql-sink-connector:1.0.0

docker tag s3-sink-connector:1.0.0 <registry>/s3-sink-connector:1.0.0
docker push <registry>/s3-sink-connector:1.0.0
```

## 4. 설정 파일 수정

### Helm values.yaml 수정

`helm/kafka-connectors/values.yaml` 파일을 환경에 맞게 수정:

```yaml
kafkaConnect:
  name: kafka-connect-cluster
  namespace: kafka
  bootstrapServers: my-cluster-kafka-bootstrap:9092  # 실제 Kafka 클러스터 이름으로 변경
  image:
    repository: <registry>/mssql-sink-connector  # 또는 s3-sink-connector
    tag: "1.0.0"

mssqlSink:
  connector:
    config:
      connection.url: "jdbc:sqlserver://mssql-service:1433;databaseName=kafka_db"
      connection.user: "kafka_user"
      connection.password: "kafka_password"  # Secret 사용 권장
      table.name: "kafka_messages"
      topics: "source-topic"  # 실제 토픽 이름으로 변경

s3Sink:
  connector:
    config:
      s3.bucket: "kafka-data-bucket"
      s3.region: "ap-northeast-2"
      aws.access.key.id: "YOUR_ACCESS_KEY"  # Secret 사용 권장
      aws.secret.access.key: "YOUR_SECRET_KEY"  # Secret 사용 권장
      topics: "source-topic"  # 실제 토픽 이름으로 변경
```

## 5. Secret 생성 (선택사항, 권장)

민감한 정보는 Kubernetes Secret을 사용하는 것을 권장합니다.

### MSSQL 비밀번호 Secret

```bash
kubectl create secret generic mssql-credentials \
  --from-literal=connection.password='your-password' \
  -n kafka
```

### S3 자격 증명 Secret

```bash
kubectl create secret generic s3-credentials \
  --from-literal=aws-access-key-id='your-access-key' \
  --from-literal=aws-secret-access-key='your-secret-key' \
  -n kafka
```

Secret을 사용하려면 `values.yaml`에서:

```yaml
secrets:
  mssql:
    enabled: true
  s3:
    enabled: true
```

## 6. MSSQL 데이터베이스 준비

MSSQL Sink Connector를 사용하는 경우, 데이터베이스와 테이블을 먼저 생성해야 합니다.

```bash
# MSSQL Pod에 접속하여 스크립트 실행
kubectl exec -it <mssql-pod> -n <namespace> -- \
  /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P <password> \
  -i /path/to/k8s/mssql-table.sql
```

또는 직접 SQL 실행:

```sql
USE kafka_db;
GO

CREATE TABLE kafka_messages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    message_key NVARCHAR(MAX),
    message_value NVARCHAR(MAX),
    topic NVARCHAR(255) NOT NULL,
    partition INT NOT NULL,
    offset BIGINT NOT NULL,
    timestamp DATETIME2 NOT NULL,
    created_at DATETIME2 DEFAULT GETDATE()
);
GO
```

## 7. 배포

### Helm을 사용한 배포 (권장)

```bash
# 배포 스크립트 사용
./scripts/deploy.sh

# 또는 직접 Helm 명령 실행
helm install kafka-connectors ./helm/kafka-connectors \
  --namespace kafka \
  --create-namespace \
  -f helm/kafka-connectors/values.yaml
```

### Kubernetes 매니페스트를 사용한 배포

```bash
# KafkaConnect 배포
kubectl apply -f k8s/kafka-connect.yaml

# Connector 배포
kubectl apply -f k8s/mssql-sink-connector.yaml
kubectl apply -f k8s/s3-sink-connector.yaml
```

## 8. 배포 확인

### 리소스 상태 확인

```bash
# KafkaConnect 상태
kubectl get kafkaconnect -n kafka
kubectl describe kafkaconnect kafka-connect-cluster -n kafka

# KafkaConnector 상태
kubectl get kafkaconnector -n kafka
kubectl describe kafkaconnector mssql-sink-connector -n kafka
kubectl describe kafkaconnector s3-sink-connector -n kafka

# Pod 상태
kubectl get pods -n kafka -l strimzi.io/kind=KafkaConnect
```

### 로그 확인

```bash
# KafkaConnect Pod 로그
kubectl logs -f <kafka-connect-pod-name> -n kafka

# 특정 Connector 로그 필터링
kubectl logs -f <kafka-connect-pod-name> -n kafka | grep -i "mssql\|s3"
```

### Connector 상태 확인 (REST API)

```bash
# KafkaConnect 서비스 포트 포워딩
kubectl port-forward svc/kafka-connect-cluster-connect-api 8083:8083 -n kafka

# 다른 터미널에서
curl http://localhost:8083/connectors
curl http://localhost:8083/connectors/mssql-sink-connector/status
curl http://localhost:8083/connectors/s3-sink-connector/status
```

## 9. 테스트

### 테스트 메시지 전송

```bash
# Kafka Pod에 접속하여 테스트 메시지 전송
kubectl exec -it <kafka-pod> -n kafka -- \
  bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic source-topic

# 메시지 입력 후 Ctrl+D로 종료
```

### 데이터 확인

**MSSQL:**
```bash
kubectl exec -it <mssql-pod> -n <namespace> -- \
  /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P <password> \
  -Q "SELECT TOP 10 * FROM kafka_db.dbo.kafka_messages ORDER BY id DESC"
```

**S3:**
```bash
aws s3 ls s3://kafka-data-bucket/kafka-data/ --recursive
```

## 10. 업데이트

### Connector 설정 업데이트

```bash
# values.yaml 수정 후
helm upgrade kafka-connectors ./helm/kafka-connectors \
  --namespace kafka \
  -f helm/kafka-connectors/values.yaml

# 또는 직접 Kubernetes 리소스 수정
kubectl edit kafkaconnector mssql-sink-connector -n kafka
```

### 이미지 업데이트

```bash
# 새 이미지 빌드 및 푸시 후
helm upgrade kafka-connectors ./helm/kafka-connectors \
  --namespace kafka \
  --set kafkaConnect.image.tag=1.0.1
```

## 11. 삭제

```bash
# 배포 스크립트 사용
./scripts/undeploy.sh

# 또는 직접 Helm 명령 실행
helm uninstall kafka-connectors -n kafka

# Connector 리소스 삭제
kubectl delete kafkaconnector --all -n kafka
kubectl delete kafkaconnect --all -n kafka
```

## 트러블슈팅

### Pod가 시작되지 않는 경우

1. 이미지가 올바르게 빌드되고 레지스트리에 푸시되었는지 확인
2. 이미지 Pull 정책 확인 (`imagePullPolicy`)
3. 리소스 제한 확인

### Connector가 실행되지 않는 경우

1. KafkaConnect 리소스 상태 확인
2. Pod 로그 확인
3. Connector 설정 검증
4. Kafka 클러스터 연결 확인

### 데이터가 처리되지 않는 경우

1. 토픽에 메시지가 있는지 확인
2. Connector가 올바른 토픽을 구독하는지 확인
3. 데이터베이스/S3 연결 확인
4. 권한 확인

## 참고 자료

- [Strimzi Documentation](https://strimzi.io/documentation/)
- [Kafka Connect Documentation](https://kafka.apache.org/documentation/#connect)
- [Helm Documentation](https://helm.sh/docs/)

