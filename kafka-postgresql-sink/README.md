# Strimzi Kafka Connect - PostgreSQL Sink 개발 & 테스트 가이드

이 디렉토리에는 Kubernetes 환경(Strimzi) 상에서 Kafka의 메시지를 PostgreSQL로 적재(Sink)하기 위한 필수 파일들이 포함되어 있습니다.

## 1. 구성 방법 선택 (플러그인 추가)
Kafka를 DB에 연결하려면 **JDBC Sink Connector (by Confluent)**와 **PostgreSQL JDBC Driver** 플러그인이 포함된 커스텀 Kafka Connect 이미지가 무조건 필요합니다.

두 가지 방식이 준비되어 있으니 상황에 맞는 방식을 선택하세요:

### 방식 A: 직접 Docker Build 하여 Push (권장)
제공된 `Dockerfile`을 사용하여 플러그인이 다운로드 된 커스텀 이미지를 로컬 또는 CI/CD 환경에서 직접 Build 하고, 개인/사내 레지스트리에 Push 합니다.
```bash
docker build -t <your-registry>/connect-jdbc-postgres:latest -f Dockerfile .
docker push <your-registry>/connect-jdbc-postgres:latest
```
이후 `kafka-connect.yaml` 안에 주석 처리된 `image: <your-registry>...` 주석을 풀고 `build:` 블록을 제거한 후 적용합니다.

### 방식 B: Strimzi Build 기능 사용
사용 중인 k8s 클러스터 내에 Strimzi Operator가 직접 플러그인 URL을 받아 이미지를 굽게(build) 만듭니다. `kafka-connect.yaml` 내 `build` 세션을 수정하세요. 단, 빌드된 이미지를 원격에 보관할 저장소(Registry) 정보(`image`, 필요시 `pushSecret`)를 기입해야 동작합니다.

---

## 2. 클러스터 배포

Connect 클러스터 및 설정을 변경한 후 쿠버네티스에 배포합니다:

```bash
# 1. Kafka Connect 리소스 배포
kubectl apply -f kafka-connect.yaml

# (커넥터가 모두 Running 상태가 될 때까지 대기합니다)
kubectl get kafkaconnect my-connect-cluster -w

# 2. PostgreSQL Sink 연동 시작
kubectl apply -f postgres-sink-connector.yaml

# 3. 배포된 커넥터 정상 동작 여부 확인
kubectl get kafkaconnector postgres-sink-connector -o yaml
```

## 3. 테스트 방법
1. Kafka Producer 툴(`kafka-console-producer` 혹은 애플리케이션 등)을 사용하여 `user-events` 토픽에 `json` 메시지를 발행합니다.
    - 단, 커넥터 설정에 따라 스키마(`key/value.converter.schemas.enable: true`)가 포함된 Payload JSON 형식이어야 합니다.
    - 스키마가 없는 일반적인 단순 JSON이라면 `kafka-connect.yaml`에서 `schemas.enable: false` 로 변경하셔야 합니다.
2. 지정한 PostgreSQL 데이터베이스에 접속 후 테이블(`kafka_user-events`)이 올바르게 생성되고, Insert 되는지 확인합니다.

> **에러 디버깅 TIP:**
> 문제가 생겼다면 `kubectl logs deployment/my-connect-cluster-connect` 로그를 확인하여 플러그인 충돌이 났는지, DB 권한/접속 문제인지 확인하면 원인 파악이 쉽습니다.
