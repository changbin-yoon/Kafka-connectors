package com.kafka.connectors.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class S3SinkTask extends SinkTask {
    private static final Logger log = LoggerFactory.getLogger(S3SinkTask.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH-mm-ss")
            .withZone(ZoneId.systemDefault());

    private S3SinkConnectorConfig config;
    private S3Client s3Client;
    private List<SinkRecord> buffer;
    private Map<String, Long> lastRotateTime;
    private ScheduledExecutorService scheduler;
    private long currentFileStartTime;

    @Override
    public void start(Map<String, String> props) {
        config = new S3SinkConnectorConfig(props);
        buffer = new ArrayList<>();
        lastRotateTime = new HashMap<>();
        currentFileStartTime = System.currentTimeMillis();

        initializeS3Client();
        startRotationScheduler();

        log.info("S3 Sink Task started successfully");
    }

    private void initializeS3Client() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                config.awsAccessKeyId,
                config.awsSecretAccessKey
        );

        s3Client = S3Client.builder()
                .region(Region.of(config.s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

        log.info("S3 Client initialized for bucket: {} in region: {}", config.s3Bucket, config.s3Region);
    }

    private void startRotationScheduler() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                this::checkAndRotate,
                config.rotateIntervalMs,
                config.rotateIntervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("File rotation scheduler started with interval: {} ms", config.rotateIntervalMs);
    }

    private void checkAndRotate() {
        if (!buffer.isEmpty()) {
            log.info("Rotating file due to time interval");
            flush();
        }
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        buffer.addAll(records);
        log.debug("Buffered {} records. Total buffer size: {}", records.size(), buffer.size());

        if (buffer.size() >= config.flushSize) {
            flush();
        }
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        try {
            String content = formatRecords(buffer);
            byte[] data = compressIfNeeded(content);
            String key = generateS3Key();

            uploadToS3(key, data);
            log.info("Successfully uploaded {} records to S3: {}", buffer.size(), key);

            buffer.clear();
            currentFileStartTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Failed to flush records to S3", e);
            throw new ConnectException("Failed to upload records to S3", e);
        }
    }

    private String formatRecords(List<SinkRecord> records) throws IOException {
        StringBuilder sb = new StringBuilder();

        if ("json".equalsIgnoreCase(config.fileFormat)) {
            for (SinkRecord record : records) {
                Map<String, Object> recordMap = new HashMap<>();
                recordMap.put("key", record.key());
                recordMap.put("value", record.value());
                recordMap.put("topic", record.topic());
                recordMap.put("partition", record.kafkaPartition());
                recordMap.put("offset", record.kafkaOffset());
                recordMap.put("timestamp", record.timestamp());

                sb.append(objectMapper.writeValueAsString(recordMap));
                sb.append("\n");
            }
        } else if ("csv".equalsIgnoreCase(config.fileFormat)) {
            // CSV 헤더
            sb.append("key,value,topic,partition,offset,timestamp\n");
            for (SinkRecord record : records) {
                sb.append(escapeCsv(record.key()));
                sb.append(",");
                sb.append(escapeCsv(record.value()));
                sb.append(",");
                sb.append(record.topic());
                sb.append(",");
                sb.append(record.kafkaPartition());
                sb.append(",");
                sb.append(record.kafkaOffset());
                sb.append(",");
                sb.append(record.timestamp() != null ? record.timestamp() : "");
                sb.append("\n");
            }
        } else {
            throw new ConnectException("Unsupported file format: " + config.fileFormat);
        }

        return sb.toString();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private byte[] compressIfNeeded(String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        if ("gzip".equalsIgnoreCase(config.compressionType)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(data);
            }
            data = baos.toByteArray();
        } else if ("snappy".equalsIgnoreCase(config.compressionType)) {
            // Snappy 압축은 별도 라이브러리 필요
            log.warn("Snappy compression not implemented, using uncompressed data");
        }

        return data;
    }

    private String generateS3Key() {
        Instant now = Instant.now();
        String date = DATE_FORMATTER.format(now);
        String time = TIME_FORMATTER.format(now);
        String timestamp = String.valueOf(currentFileStartTime);

        String extension = config.fileFormat.toLowerCase();
        if ("gzip".equalsIgnoreCase(config.compressionType)) {
            extension += ".gz";
        }

        String prefix = config.s3Prefix;
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }

        return String.format("%s%s/%s/kafka-data-%s.%s", 
                prefix, date, time, timestamp, extension);
    }

    private void uploadToS3(String key, byte[] data) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(config.s3Bucket)
                .key(key)
                .contentType(getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
    }

    private String getContentType() {
        if ("json".equalsIgnoreCase(config.fileFormat)) {
            return "application/json";
        } else if ("csv".equalsIgnoreCase(config.fileFormat)) {
            return "text/csv";
        }
        return "application/octet-stream";
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {
        flush();
    }

    @Override
    public void stop() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (buffer != null && !buffer.isEmpty()) {
                log.info("Flushing remaining {} records before shutdown", buffer.size());
                flush();
            }
        } catch (Exception e) {
            log.error("Error flushing records during shutdown", e);
        }

        try {
            if (s3Client != null) {
                s3Client.close();
                log.info("S3 Client closed");
            }
        } catch (Exception e) {
            log.error("Error closing S3 client", e);
        }
    }

    @Override
    public String version() {
        return "1.0.0";
    }
}

