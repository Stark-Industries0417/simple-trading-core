#!/bin/bash

# Simple Trading Core - 수동 주문 테스트 스크립트
# 각 사용자별로 다양한 시나리오를 테스트합니다.

API_URL="http://localhost:8080/api/v1/orders"
MARKET_URL="http://localhost:8080/api/v1/market-data"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수: 주문 생성
create_order() {
    local user_id=$1
    local symbol=$2
    local order_type=$3
    local side=$4
    local quantity=$5
    local price=$6
    local description=$7

    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${YELLOW}테스트: ${description}${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo -e "사용자: ${user_id}"
    echo -e "주문: ${side} ${quantity} ${symbol} @ ${price} (${order_type})"

    local data="{\"symbol\":\"${symbol}\",\"orderType\":\"${order_type}\",\"side\":\"${side}\",\"quantity\":${quantity}"
    if [ "$order_type" == "LIMIT" ]; then
        data="${data},\"price\":${price}"
    fi
    data="${data}}"

    echo -e "\n요청 데이터: ${data}"

    response=$(curl -s -m 5 -X POST ${API_URL} \
        -H "Content-Type: application/json" \
        -H "X-User-Id: ${user_id}" \
        -H "X-Trace-Id: manual-test-$(date +%s)" \
        -d "${data}" 2>&1)

    if [ $? -eq 0 ]; then
        echo -e "\n${GREEN}응답:${NC}"
        echo "$response" | jq '.' 2>/dev/null || echo "$response"
    else
        echo -e "\n${RED}요청 실패${NC}"
    fi

    echo -e "${BLUE}----------------------------------------${NC}"
    sleep 2
}

# 함수: 현재 시세 조회
get_market_price() {
    local symbol=$1
    echo -e "\n${BLUE}현재 ${symbol} 시세 조회${NC}"
    curl -s "${MARKET_URL}/${symbol}/current" | jq '.' 2>/dev/null
}

# 함수: 계정 정보 조회
get_account_info() {
    local user_id=$1
    echo -e "\n${BLUE}${user_id} 계정 정보 조회${NC}"
    curl -s "http://localhost:8080/api/v1/accounts/${user_id}" | jq '.' 2>/dev/null
}

# 메인 메뉴
show_menu() {
    echo -e "\n${GREEN}=== Simple Trading Core 수동 테스트 ===${NC}"
    echo "1. USER001 - 매수 테스트 (충분한 자금)"
    echo "2. USER002 - 매도 테스트 (주식 보유)"
    echo "3. USER003 - 대량 매수 테스트"
    echo "4. USER004 - 소액 거래 테스트"
    echo "5. USER005 - 매도 테스트 (AAPL 보유)"
    echo "6. 실패 시나리오 테스트 (선택식)"
    echo "7. 단일 지정가 주문 테스트 (선택식)"
    echo "8. 사용자 지정 주문 테스트"
    echo "9. 현재 시세 조회"
    echo "10. 계정 정보 조회"
    echo "0. 종료"
    echo -n "선택: "
}

# USER001 테스트 - 매수 주문 1개
test_user001() {
    echo -e "\n${GREEN}=== USER001 정상 거래 테스트 ===${NC}"

    # 정상 BUY 주문 1개만
    create_order "USER001" "AAPL" "LIMIT" "BUY" "10" "145.00" \
        "USER001: AAPL 정상 매수 (자금 충분)"
}

# USER002 테스트 - 매도 주문 1개
test_user002() {
    echo -e "\n${GREEN}=== USER002 매도 테스트 ===${NC}"

    # 보유 주식 매도 1개만
    create_order "USER002" "AAPL" "LIMIT" "SELL" "50" "144.00" \
        "USER002: AAPL 대량 매도 (180주 중 50주)"
}

# USER003 테스트 - 대량 매수 1개
test_user003() {
    echo -e "\n${GREEN}=== USER003 대량 매수 테스트 ===${NC}"

    # 대량 자금으로 매수 1개만
    create_order "USER003" "AAPL" "LIMIT" "BUY" "100" "148.00" \
        "USER003: AAPL 대량 매수 (자금 충분)"
}

# USER004 테스트 - 소액 매수 1개
test_user004() {
    echo -e "\n${GREEN}=== USER004 소액 거래 테스트 ===${NC}"

    # 소액으로 매수 1개만
    create_order "USER004" "AAPL" "LIMIT" "BUY" "1" "145.00" \
        "USER004: AAPL 소량 매수 (가능)"
}

# USER005 테스트 - 매도 전용 1개
test_user005() {
    echo -e "\n${GREEN}=== USER005 매도 테스트 ===${NC}"

    # 보유 주식 매도 1개만
    create_order "USER005" "AAPL" "LIMIT" "SELL" "20" "148.00" \
        "USER005: AAPL 매도 (50주 중 20주)"
}

# 실패 시나리오 테스트 - 1개 선택
test_failure_scenarios() {
    echo -e "\n${GREEN}=== 실패 시나리오 테스트 ===${NC}"
    echo "1. 존재하지 않는 사용자"
    echo "2. 지원하지 않는 종목"
    echo "3. 음수 수량"
    echo "4. 전액 예약된 계정"
    echo -n "테스트할 시나리오 선택 (1-4): "
    read scenario

    case $scenario in
        1)
            create_order "USER999" "AAPL" "LIMIT" "BUY" "10" "145.00" \
                "존재하지 않는 사용자 주문"
            ;;
        2)
            create_order "USER001" "INVALID" "LIMIT" "BUY" "10" "100.00" \
                "지원하지 않는 종목 주문"
            ;;
        3)
            create_order "USER001" "AAPL" "LIMIT" "BUY" "-10" "145.00" \
                "음수 수량 주문"
            ;;
        4)
            create_order "USER_LOCKED" "AAPL" "LIMIT" "BUY" "10" "145.00" \
                "전액 예약된 계정 주문"
            ;;
        *)
            echo -e "${RED}잘못된 선택입니다.${NC}"
            ;;
    esac
}

# 단일 지정가 주문 테스트
test_single_limit_order() {
    echo -e "\n${GREEN}=== 단일 지정가 주문 테스트 ===${NC}"
    echo "1. AAPL 매수 주문"
    echo "2. GOOGL 매도 주문"
    echo "3. TSLA 매수 주문"
    echo "4. MSFT 매도 주문"
    echo -n "테스트할 주문 선택 (1-4): "
    read choice

    case $choice in
        1)
            create_order "USER001" "AAPL" "LIMIT" "BUY" "5" "147.50" \
                "USER001: AAPL 지정가 매수"
            ;;
        2)
            create_order "USER002" "GOOGL" "LIMIT" "SELL" "2" "2855.00" \
                "USER002: GOOGL 지정가 매도"
            ;;
        3)
            create_order "USER001" "TSLA" "LIMIT" "BUY" "8" "243.00" \
                "USER001: TSLA 지정가 매수"
            ;;
        4)
            create_order "USER002" "MSFT" "LIMIT" "SELL" "3" "382.00" \
                "USER002: MSFT 지정가 매도"
            ;;
        *)
            echo -e "${RED}잘못된 선택입니다.${NC}"
            ;;
    esac
}

# 사용자 지정 주문 테스트
test_custom_order() {
    echo -e "\n${GREEN}=== 사용자 지정 주문 테스트 ===${NC}"

    echo -n "사용자 ID (예: USER001): "
    read user_id
    echo -n "종목 코드 (AAPL/GOOGL/MSFT/AMZN/TSLA): "
    read symbol
    echo -n "주문 유형 (BUY/SELL): "
    read side
    echo -n "수량: "
    read quantity
    echo -n "가격: "
    read price

    create_order "$user_id" "$symbol" "LIMIT" "$side" "$quantity" "$price" \
        "사용자 지정 주문: $user_id - $symbol $side $quantity @ $price"
}

# 메인 루프
while true; do
    show_menu
    read choice

    case $choice in
        1) test_user001 ;;
        2) test_user002 ;;
        3) test_user003 ;;
        4) test_user004 ;;
        5) test_user005 ;;
        6) test_failure_scenarios ;;
        7) test_single_limit_order ;;
        8) test_custom_order ;;
        9)
            echo -n "종목 코드 입력 (AAPL/GOOGL/MSFT/AMZN/TSLA): "
            read symbol
            get_market_price "$symbol"
            ;;
        10)
            echo -n "사용자 ID 입력: "
            read user_id
            get_account_info "$user_id"
            ;;
        0)
            echo "종료합니다."
            exit 0
            ;;
        *)
            echo -e "${RED}잘못된 선택입니다.${NC}"
            ;;
    esac

    echo -e "\n계속하려면 Enter를 누르세요..."
    read
done