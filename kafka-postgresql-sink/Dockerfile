FROM quay.io/strimzi/kafka:0.48.0-kafka-4.0.0
USER root:root

# 1. 플러그인 다운로드를 위한 디렉토리 생성
RUN mkdir -p /opt/kafka/plugins/jdbc-sink-connector \
    && mkdir -p /opt/kafka/plugins/postgresql-driver

# 2. Confluent JDBC Sink Connector 다운로드 및 압축 해제
RUN curl -L -o /tmp/confluent-kafka-connect-jdbc.zip https://packages.confluent.io/archive/7.5/confluentinc-kafka-connect-jdbc-10.7.4.zip \
    && unzip /tmp/confluent-kafka-connect-jdbc.zip -d /tmp \
    && mv /tmp/confluentinc-kafka-connect-jdbc-*/lib/* /opt/kafka/plugins/jdbc-sink-connector/ \
    && rm -rf /tmp/confluent-kafka-connect-jdbc.zip /tmp/confluentinc-kafka-connect-jdbc-*

# 3. PostgreSQL JDBC 드라이버 다운로드
RUN curl -L -o /opt/kafka/plugins/postgresql-driver/postgresql-42.6.0.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.6.0/postgresql-42.6.0.jar

USER 1001
