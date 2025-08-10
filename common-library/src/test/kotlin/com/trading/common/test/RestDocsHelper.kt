package com.trading.common.test
import org.springframework.restdocs.headers.HeaderDocumentation.*
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.test.web.servlet.ResultActions
object RestDocsHelper {

    fun ResultActions.documentApi(identifier: String) = andDo(
        document(
            identifier,
            preprocessRequest(prettyPrint()),
            preprocessResponse(prettyPrint()),
            commonRequestHeaders(),
            commonResponseHeaders()
        )
    )

    fun commonRequestHeaders() = requestHeaders(
        headerWithName("Content-Type").description("요청 본문의 미디어 타입").optional(),
        headerWithName("X-Trace-Id").description("분산 추적을 위한 Trace ID").optional()
    )

    fun commonResponseHeaders() = responseHeaders(
        headerWithName("Content-Type").description("응답 본문의 미디어 타입").optional(),
        headerWithName("X-Trace-Id").description("분산 추적을 위한 Trace ID").optional()
    )

    fun orderDtoFields() = arrayOf(
        fieldWithPath("orderId").type(JsonFieldType.STRING).description("주문 ID"),
        fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
        fieldWithPath("symbol").type(JsonFieldType.STRING).description("거래 종목 심볼"),
        fieldWithPath("orderType").type(JsonFieldType.STRING).description("주문 타입 (MARKET, LIMIT)"),
        fieldWithPath("side").type(JsonFieldType.STRING).description("주문 방향 (BUY, SELL)"),
        fieldWithPath("quantity").type(JsonFieldType.STRING).description("주문 수량"),
        fieldWithPath("price").type(JsonFieldType.STRING).optional().description("주문 가격 (시장가 주문시 null)"),
        fieldWithPath("timestamp").type(JsonFieldType.STRING).description("주문 생성 시각 (ISO-8601)"),
        fieldWithPath("status").type(JsonFieldType.STRING).description("주문 상태 (PENDING, FILLED, CANCELLED, REJECTED)")
    )

    fun marketDataDtoFields() = arrayOf(
        fieldWithPath("symbol").type(JsonFieldType.STRING).description("거래 종목 심볼"),
        fieldWithPath("price").type(JsonFieldType.STRING).description("현재 가격"),
        fieldWithPath("volume").type(JsonFieldType.STRING).description("거래량"),
        fieldWithPath("timestamp").type(JsonFieldType.STRING).description("시세 업데이트 시각 (ISO-8601)"),
        fieldWithPath("bid").type(JsonFieldType.STRING).optional().description("매수 호가"),
        fieldWithPath("ask").type(JsonFieldType.STRING).optional().description("매도 호가"),
        fieldWithPath("bidSize").type(JsonFieldType.STRING).optional().description("매수 호가 수량"),
        fieldWithPath("askSize").type(JsonFieldType.STRING).optional().description("매도 호가 수량")
    )

    fun accountDtoFields() = arrayOf(
        fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
        fieldWithPath("cashBalance").type(JsonFieldType.STRING).description("현금 잔액"),
        fieldWithPath("availableCash").type(JsonFieldType.STRING).description("사용 가능한 현금")
    )

    fun tradeDtoFields() = arrayOf(
        fieldWithPath("tradeId").type(JsonFieldType.STRING).description("거래 ID"),
        fieldWithPath("buyOrderId").type(JsonFieldType.STRING).description("매수 주문 ID"),
        fieldWithPath("sellOrderId").type(JsonFieldType.STRING).description("매도 주문 ID"),
        fieldWithPath("symbol").type(JsonFieldType.STRING).description("거래 종목 심볼"),
        fieldWithPath("quantity").type(JsonFieldType.STRING).description("거래 수량"),
        fieldWithPath("price").type(JsonFieldType.STRING).description("거래 가격"),
        fieldWithPath("executedAt").type(JsonFieldType.STRING).description("거래 체결 시각 (ISO-8601)"),
        fieldWithPath("buyUserId").type(JsonFieldType.STRING).description("매수자 사용자 ID"),
        fieldWithPath("sellUserId").type(JsonFieldType.STRING).description("매도자 사용자 ID")
    )

    fun errorResponseFields() = arrayOf(
        fieldWithPath("timestamp").type(JsonFieldType.STRING).description("에러 발생 시각 (ISO-8601)"),
        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
        fieldWithPath("error").type(JsonFieldType.STRING).description("에러 타입"),
        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
        fieldWithPath("path").type(JsonFieldType.STRING).description("요청 경로"),
        fieldWithPath("traceId").type(JsonFieldType.STRING).optional().description("분산 추적 ID")
    )

    fun pagingParameters() = arrayOf(
        parameterWithName("page").optional().description("페이지 번호 (0부터 시작, 기본값: 0)"),
        parameterWithName("size").optional().description("페이지 크기 (기본값: 20)"),
        parameterWithName("sort").optional().description("정렬 기준 (예: createdAt,desc)")
    )

    fun pageResponseFields(contentFieldName: String = "content") = arrayOf(
        fieldWithPath("$contentFieldName").type(JsonFieldType.ARRAY).description("실제 데이터 배열"),
        fieldWithPath("pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 조건 없음 여부"),
        fieldWithPath("pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부"),
        fieldWithPath("pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬 안됨 여부"),
        fieldWithPath("pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
        fieldWithPath("pageable.pageSize").type(JsonFieldType.NUMBER).description("페이지 크기"),
        fieldWithPath("pageable.pageNumber").type(JsonFieldType.NUMBER).description("페이지 번호"),
        fieldWithPath("pageable.unpaged").type(JsonFieldType.BOOLEAN).description("페이징 미적용 여부"),
        fieldWithPath("pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 적용 여부"),
        fieldWithPath("last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
        fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
        fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
        fieldWithPath("first").type(JsonFieldType.BOOLEAN).description("첫 번째 페이지 여부"),
        fieldWithPath("size").type(JsonFieldType.NUMBER).description("현재 페이지 크기"),
        fieldWithPath("number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
        fieldWithPath("numberOfElements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
        fieldWithPath("empty").type(JsonFieldType.BOOLEAN).description("빈 페이지 여부")
    )
}
