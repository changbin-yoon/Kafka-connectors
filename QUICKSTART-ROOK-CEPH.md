# Rook-Ceph S3 연동 Quick Start

Kafka Sink Connector를 사용하여 Rook-Ceph Object Store에 데이터를 저장하는 빠른 시작 가이드입니다.

## 1. Rook-Ceph S3 설정

### RGW 서비스 확인

```bash
kubectl get svc -n rook-ceph | grep rgw
```

출력 예시:
```
rook-ceph-rgw-my-store    ClusterIP   10.96.x.x   <none>    80/TCP
```

### 자동 설정 스크립트 실행

```bash
cd /Users/ycb/study/kafka-connectors
./scripts/setup-rook-ceph-s3.sh
```

이 스크립트는:
- S3 사용자 생성
- 버킷 생성
- Kubernetes Secret 생성

## 2. 설정 파일 수정

`helm/kafka-connectors/values-rook-ceph.yaml` 파일에서 실제 RGW 엔드포인트를 수정:

```yaml
s3.endpoint: "http://rook-ceph-rgw-my-store.rook-ceph.svc:80"  # 실제 서비스 이름으로 변경
```

## 3. Kafka Connect 배포

```bash
helm install kafka-connectors ./helm/kafka-connectors \
  --namespace kafka \
  --create-namespace \
  -f helm/kafka-connectors/values-rook-ceph.yaml
```

## 4. 테스트

### 토픽 생성

```bash
kubectl exec -it my-cluster-kafka-0 -n kafka -- \
  bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic kafka-data-topic \
  --partitions 3 \
  --replication-factor 1
```

### 메시지 전송 (Python)

```bash
pip install kafka-python
python examples/kafka-producer-python.py
```

### Rook-Ceph에서 확인

```bash
RGW_ENDPOINT="http://rook-ceph-rgw-my-store.rook-ceph.svc:80"
ACCESS_KEY="your-access-key"
SECRET_KEY="your-secret-key"

aws --endpoint-url=$RGW_ENDPOINT s3 ls s3://kafka-data-bucket/kafka-data/ --recursive \
  --access-key=$ACCESS_KEY --secret-key=$SECRET_KEY
```

## 5. 상태 확인

```bash
# Connector 상태
kubectl get kafkaconnector -n kafka

# Pod 로그
kubectl logs -f kafka-connect-cluster-connect-0 -n kafka
```

## 6. 물리적으로 분리된 클러스터에서 접근

Ceph 클러스터와 Test 클러스터가 물리적으로 분리되어 있는 경우, NodePort나 Ingress를 통해 접근할 수 있습니다.

자세한 내용은 [EXTERNAL-CEPH-ACCESS.md](./EXTERNAL-CEPH-ACCESS.md)를 참조하세요.

### 빠른 예시 (NodePort 사용)

```bash
# 1. Ceph 클러스터에서 NodePort 서비스 생성
kubectl apply -f k8s/rook-ceph-rgw-nodeport.yaml -n rook-ceph

# 2. NodePort 확인
kubectl get svc rook-ceph-rgw-nodeport -n rook-ceph

# 3. Test 클러스터에서 스크립트 실행 (외부 엔드포인트 지정)
EXTERNAL_RGW_ENDPOINT="<NODE_IP>:<NODE_PORT>" ./scripts/setup-rook-ceph-s3.sh

# 4. Helm values 파일에서 endpoint 수정
# s3.endpoint: "http://<NODE_IP>:<NODE_PORT>"
```

## 상세 가이드

- 물리적으로 분리된 클러스터 접근: [EXTERNAL-CEPH-ACCESS.md](./EXTERNAL-CEPH-ACCESS.md)
- 더 자세한 내용은 [ROOK-CEPH-SETUP.md](./ROOK-CEPH-SETUP.md)를 참조하세요.

