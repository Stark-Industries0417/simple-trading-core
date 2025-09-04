package com.trading.cdc

import com.trading.cdc.config.CdcProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
    "cdc.database.hostname=localhost",
    "cdc.database.port=3306",
    "cdc.database.name=trading_core",
    "cdc.database.username=root",
    "cdc.database.password=password",
    "cdc.kafka.bootstrap-servers=localhost:9092",
    "cdc.debezium.offset-storage-file-name=/tmp/test-offsets.dat"
])
class CdcSimpleTest {
    
    @Autowired(required = false)
    private var cdcProperties: CdcProperties? = null
    
    @Test
    fun `컨텍스트가 성공적으로 로드된다`() {
        assertNotNull(cdcProperties)
    }
    
    @Test
    fun `CDC 프로퍼티가 올바르게 로드된다`() {
        assertNotNull(cdcProperties)
        assertEquals("localhost", cdcProperties?.database?.hostname)
        assertEquals(3306, cdcProperties?.database?.port)
        assertEquals("trading_core", cdcProperties?.database?.name)
        assertEquals("localhost:9092", cdcProperties?.kafka?.bootstrapServers)
    }
    
    @Test
    fun `Debezium 프로퍼티가 설정된다`() {
        assertNotNull(cdcProperties?.debezium)
        assertEquals("184054", cdcProperties?.debezium?.serverId)
        assertEquals("order-service", cdcProperties?.debezium?.serverName)
        assertEquals("/tmp/test-offsets.dat", cdcProperties?.debezium?.offsetStorageFileName)
    }
}