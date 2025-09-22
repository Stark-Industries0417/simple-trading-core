# Simple Trading Core - 수동 테스트 가이드

## 📋 사전 준비

### 1. 인프라 실행
```bash
# Docker 컨테이너 실행 (MySQL, Kafka, Zookeeper)
docker-compose up -d

# 컨테이너 상태 확인
docker ps
```

### 2. 데이터베이스 초기화
```bash
# 테스트 데이터 로드
docker exec -i trading-mysql mysql -uroot -ppassword < init-db.sql
```

### 3. 애플리케이션 실행
```bash
# 터미널 1: 메인 애플리케이션 실행
./gradlew :app:bootRun

# 또는 IDE에서 SimpleTradingCoreApplication 실행
```

## 🧪 테스트 시나리오

### 터미널 구성 (추천)
- **터미널 1**: 애플리케이션 실행
- **터미널 2**: 로그 모니터링 (`./monitor-logs.sh`)
- **터미널 3**: 주문 테스트 실행 (`./test-orders.sh`)
- **터미널 4**: Kafka UI 모니터링 (http://localhost:8085)

## 📊 테스트 데이터 현황

### 사용자별 초기 상태

| 사용자 | 현금 잔액 | 사용가능 현금 | 보유 주식 | 테스트 목적 |
|--------|-----------|---------------|-----------|-------------|
| USER001 | 1,000,000 | 950,000 | AAPL(100), GOOGL(50), MSFT(75), TSLA(30) | 정상 거래 |
| USER002 | 500,000 | 450,000 | GOOGL(20), AMZN(10), AAPL(200) | 혼합 거래 |
| USER003 | 2,000,000 | 2,000,000 | 없음 | BUY 전용 |
| USER004 | 10,000 | 5,000 | AAPL(5), MSFT(3), TSLA(1) | 자금 부족 |
| USER005 | 0 | 0 | AAPL(50), GOOGL(10), MSFT(25), AMZN(15), TSLA(20) | SELL 전용 |

## 🚀 테스트 실행 방법

### 방법 1: 대화형 스크립트 사용
```bash
# 테스트 스크립트 실행
./test-orders.sh

# 메뉴에서 옵션 선택:
# 1. USER001 정상 거래
# 2. USER002 혼합 거래
# 3. USER003 BUY 전용
# 4. USER004 자금 부족
# 5. USER005 SELL 전용
# 6. 실패 시나리오
# 7. 시장가 주문
# 8. 대량 주문
```

### 방법 2: 직접 API 호출
```bash
# BUY 주문 예시
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: USER001" \
  -H "X-Trace-Id: manual-test-001" \
  -d '{
    "symbol": "AAPL",
    "orderType": "LIMIT",
    "side": "BUY",
    "quantity": 10,
    "price": 145.00
  }'

# SELL 주문 예시
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: USER002" \
  -H "X-Trace-Id: manual-test-002" \
  -d '{
    "symbol": "AAPL",
    "orderType": "LIMIT",
    "side": "SELL",
    "quantity": 50,
    "price": 152.00
  }'
```

## 📝 로그 모니터링

### 로그 모니터링 스크립트 사용
```bash
# 모니터링 스크립트 실행
./monitor-logs.sh

# 옵션:
# 1. 전체 로그
# 2. Order 모듈
# 3. Matching 모듈
# 4. Account 모듈
# 5. Saga 패턴
# 6. 에러 로그
# 7. Kafka 이벤트
# 8. 특정 사용자
# 9. 특정 주문
```

### 수동 로그 확인
```bash
# 전체 로그 실시간 확인
tail -f logs/trading-core.log

# 특정 사용자 추적
tail -f logs/trading-core.log | grep USER001

# Saga 이벤트 흐름 추적
tail -f logs/trading-core.log | grep sagaId

# 에러만 확인
tail -f logs/trading-core.log | grep ERROR
```

## 🔍 확인 사항

### 1. Order → Matching → Account 흐름
- Order 생성 이벤트 발행
- Matching 엔진에서 체결 처리
- Account에서 잔액/주식 업데이트

### 2. 실패 시나리오 검증
- 자금 부족 시 보상 트랜잭션
- 주식 부족 시 주문 취소
- 계정 미존재 시 에러 처리

### 3. Saga 패턴 동작
- 각 단계별 이벤트 발행
- 실패 시 보상 이벤트
- 타임아웃 처리

## 🎯 주요 테스트 케이스

### 성공 케이스
1. **정상 BUY**: USER001이 AAPL 10주를 145원에 매수
2. **정상 SELL**: USER002가 AAPL 50주를 152원에 매도
3. **시장가 주문**: USER001이 MSFT 시장가 매수

### 실패 케이스
1. **자금 부족**: USER004가 GOOGL 10주 매수 시도 (28,000원 필요, 5,000원만 보유)
2. **주식 부족**: USER003이 AAPL 매도 시도 (주식 미보유)
3. **전액 예약**: USER_LOCKED 계정 주문 (사용가능 자금 0)

### 엣지 케이스
1. **최소 수량**: USER_EDGE_01이 0.0001주 거래
2. **최대 수량**: USER_EDGE_02가 99,999주 거래
3. **동시 주문**: 같은 사용자가 연속으로 주문

## 📊 모니터링 URL

- **Kafka UI**: http://localhost:8085
- **MySQL Adminer**: http://localhost:8090
- **Application Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics

## 🛠 문제 해결

### 애플리케이션이 실행되지 않을 때
```bash
# 포트 확인
lsof -i :8080

# Docker 컨테이너 재시작
docker-compose restart

# 로그 확인
docker logs trading-mysql
docker logs trading-kafka
```

### 주문이 처리되지 않을 때
1. Kafka 토픽 확인 (Kafka UI)
2. 각 모듈의 로그 확인
3. 데이터베이스 연결 상태 확인

## 📚 참고 사항

- TestOrderGenerator는 5초마다 자동으로 랜덤 주문 생성
- 시장 데이터는 1초마다 업데이트 (±2% 변동)
- 모든 금액은 BigDecimal로 처리 (소수점 정확도 보장)
- Lock-Free 매칭 엔진 사용 (종목별 단일 스레드 처리)