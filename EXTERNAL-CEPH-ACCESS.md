# 물리적으로 분리된 클러스터에서 Ceph RGW 접근하기

Ceph 클러스터와 Test 클러스터가 물리적으로 분리되어 있을 때 NodePort나 Ingress를 통해 접근하는 방법입니다.

## 방법 1: NodePort 사용

### 1. Ceph 클러스터에서 NodePort 서비스 생성

```bash
# NodePort 서비스 적용
kubectl apply -f k8s/rook-ceph-rgw-nodeport.yaml -n rook-ceph

# NodePort 확인
kubectl get svc rook-ceph-rgw-nodeport -n rook-ceph

# 출력 예시:
# NAME                      TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
# rook-ceph-rgw-nodeport    NodePort   10.96.123.45    <none>        80:30080/TCP   1m
```

### 2. Ceph 클러스터의 Node IP 확인

```bash
# Ceph 클러스터의 모든 노드 IP 확인
kubectl get nodes -o wide

# 또는 특정 노드의 외부 IP 확인
kubectl get node <node-name> -o jsonpath='{.status.addresses[?(@.type=="ExternalIP")].address}'
```

### 3. Test 클러스터에서 접근

NodePort를 사용하면 `<NODE_IP>:<NODE_PORT>` 형식으로 접근할 수 있습니다.

```bash
# 예시: Ceph 클러스터의 노드 IP가 192.168.1.100이고 NodePort가 30080인 경우
export EXTERNAL_RGW_ENDPOINT="192.168.1.100:30080"

# 또는 http:// 포함하여
export EXTERNAL_RGW_ENDPOINT="http://192.168.1.100:30080"
```

### 4. 스크립트 실행 시 외부 엔드포인트 사용

```bash
# 설정 스크립트 실행 시 외부 엔드포인트 지정
EXTERNAL_RGW_ENDPOINT="192.168.1.100:30080" ./scripts/setup-rook-ceph-s3.sh
```

### 5. Helm values 파일 설정

`helm/kafka-connectors/values-rook-ceph.yaml` 파일에서:

```yaml
s3Sink:
  enabled: true
  connector:
    config:
      s3.endpoint: "http://192.168.1.100:30080"  # NodePort 엔드포인트
      s3.path.style.access: "true"  # 필수!
```

## 방법 2: Ingress 사용

### 1. Ceph 클러스터에 Ingress Controller 설치 확인

```bash
# Nginx Ingress Controller 확인
kubectl get pods -n ingress-nginx

# 또는 Traefik 확인
kubectl get pods -n traefik
```

### 2. RGW 서비스 이름 확인

```bash
kubectl get svc -n rook-ceph | grep rook-ceph-rgw
```

### 3. Ingress 설정 파일 수정

`k8s/rook-ceph-rgw-ingress.yaml` 파일을 편집:

```yaml
spec:
  ingressClassName: nginx  # 실제 사용하는 Ingress Controller
  rules:
  - host: ceph-rgw.example.com  # 실제 도메인 이름으로 변경
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: rook-ceph-rgw-my-store  # 위에서 확인한 실제 서비스 이름
            port:
              number: 80
```

### 4. Ingress 적용

```bash
kubectl apply -f k8s/rook-ceph-rgw-ingress.yaml -n rook-ceph

# Ingress 상태 확인
kubectl get ingress -n rook-ceph
```

### 5. DNS 설정 또는 /etc/hosts 설정

Ceph 클러스터의 Ingress Controller 외부 IP를 확인하고, 호스트 이름을 해당 IP로 매핑:

```bash
# Ingress Controller 외부 IP 확인
kubectl get svc -n ingress-nginx

# /etc/hosts 파일에 추가 (Test 클러스터에서)
echo "<INGRESS_IP> ceph-rgw.example.com" | sudo tee -a /etc/hosts
```

### 6. Test 클러스터에서 접근

```bash
# 설정 스크립트 실행 시 Ingress 호스트 사용
EXTERNAL_RGW_ENDPOINT="http://ceph-rgw.example.com" ./scripts/setup-rook-ceph-s3.sh
```

### 7. Helm values 파일 설정

```yaml
s3Sink:
  enabled: true
  connector:
    config:
      s3.endpoint: "http://ceph-rgw.example.com"  # Ingress 호스트
      s3.path.style.access: "true"  # 필수!
```

## 주의사항

### 1. Path-Style Access 필수

물리적으로 분리된 클러스터에서 접근할 때는 반드시 `s3.path.style.access: "true"`를 설정해야 합니다.

### 2. 방화벽 설정

- **NodePort 사용 시**: Ceph 클러스터의 노드에서 NodePort 포트(30000-32767 범위)를 열어야 합니다.
- **Ingress 사용 시**: Ingress Controller의 포트(보통 80, 443)를 열어야 합니다.

### 3. 네트워크 연결

Test 클러스터에서 Ceph 클러스터의 Node IP나 Ingress Controller IP로 네트워크 접근이 가능해야 합니다.

### 4. 보안 고려사항

- 프로덕션 환경에서는 Ingress에 TLS를 설정하는 것을 권장합니다.
- Access Key와 Secret Key는 Kubernetes Secret으로 관리하고 환경 변수로 주입하세요.

## 테스트

### 연결 테스트

```bash
# NodePort 사용 시
curl -v http://<NODE_IP>:<NODE_PORT>

# Ingress 사용 시
curl -v http://ceph-rgw.example.com

# S3 API 테스트 (AWS CLI 사용)
aws --endpoint-url=http://<ENDPOINT> s3 ls \
  --access-key=<ACCESS_KEY> \
  --secret-key=<SECRET_KEY>
```

### 버킷 생성 테스트

```bash
export RGW_ENDPOINT="http://<NODE_IP>:<NODE_PORT>"  # 또는 Ingress 호스트
export ACCESS_KEY="your-access-key"
export SECRET_KEY="your-secret-key"

aws --endpoint-url=$RGW_ENDPOINT s3 mb s3://test-bucket \
  --access-key=$ACCESS_KEY \
  --secret-key=$SECRET_KEY
```

## 문제 해결

### 연결 실패

```bash
# Ceph 클러스터에서 RGW 파드 확인
kubectl get pods -n rook-ceph | grep rgw

# RGW 파드 로그 확인
kubectl logs -n rook-ceph <rgw-pod-name>

# 서비스 엔드포인트 확인
kubectl get endpoints -n rook-ceph rook-ceph-rgw-*
```

### NodePort 접근 불가

```bash
# NodePort 서비스 상태 확인
kubectl describe svc rook-ceph-rgw-nodeport -n rook-ceph

# 파드의 selector 확인
kubectl get pods -n rook-ceph -l app=rook-ceph-rgw --show-labels
```

### Ingress 접근 불가

```bash
# Ingress 상태 확인
kubectl describe ingress rook-ceph-rgw-ingress -n rook-ceph

# Ingress Controller 로그 확인
kubectl logs -n ingress-nginx <ingress-controller-pod>
```

