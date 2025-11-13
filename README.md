# Kafka Connect Sink Connectors

Strimzi Kafka를 사용하는 환경에서 Kafka 메시지를 MSSQL과 S3로 전송하는 Sink Connector 프로젝트입니다.

## 개요

이 프로젝트는 다음 두 가지 Sink Connector를 제공합니다:

1. **MSSQL Sink Connector**: Kafka 토픽의 메시지를 Microsoft SQL Server 데이터베이스에 삽입
2. **S3 Sink Connector**: Kafka 토픽의 메시지를 Amazon S3에 저장

## 기술 스택

- **Kafka**: 4.0.0 (KRaft 모드)
- **Strimzi**: 0.48.0
- **Kubernetes**: 1.33.4
- **Java**: 17
- **Maven**: 프로젝트 빌드 도구
- **Docker**: 컨테이너 이미지 빌드
- **Helm**: Kubernetes 배포

## 프로젝트 구조

```
kafka-connectors/
├── mssql-sink-connector/      # MSSQL Sink Connector 모듈
│   ├── src/main/java/
│   └── pom.xml
├── s3-sink-connector/         # S3 Sink Connector 모듈
│   ├── src/main/java/
│   └── pom.xml
├── docker/                    # Docker 이미지 빌드 파일
│   ├── Dockerfile.mssql-sink
│   ├── Dockerfile.s3-sink
│   └── build.sh
├── helm/                      # Helm Chart
│   └── kafka-connectors/
├── k8s/                       # Kubernetes 매니페스트 파일
│   ├── kafka-connect.yaml
│   ├── mssql-sink-connector.yaml
│   └── s3-sink-connector.yaml
└── pom.xml                    # 부모 POM 파일
```

## 빌드

### 사전 요구사항

- Java 17 이상
- Maven 3.6 이상
- Docker
- Kubernetes 클러스터 (Strimzi 설치 필요)

### Maven 빌드

```bash
mvn clean package
```

### Docker 이미지 빌드

```bash
# 전체 빌드 스크립트 실행
./docker/build.sh

# 또는 개별 빌드
docker build -f docker/Dockerfile.mssql-sink -t mssql-sink-connector:1.0.0 .
docker build -f docker/Dockerfile.s3-sink -t s3-sink-connector:1.0.0 .
```

## 배포

### Helm을 사용한 배포

1. **values.yaml 수정**

   `helm/kafka-connectors/values.yaml` 파일을 환경에 맞게 수정합니다.

2. **Helm Chart 배포**

   ```bash
   helm install kafka-connectors ./helm/kafka-connectors \
     --namespace kafka \
     --create-namespace \
     -f helm/kafka-connectors/values.yaml
   ```

3. **배포 확인**

   ```bash
   kubectl get kafkaconnect -n kafka
   kubectl get kafkaconnector -n kafka
   ```

### Kubernetes 매니페스트를 사용한 배포

1. **KafkaConnect 리소스 배포**

   ```bash
   kubectl apply -f k8s/kafka-connect.yaml
   ```

2. **Connector 리소스 배포**

   ```bash
   kubectl apply -f k8s/mssql-sink-connector.yaml
   kubectl apply -f k8s/s3-sink-connector.yaml
   ```

## 설정

### MSSQL Sink Connector 설정

| 설정 항목 | 설명 | 기본값 |
|---------|------|--------|
| `connection.url` | MSSQL 연결 URL | 필수 |
| `connection.user` | 데이터베이스 사용자명 | 필수 |
| `connection.password` | 데이터베이스 비밀번호 | 필수 |
| `table.name` | 데이터 삽입 테이블명 | 필수 |
| `topics` | 소비할 Kafka 토픽 | 필수 |
| `batch.size` | 배치 크기 | 100 |
| `max.retries` | 최대 재시도 횟수 | 3 |
| `retry.backoff.ms` | 재시도 대기 시간 (ms) | 1000 |

### S3 Sink Connector 설정

| 설정 항목 | 설명 | 기본값 |
|---------|------|--------|
| `s3.bucket` | S3 버킷 이름 | 필수 |
| `s3.region` | AWS 리전 | 필수 |
| `s3.prefix` | S3 객체 키 접두사 | "" |
| `aws.access.key.id` | AWS 액세스 키 ID | 필수 |
| `aws.secret.access.key` | AWS 시크릿 액세스 키 | 필수 |
| `topics` | 소비할 Kafka 토픽 | 필수 |
| `flush.size` | 파일 저장 전 버퍼 크기 | 1000 |
| `rotate.interval.ms` | 파일 회전 간격 (ms) | 3600000 |
| `file.format` | 파일 형식 (json/csv) | json |
| `compression.type` | 압축 형식 (none/gzip) | none |

## 데이터베이스 설정

### MSSQL 테이블 생성

MSSQL 데이터베이스에 다음 스크립트를 실행하여 테이블을 생성합니다:

```sql
-- k8s/mssql-table.sql 참조
```

또는 직접 실행:

```bash
kubectl exec -it <mssql-pod> -n <namespace> -- \
  /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P <password> \
  -i /path/to/mssql-table.sql
```

## 보안

### Secret 사용

민감한 정보(비밀번호, 액세스 키 등)는 Kubernetes Secret을 사용하는 것을 권장합니다.

```bash
# MSSQL 비밀번호 Secret 생성
kubectl create secret generic mssql-credentials \
  --from-literal=connection.password=<password> \
  -n kafka

# S3 자격 증명 Secret 생성
kubectl create secret generic s3-credentials \
  --from-literal=aws-access-key-id=<access-key> \
  --from-literal=aws-secret-access-key=<secret-key> \
  -n kafka
```

Helm values.yaml에서 `secrets.enabled: true`로 설정하면 Secret을 사용할 수 있습니다.

## 모니터링

### 로그 확인

```bash
# KafkaConnect Pod 로그 확인
kubectl logs -f <kafka-connect-pod-name> -n kafka

# Connector 상태 확인
kubectl describe kafkaconnector mssql-sink-connector -n kafka
kubectl describe kafkaconnector s3-sink-connector -n kafka
```

### 메트릭

메트릭은 JMX Prometheus Exporter를 통해 수집할 수 있습니다. `values.yaml`에서 `metrics.enabled: true`로 설정하면 활성화됩니다.

## 트러블슈팅

### Connector가 시작되지 않는 경우

1. KafkaConnect 리소스 상태 확인:
   ```bash
   kubectl get kafkaconnect -n kafka
   kubectl describe kafkaconnect kafka-connect-cluster -n kafka
   ```

2. Pod 로그 확인:
   ```bash
   kubectl logs -f <kafka-connect-pod> -n kafka
   ```

3. Connector 설정 확인:
   ```bash
   kubectl get kafkaconnector -n kafka -o yaml
   ```

### 데이터가 삽입되지 않는 경우

1. 데이터베이스 연결 확인
2. 테이블 존재 여부 확인
3. 토픽에 메시지가 있는지 확인:
   ```bash
   kubectl exec -it <kafka-pod> -n kafka -- \
     bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic <topic-name> \
     --from-beginning
   ```

## 라이선스

이 프로젝트는 Apache License 2.0을 따릅니다.

## 참고 자료

- [Strimzi Documentation](https://strimzi.io/documentation/)
- [Kafka Connect Documentation](https://kafka.apache.org/documentation/#connect)
- [Strimzi KafkaConnect API](https://strimzi.io/docs/operators/latest/using.html#type-KafkaConnect-reference)

