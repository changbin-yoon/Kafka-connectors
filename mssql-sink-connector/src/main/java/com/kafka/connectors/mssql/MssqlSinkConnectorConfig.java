package com.kafka.connectors.mssql;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class MssqlSinkConnectorConfig extends AbstractConfig {
    public static final String CONNECTION_URL_CONFIG = "connection.url";
    public static final String CONNECTION_USER_CONFIG = "connection.user";
    public static final String CONNECTION_PASSWORD_CONFIG = "connection.password";
    public static final String TABLE_NAME_CONFIG = "table.name";
    public static final String TOPICS_CONFIG = "topics";
    public static final String BATCH_SIZE_CONFIG = "batch.size";
    public static final String MAX_RETRIES_CONFIG = "max.retries";
    public static final String RETRY_BACKOFF_MS_CONFIG = "retry.backoff.ms";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(CONNECTION_URL_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "MSSQL 데이터베이스 연결 URL (예: jdbc:sqlserver://host:port;databaseName=dbname)")
            .define(CONNECTION_USER_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "MSSQL 데이터베이스 사용자명")
            .define(CONNECTION_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH,
                    "MSSQL 데이터베이스 비밀번호")
            .define(TABLE_NAME_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "데이터를 삽입할 테이블명")
            .define(TOPICS_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "소비할 Kafka 토픽명 (쉼표로 구분)")
            .define(BATCH_SIZE_CONFIG, ConfigDef.Type.INT, 100, ConfigDef.Importance.MEDIUM,
                    "배치로 삽입할 레코드 수")
            .define(MAX_RETRIES_CONFIG, ConfigDef.Type.INT, 3, ConfigDef.Importance.LOW,
                    "실패 시 최대 재시도 횟수")
            .define(RETRY_BACKOFF_MS_CONFIG, ConfigDef.Type.LONG, 1000L, ConfigDef.Importance.LOW,
                    "재시도 전 대기 시간 (밀리초)");

    public final String connectionUrl;
    public final String connectionUser;
    public final String connectionPassword;
    public final String tableName;
    public final String topics;
    public final int batchSize;
    public final int maxRetries;
    public final long retryBackoffMs;

    public MssqlSinkConnectorConfig(Map<String, ?> props) {
        super(CONFIG_DEF, props);
        this.connectionUrl = getString(CONNECTION_URL_CONFIG);
        this.connectionUser = getString(CONNECTION_USER_CONFIG);
        this.connectionPassword = getPassword(CONNECTION_PASSWORD_CONFIG).value();
        this.tableName = getString(TABLE_NAME_CONFIG);
        this.topics = getString(TOPICS_CONFIG);
        this.batchSize = getInt(BATCH_SIZE_CONFIG);
        this.maxRetries = getInt(MAX_RETRIES_CONFIG);
        this.retryBackoffMs = getLong(RETRY_BACKOFF_MS_CONFIG);
    }
}

