package com.v2ray.ang.ui.aggregation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AggregationPortTest {

    @Test
    fun parsesPortsInsideTcpRange() {
        assertEquals(1, parseAggregationPort("1"))
        assertEquals(1080, parseAggregationPort("1080"))
        assertEquals(65535, parseAggregationPort("65535"))
    }

    @Test
    fun rejectsEmptyAndNonNumericValues() {
        assertNull(parseAggregationPort(""))
        assertNull(parseAggregationPort("port"))
    }

    @Test
    fun rejectsPortsOutsideTcpRange() {
        assertNull(parseAggregationPort("0"))
        assertNull(parseAggregationPort("65536"))
        assertNull(parseAggregationPort("-1"))
    }
}
