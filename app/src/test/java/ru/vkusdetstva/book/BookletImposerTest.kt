package ru.vkusdetstva.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vkusdetstva.util.book.BookletImposer

class BookletImposerTest {

    @Test
    fun `order for eight pages matches booklet formula`() {
        val sheets = BookletImposer.bookletOrder(8)
        assertEquals(2, sheets.size)
        assertEquals(7, sheets[0].frontLeft)
        assertEquals(0, sheets[0].frontRight)
        assertEquals(1, sheets[0].backLeft)
        assertEquals(6, sheets[0].backRight)
        assertEquals(5, sheets[1].frontLeft)
        assertEquals(2, sheets[1].frontRight)
        assertEquals(3, sheets[1].backLeft)
        assertEquals(4, sheets[1].backRight)
    }

    @Test
    fun `every page appears exactly once for common sizes`() {
        for (total in listOf(4, 8, 12, 20, 28)) {
            val pages = BookletImposer.bookletOrder(total).flatMap {
                listOf(it.frontLeft, it.frontRight, it.backLeft, it.backRight)
            }
            assertEquals((0 until total).toList(), pages.sorted())
        }
    }

    @Test
    fun `pads total to multiple of four`() {
        assertEquals(12, BookletImposer.padToQuadruple(9))
        assertEquals(8, BookletImposer.padToQuadruple(8))
        assertEquals(4, BookletImposer.padToQuadruple(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects totals not divisible by four`() {
        BookletImposer.bookletOrder(6)
    }

    @Test
    fun `first sheet always pairs last and first page`() {
        val sheets = BookletImposer.bookletOrder(16)
        assertEquals(15, sheets.first().frontLeft)
        assertEquals(0, sheets.first().frontRight)
        assertTrue(sheets.all { it.frontLeft > it.frontRight })
        assertTrue(sheets.all { it.backLeft < it.backRight })
    }
}
