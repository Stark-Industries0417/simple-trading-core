# CDC Module (Change Data Capture)

## 개요

CDC 모듈은 Debezium Embedded Engine을 사용하여 MySQL 데이터베이스의 Outbox 테이블 변경사항을 캡처하고 Kafka로 이벤트를 발행하는 모듈입니다. **메인 애플리케이션의 인프라(MySQL, Kafka)를 재사용**합니다.

## 아키텍처

```
[Order Service]
    ↓ (DB Transaction: Order + Outbox Event)
[Main MySQL - order_outbox_events]
    ↓ (CDC via Debezium)
[CDC Module]
    ↓ (Publish)
[Main Kafka - order.events Topic]
```

## 주요 기능

- **Transactional Outbox Pattern**: 데이터베이스 트랜잭션과 이벤트 발행의 원자성 보장
- **Debezium Embedded Engine**: 독립적인 CDC 프로세스로 운영
- **Exactly-Once Semantics**: 멱등성 키를 통한 중복 방지
- **Health Monitoring**: Spring Actuator를 통한 상태 모니터링
- **인프라 통합**: 메인 애플리케이션의 MySQL/Kafka 재사용

## 시작하기

### 통합 실행 (권장)

```bash
# 1단계: 메인 인프라 실행 (프로젝트 루트에서)
docker-compose up -d

# 2단계: CDC 모듈 실행 (메인 인프라 사용)
./gradlew :cdc-module:bootRun
```

### 독립 테스트 환경

```bash
# 테스트 전용 환경 실행 (CDC 기능 독립 테스트용)
cd cdc-module
docker-compose -f docker-compose-dev.yml up -d
```

### 상태 확인

```bash
# Health Check
curl http://localhost:8084/actuator/health

# Kafka UI 접속
open http://localhost:8080
```

## 설정

### application.yml 주요 설정

```yaml
cdc:
  database:
    hostname: localhost
    port: 3306
    name: trading_core  # 메인 애플리케이션 DB
    username: root
    password: password  # 메인 애플리케이션 비밀번호
  
  kafka:
    bootstrap-servers: localhost:9092  # 메인 Kafka
    order-events-topic: order.events
  
  debezium:
    server-id: "184054"
    server-name: "order-service"
    snapshot-mode: schema_only  # 기존 데이터는 스킵
```

### 환경 변수

- `DB_HOST`: MySQL 호스트 (기본값: localhost)
- `DB_PORT`: MySQL 포트 (기본값: 3306)
- `DB_NAME`: 데이터베이스 이름 (기본값: trading_core)
- `DB_USERNAME`: 데이터베이스 사용자 (기본값: root)
- `DB_PASSWORD`: 데이터베이스 비밀번호 (기본값: password)
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka 브로커 주소 (기본값: localhost:9092)

## 모니터링

### Health Indicator

CDC 모듈은 커스텀 Health Indicator를 제공합니다:

- **running**: Debezium Engine 실행 상태
- **eventsProcessed**: 처리된 이벤트 수
- **lastEventTime**: 마지막 이벤트 처리 시간
- **timeSinceLastEventMs**: 마지막 이벤트 이후 경과 시간

5분 이상 이벤트가 처리되지 않으면 unhealthy 상태로 변경됩니다.

### 로깅

```yaml
logging:
  level:
    com.trading.cdc: DEBUG
    io.debezium: INFO
```

## 이벤트 처리 흐름

1. **Order Service**가 주문 생성/취소 시 Outbox 테이블에 이벤트 저장
2. **Debezium Engine**이 MySQL binlog를 모니터링하여 변경사항 감지
3. **OrderOutboxConnector**가 PENDING 상태의 이벤트를 Kafka로 발행
4. **Health Indicator**가 처리 상태를 추적

## 지원되는 이벤트 타입

- `OrderCreated`: 주문 생성 이벤트
- `OrderCancelled`: 주문 취소 이벤트

## 트러블슈팅

### Debezium Engine이 시작되지 않는 경우

1. MySQL binlog가 활성화되어 있는지 확인:
```sql
SHOW VARIABLES LIKE 'log_bin';
```

2. CDC 사용자에게 필요한 권한이 있는지 확인:
```sql
SHOW GRANTS FOR 'cdc_user'@'%';
```

### 이벤트가 Kafka로 전송되지 않는 경우

1. Kafka 브로커가 실행 중인지 확인
2. order.events 토픽이 생성되었는지 확인
3. CDC 모듈 로그에서 에러 메시지 확인

## 테스트

### 수동 이벤트 생성

```sql
INSERT INTO order_outbox_events (event_id, aggregate_id, aggregate_type, event_type, payload, order_id, user_id)
VALUES (
    UUID(),
    'test-order-id',
    'Order',
    'OrderCreated',
    '{"orderId":"test-order-id","userId":"test-user","symbol":"AAPL","quantity":100}',
    'test-order-id',
    'test-user'
);
```

### Kafka 메시지 확인

```bash
# Kafka Console Consumer
docker exec -it kafka-cdc kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.events \
  --from-beginning
```

## 프로덕션 고려사항

1. **Offset 관리**: 프로덕션에서는 파일 시스템 대신 Kafka를 사용한 offset 저장 권장
2. **스케일링**: 여러 인스턴스 실행 시 server-id를 다르게 설정
3. **모니터링**: Prometheus/Grafana와 통합하여 메트릭 수집
4. **에러 처리**: DLQ(Dead Letter Queue) 구현 고려