package com.dianming.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyStoreTest {

    @Test
    fun `round trips owners`() {
        val owners = listOf(
            Owner("张三", "13800138000"),
            Owner("李四", "13900139000")
        )
        val encoded = TinyStore.encodeOwners(owners)
        assertEquals(owners, TinyStore.decodeOwners(encoded))
    }

    @Test
    fun `round trips maps`() {
        val map = mapOf("学号" to "20230101", "语文" to "88", "备注" to "a,b;c")
        assertEquals(map, TinyStore.decodeMap(TinyStore.encodeMap(map)))
    }

    @Test
    fun `handles empty and special characters`() {
        assertTrue(TinyStore.decodeOwners("").isEmpty())
        assertTrue(TinyStore.decodeOwners(null).isEmpty())
        val owners = listOf(Owner("张\u001F三", "138\u001E001"))
        assertEquals(owners, TinyStore.decodeOwners(TinyStore.encodeOwners(owners)))
    }
}
