#!/bin/bash

# 로그 모니터링 스크립트
# 여러 터미널에서 각 모듈의 로그를 실시간으로 확인합니다.

LOG_DIR="logs"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

show_menu() {
    echo -e "\n${GREEN}=== 로그 모니터링 메뉴 ===${NC}"
    echo "1. 전체 로그 모니터링 (tail -f)"
    echo "2. Order 모듈 이벤트 추적"
    echo "3. Matching 모듈 이벤트 추적"
    echo "4. Account 모듈 이벤트 추적"
    echo "5. Saga 패턴 추적"
    echo "6. 에러 로그만 보기"
    echo "7. Kafka 이벤트 추적"
    echo "8. 특정 사용자 추적"
    echo "9. 특정 주문 ID 추적"
    echo "0. 종료"
    echo -n "선택: "
}

# 전체 로그 모니터링
monitor_all() {
    echo -e "${BLUE}=== 전체 로그 모니터링 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "(ORDER|MATCHING|ACCOUNT|SAGA|ERROR|WARN)"
}

# Order 모듈 이벤트
monitor_order() {
    echo -e "${YELLOW}=== Order 모듈 이벤트 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "order\.|OrderSaga|OrderCreated|OrderCancelled|OrderValidator"
}

# Matching 모듈 이벤트
monitor_matching() {
    echo -e "${MAGENTA}=== Matching 모듈 이벤트 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "matching\.|MatchingSaga|MatchingEngine|OrderMatched|Trade"
}

# Account 모듈 이벤트
monitor_account() {
    echo -e "${CYAN}=== Account 모듈 이벤트 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "account\.|AccountSaga|AccountUpdate|Balance|Reservation"
}

# Saga 패턴 추적
monitor_saga() {
    echo -e "${GREEN}=== Saga 패턴 이벤트 흐름 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "SagaService|sagaId|compensation|rollback|saga"
}

# 에러 로그
monitor_errors() {
    echo -e "${RED}=== 에러 로그 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "ERROR|EXCEPTION|Failed|failed|Error|error"
}

# Kafka 이벤트
monitor_kafka() {
    echo -e "${BLUE}=== Kafka 이벤트 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "KafkaListener|KafkaTemplate|kafka\.|eventType|topic"
}

# 특정 사용자 추적
monitor_user() {
    echo -n "추적할 사용자 ID 입력: "
    read user_id
    echo -e "${YELLOW}=== ${user_id} 추적 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "${user_id}"
}

# 특정 주문 추적
monitor_order_id() {
    echo -n "추적할 주문 ID 입력: "
    read order_id
    echo -e "${CYAN}=== 주문 ${order_id} 추적 ===${NC}"
    tail -f ${LOG_DIR}/trading-core.log | grep --line-buffered -E --color=always "${order_id}"
}

# 메인 루프
while true; do
    show_menu
    read choice

    case $choice in
        1) monitor_all ;;
        2) monitor_order ;;
        3) monitor_matching ;;
        4) monitor_account ;;
        5) monitor_saga ;;
        6) monitor_errors ;;
        7) monitor_kafka ;;
        8) monitor_user ;;
        9) monitor_order_id ;;
        0)
            echo "종료합니다."
            exit 0
            ;;
        *)
            echo -e "${RED}잘못된 선택입니다.${NC}"
            ;;
    esac
done