package com.kafka.examples;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Producer 예제
 * Kafka 토픽에 메시지를 전송하여 S3 Sink Connector가 Rook-Ceph에 저장하도록 테스트
 */
public class KafkaProducerExample {
    private static final String BOOTSTRAP_SERVERS = "my-cluster-kafka-bootstrap:9092";
    private static final String TOPIC_NAME = "kafka-data-topic";
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        try {
            // 테스트 메시지 생성 및 전송
            Random random = new Random();
            for (int i = 0; i < 100; i++) {
                String key = "key-" + i;
                String value = createSampleMessage(i, random);
                
                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, key, value);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Error sending message: " + exception.getMessage());
                    } else {
                        System.out.println("Sent message: topic=" + metadata.topic() +
                                ", partition=" + metadata.partition() +
                                ", offset=" + metadata.offset());
                    }
                });
                
                // 짧은 지연 시간
                Thread.sleep(100);
            }
            
            // 모든 메시지 전송 완료 대기
            producer.flush();
            System.out.println("All messages sent successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            producer.close();
        }
    }
    
    private static String createSampleMessage(int index, Random random) {
        // JSON 형식의 샘플 메시지 생성
        return String.format(
            "{\"id\":%d,\"timestamp\":\"%s\",\"value\":%.2f,\"status\":\"%s\",\"data\":{\"field1\":\"value%d\",\"field2\":%d}}",
            index,
            System.currentTimeMillis(),
            random.nextDouble() * 100,
            random.nextBoolean() ? "active" : "inactive",
            index,
            random.nextInt(1000)
        );
    }
}

