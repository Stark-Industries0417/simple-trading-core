package com.trading.common.util
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
class BigDecimalExtensionsTest {
    @Test
    fun `isZero should return true for zero value`() {
        val zero = BigDecimal.ZERO
        val notZero = BigDecimal("1.0")
        assertThat(zero.isZero).isTrue()
        assertThat(notZero.isZero).isFalse()
    }
    @Test
    fun `isPositive should return true for positive values`() {
        val positive = BigDecimal("1.5")
        val zero = BigDecimal.ZERO
        val negative = BigDecimal("-1.5")
        assertThat(positive.isPositive).isTrue()
        assertThat(zero.isPositive).isFalse()
        assertThat(negative.isPositive).isFalse()
    }
    @Test
    fun `isNegative should return true for negative values`() {
        val negative = BigDecimal("-1.5")
        val zero = BigDecimal.ZERO
        val positive = BigDecimal("1.5")
        assertThat(negative.isNegative).isTrue()
        assertThat(zero.isNegative).isFalse()
        assertThat(positive.isNegative).isFalse()
    }
    @Test
fun `toMoneyScale should round to 4 decimal places`() {
        val value = BigDecimal("123.456789")
        val result = value.toMoneyScale()
        assertThat(result).isEqualTo(BigDecimal("123.4568"))
        assertThat(result.scale()).isEqualTo(4)
    }
    @Test
    fun `toPriceScale should round to 2 decimal places`() {
        val value = BigDecimal("123.456")
        val result = value.toPriceScale()
        assertThat(result).isEqualTo(BigDecimal("123.46"))
        assertThat(result.scale()).isEqualTo(2)
    }
    @Test
    fun `toQuantityScale should round to 8 decimal places`() {
        val value = BigDecimal("123.123456789")
        val result = value.toQuantityScale()
        assertThat(result).isEqualTo(BigDecimal("123.12345679"))
        assertThat(result.scale()).isEqualTo(8)
    }
    @Test
    fun `safeMultiply should handle null values`() {
        val value1 = BigDecimal("10.5")
        val value2 = BigDecimal("2.0")
        val nullValue: BigDecimal? = null
        assertThat(value1 safeMultiply value2).isEqualTo(BigDecimal("21.0000"))
        assertThat(value1 safeMultiply nullValue).isEqualTo(BigDecimal.ZERO)
        assertThat(nullValue safeMultiply value2).isEqualTo(BigDecimal.ZERO)
        assertThat(nullValue safeMultiply nullValue).isEqualTo(BigDecimal.ZERO)
    }
    @Test
    fun `safeDivide should handle null and zero values`() {
        val value1 = BigDecimal("10.0")
        val value2 = BigDecimal("2.0")
        val zero = BigDecimal.ZERO
        val nullValue: BigDecimal? = null
        assertThat(value1 safeDivide value2).isEqualTo(BigDecimal("5.00000000"))
        assertThat(value1 safeDivide zero).isEqualTo(BigDecimal.ZERO)
        assertThat(value1 safeDivide nullValue).isEqualTo(BigDecimal.ZERO)
        assertThat(nullValue safeDivide value2).isEqualTo(BigDecimal.ZERO)
    }
    @Test
    fun `percentageOf should calculate percentage correctly`() {
        val part = BigDecimal("25")
        val total = BigDecimal("100")
        val zero = BigDecimal.ZERO
        assertThat(part.percentageOf(total)).isEqualTo(BigDecimal("25.0000"))
        assertThat(part.percentageOf(zero)).isEqualTo(BigDecimal.ZERO)
    }
}
