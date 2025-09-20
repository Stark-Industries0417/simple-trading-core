package com.trading.cdc.connector

import org.apache.kafka.connect.data.Struct

/**
 * CDC 커넥터 인터페이스
 *
 * Debezium이 캡처한 데이터베이스 변경 이벤트를 처리하는 커넥터들의 공통 인터페이스
 */
interface CdcConnector {

    /**
     * CDC 이벤트 처리
     *
     * @param record Debezium이 캡처한 데이터베이스 레코드
     */
    fun processEvent(record: Struct)

    /**
     * 커넥터 이름 반환
     */
    fun getConnectorName(): String

    /**
     * 처리하는 소스 테이블 이름
     */
    fun getSourceTable(): String
}