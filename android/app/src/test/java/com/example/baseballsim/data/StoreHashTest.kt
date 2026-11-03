package com.example.baseballsim.data

import java.security.MessageDigest
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class StoreHashTest {
    private fun legacyHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test fun everyByteIncludingNegativeValuesKeepsTwoLowercaseDigitsInOrder() {
        val allBytes = ByteArray(256) { it.toByte() }
        assertEquals(legacyHex(allBytes), lowercaseHex(allBytes))
        allBytes.forEach { byte -> assertEquals(legacyHex(byteArrayOf(byte)), lowercaseHex(byteArrayOf(byte))) }
        assertEquals("007f80ff", lowercaseHex(byteArrayOf(0, 127, -128, -1)))
        assertEquals("", lowercaseHex(byteArrayOf()))
    }

    @Test fun publishedSha256VectorsKeepTheirExactStoredStrings() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("abc".toByteArray()))
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", hash("The quick brown fox jumps over the lazy dog".toByteArray()))
    }

    @Test fun fixedRandomBuffersAgreeWithThePreviousDigestAndFormatterPath() {
        val random = Random(20261104L)
        val lengths = listOf(0, 1, 31, 32, 33, 55, 56, 63, 64, 65, 127, 128, 4096, 131072) + List(32) { random.nextInt(8193) }
        lengths.forEach { length ->
            val bytes = ByteArray(length).also(random::nextBytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val actual = hash(bytes)
            assertEquals("buffer length $length", legacyHex(digest), actual)
            assertTrue(actual.matches(Regex("[0-9a-f]{64}")))
        }
    }
}
