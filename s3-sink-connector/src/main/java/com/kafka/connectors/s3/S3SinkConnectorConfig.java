package com.kafka.connectors.s3;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class S3SinkConnectorConfig extends AbstractConfig {
    public static final String S3_BUCKET_CONFIG = "s3.bucket";
    public static final String S3_REGION_CONFIG = "s3.region";
    public static final String S3_ENDPOINT_CONFIG = "s3.endpoint";
    public static final String S3_PATH_STYLE_ACCESS_CONFIG = "s3.path.style.access";
    public static final String S3_PREFIX_CONFIG = "s3.prefix";
    public static final String AWS_ACCESS_KEY_ID_CONFIG = "aws.access.key.id";
    public static final String AWS_SECRET_ACCESS_KEY_CONFIG = "aws.secret.access.key";
    public static final String TOPICS_CONFIG = "topics";
    public static final String FLUSH_SIZE_CONFIG = "flush.size";
    public static final String ROTATE_INTERVAL_MS_CONFIG = "rotate.interval.ms";
    public static final String FILE_FORMAT_CONFIG = "file.format";
    public static final String COMPRESSION_TYPE_CONFIG = "compression.type";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(S3_BUCKET_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "S3 버킷 이름")
            .define(S3_REGION_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "AWS 리전 (예: ap-northeast-2, Rook-Ceph의 경우 us-east-1 사용)")
            .define(S3_ENDPOINT_CONFIG, ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "S3 엔드포인트 URL (Rook-Ceph의 경우 http://rook-ceph-rgw-my-store.rook-ceph.svc:80)")
            .define(S3_PATH_STYLE_ACCESS_CONFIG, ConfigDef.Type.BOOLEAN, true, ConfigDef.Importance.LOW,
                    "Path-style 접근 사용 여부 (Rook-Ceph의 경우 true)")
            .define(S3_PREFIX_CONFIG, ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM,
                    "S3 객체 키 접두사 (예: kafka-data/)")
            .define(AWS_ACCESS_KEY_ID_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "AWS 액세스 키 ID")
            .define(AWS_SECRET_ACCESS_KEY_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH,
                    "AWS 시크릿 액세스 키")
            .define(TOPICS_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                    "소비할 Kafka 토픽명 (쉼표로 구분)")
            .define(FLUSH_SIZE_CONFIG, ConfigDef.Type.INT, 1000, ConfigDef.Importance.MEDIUM,
                    "파일로 저장하기 전에 버퍼링할 레코드 수")
            .define(ROTATE_INTERVAL_MS_CONFIG, ConfigDef.Type.LONG, 3600000L, ConfigDef.Importance.MEDIUM,
                    "파일 회전 간격 (밀리초, 기본값: 1시간)")
            .define(FILE_FORMAT_CONFIG, ConfigDef.Type.STRING, "json", ConfigDef.Importance.LOW,
                    "파일 형식 (json, csv)")
            .define(COMPRESSION_TYPE_CONFIG, ConfigDef.Type.STRING, "none", ConfigDef.Importance.LOW,
                    "압축 형식 (none, gzip, snappy)");

    public final String s3Bucket;
    public final String s3Region;
    public final String s3Endpoint;
    public final boolean s3PathStyleAccess;
    public final String s3Prefix;
    public final String awsAccessKeyId;
    public final String awsSecretAccessKey;
    public final String topics;
    public final int flushSize;
    public final long rotateIntervalMs;
    public final String fileFormat;
    public final String compressionType;

    public S3SinkConnectorConfig(Map<String, ?> props) {
        super(CONFIG_DEF, props);
        this.s3Bucket = getString(S3_BUCKET_CONFIG);
        this.s3Region = getString(S3_REGION_CONFIG);
        this.s3Endpoint = getString(S3_ENDPOINT_CONFIG);
        this.s3PathStyleAccess = getBoolean(S3_PATH_STYLE_ACCESS_CONFIG);
        this.s3Prefix = getString(S3_PREFIX_CONFIG);
        this.awsAccessKeyId = getString(AWS_ACCESS_KEY_ID_CONFIG);
        this.awsSecretAccessKey = getPassword(AWS_SECRET_ACCESS_KEY_CONFIG).value();
        this.topics = getString(TOPICS_CONFIG);
        this.flushSize = getInt(FLUSH_SIZE_CONFIG);
        this.rotateIntervalMs = getLong(ROTATE_INTERVAL_MS_CONFIG);
        this.fileFormat = getString(FILE_FORMAT_CONFIG);
        this.compressionType = getString(COMPRESSION_TYPE_CONFIG);
    }
}

