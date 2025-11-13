package com.kafka.connectors.mssql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class MssqlSinkTask extends SinkTask {
    private static final Logger log = LoggerFactory.getLogger(MssqlSinkTask.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MssqlSinkConnectorConfig config;
    private Connection connection;
    private PreparedStatement insertStatement;
    private List<SinkRecord> buffer;
    private int retryCount = 0;

    @Override
    public void start(Map<String, String> props) {
        config = new MssqlSinkConnectorConfig(props);
        buffer = new ArrayList<>();
        
        try {
            initializeConnection();
            prepareInsertStatement();
            log.info("MSSQL Sink Task started successfully");
        } catch (SQLException e) {
            throw new ConnectException("Failed to initialize MSSQL connection", e);
        }
    }

    private void initializeConnection() throws SQLException {
        String url = config.connectionUrl;
        String user = config.connectionUser;
        String password = config.connectionPassword;

        log.info("Connecting to MSSQL: {}", url);
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        log.info("Successfully connected to MSSQL");
    }

    private void prepareInsertStatement() throws SQLException {
        // JSON 메시지에서 필드를 동적으로 추출하여 INSERT
        // 실제 구현에서는 스키마나 설정을 통해 컬럼 매핑을 정의할 수 있습니다
        String sql = String.format(
            "INSERT INTO %s (message_key, message_value, topic, partition, offset, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            config.tableName
        );
        insertStatement = connection.prepareStatement(sql);
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        buffer.addAll(records);
        log.debug("Buffered {} records. Total buffer size: {}", records.size(), buffer.size());

        if (buffer.size() >= config.batchSize) {
            flush();
        }
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        int attempts = 0;
        while (attempts <= config.maxRetries) {
            try {
                insertRecords();
                connection.commit();
                log.info("Successfully inserted {} records", buffer.size());
                buffer.clear();
                retryCount = 0;
                return;
            } catch (SQLException e) {
                attempts++;
                log.error("Failed to insert records (attempt {}/{}): {}", 
                    attempts, config.maxRetries + 1, e.getMessage());
                
                if (attempts <= config.maxRetries) {
                    try {
                        connection.rollback();
                        Thread.sleep(config.retryBackoffMs * attempts);
                        reconnectIfNeeded();
                    } catch (InterruptedException | SQLException ie) {
                        Thread.currentThread().interrupt();
                        throw new ConnectException("Error during retry", ie);
                    }
                } else {
                    try {
                        connection.rollback();
                    } catch (SQLException re) {
                        log.error("Failed to rollback", re);
                    }
                    throw new ConnectException("Failed to insert records after " + attempts + " attempts", e);
                }
            }
        }
    }

    private void insertRecords() throws SQLException {
        for (SinkRecord record : buffer) {
            String key = record.key() != null ? record.key().toString() : null;
            String value = extractValue(record.value());
            String topic = record.topic();
            int partition = record.kafkaPartition();
            long offset = record.kafkaOffset();
            Timestamp timestamp = new Timestamp(record.timestamp() != null ? record.timestamp() : System.currentTimeMillis());

            insertStatement.setString(1, key);
            insertStatement.setString(2, value);
            insertStatement.setString(3, topic);
            insertStatement.setInt(4, partition);
            insertStatement.setLong(5, offset);
            insertStatement.setTimestamp(6, timestamp);

            insertStatement.addBatch();
        }
        insertStatement.executeBatch();
        insertStatement.clearBatch();
    }

    private String extractValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof String) {
            return (String) value;
        }
        
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize value to JSON, using toString(): {}", e.getMessage());
            return value.toString();
        }
    }

    private void reconnectIfNeeded() throws SQLException {
        if (connection == null || connection.isClosed()) {
            log.info("Reconnecting to MSSQL...");
            initializeConnection();
            prepareInsertStatement();
        }
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {
        flush();
    }

    @Override
    public void stop() {
        try {
            if (buffer != null && !buffer.isEmpty()) {
                log.info("Flushing remaining {} records before shutdown", buffer.size());
                flush();
            }
        } catch (Exception e) {
            log.error("Error flushing records during shutdown", e);
        }

        try {
            if (insertStatement != null) {
                insertStatement.close();
            }
        } catch (SQLException e) {
            log.error("Error closing prepared statement", e);
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("MSSQL connection closed");
            }
        } catch (SQLException e) {
            log.error("Error closing connection", e);
        }
    }

    @Override
    public String version() {
        return "1.0.0";
    }
}

