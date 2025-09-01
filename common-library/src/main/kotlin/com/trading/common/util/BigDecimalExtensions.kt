package com.trading.common.util
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

val BigDecimal.isZero: Boolean
    get() = this == BigDecimal.ZERO

val BigDecimal.isPositive: Boolean
    get() = this > BigDecimal.ZERO

val BigDecimal.isNegative: Boolean
    get() = this < BigDecimal.ZERO

fun BigDecimal.toMoneyScale(): BigDecimal = this.setScale(4, RoundingMode.HALF_UP)

fun BigDecimal.toPriceScale(): BigDecimal = this.setScale(2, RoundingMode.HALF_UP)

fun BigDecimal.toQuantityScale(): BigDecimal = this.setScale(8, RoundingMode.HALF_UP)

fun BigDecimal.toCurrencyString(locale: Locale = Locale.getDefault()): String =
    NumberFormat.getCurrencyInstance(locale).format(this)

infix fun BigDecimal?.safeMultiply(other: BigDecimal?): BigDecimal =
    if (this != null && other != null) {
        this.multiply(other).toMoneyScale()
    } else {
        BigDecimal.ZERO
    }

infix fun BigDecimal?.safeDivide(other: BigDecimal?): BigDecimal =
    if (this != null && other != null && !other.isZero) {
        this.divide(other, 8, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }

fun BigDecimal.percentageOf(total: BigDecimal): BigDecimal =
    if (!total.isZero) {
        this.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
    } else {
        BigDecimal.ZERO
    }
