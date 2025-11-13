-- MSSQL Sink Connector를 위한 테이블 생성 스크립트
-- 이 스크립트는 MSSQL 데이터베이스에서 실행해야 합니다.

USE kafka_db;
GO

CREATE TABLE kafka_messages (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    message_key NVARCHAR(MAX),
    message_value NVARCHAR(MAX),
    topic NVARCHAR(255) NOT NULL,
    partition INT NOT NULL,
    offset BIGINT NOT NULL,
    timestamp DATETIME2 NOT NULL,
    created_at DATETIME2 DEFAULT GETDATE()
);
GO

-- 인덱스 생성 (성능 향상)
CREATE INDEX idx_topic_partition_offset ON kafka_messages(topic, partition, offset);
CREATE INDEX idx_timestamp ON kafka_messages(timestamp);
GO

