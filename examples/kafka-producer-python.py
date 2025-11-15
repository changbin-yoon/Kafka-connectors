#!/usr/bin/env python3
"""
Kafka Producer 예제 (Python)
Kafka 토픽에 메시지를 전송하여 S3 Sink Connector가 Rook-Ceph에 저장하도록 테스트
"""

from kafka import KafkaProducer
import json
import time
import random
from datetime import datetime

# Kafka 설정
BOOTSTRAP_SERVERS = 'my-cluster-kafka-bootstrap:9092'
TOPIC_NAME = 'kafka-data-topic'

def create_sample_message(index):
    """샘플 메시지 생성"""
    return {
        "id": index,
        "timestamp": datetime.now().isoformat(),
        "value": round(random.uniform(0, 100), 2),
        "status": "active" if random.choice([True, False]) else "inactive",
        "data": {
            "field1": f"value{index}",
            "field2": random.randint(0, 1000)
        }
    }

def main():
    # Kafka Producer 생성
    producer = KafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None,
        acks='all',
        retries=3
    )
    
    try:
        print(f"Starting to send messages to topic: {TOPIC_NAME}")
        
        # 100개의 테스트 메시지 전송
        for i in range(100):
            key = f"key-{i}"
            message = create_sample_message(i)
            
            # 메시지 전송
            future = producer.send(TOPIC_NAME, key=key, value=message)
            
            # 결과 확인
            try:
                record_metadata = future.get(timeout=10)
                print(f"Sent message {i}: topic={record_metadata.topic}, "
                      f"partition={record_metadata.partition}, "
                      f"offset={record_metadata.offset}")
            except Exception as e:
                print(f"Error sending message {i}: {e}")
            
            # 짧은 지연 시간
            time.sleep(0.1)
        
        # 모든 메시지 전송 완료 대기
        producer.flush()
        print("All messages sent successfully!")
        
    except Exception as e:
        print(f"Error: {e}")
    finally:
        producer.close()

if __name__ == "__main__":
    main()

