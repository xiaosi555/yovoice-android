package com.xiaosi555.yovoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTtsPackageImporterTest {
    @Test
    fun mapsSpeakerChineseNameToLocalDatByCode() {
        val yaml = """
            huoshan:
            - !!org.nobody.multitts.tts.speaker.Speaker
              code: lfeichen
              desc: LingFeiChen
              locale: zh-CN
              name: 聆飞晨
        """.trimIndent()
        val speakers = MultiTtsPackageImporter.parseSpeakers(
            yaml,
            listOf("isstts/xTTSRes/lfeichen.voc.dat"),
            mapOf("lfeichen" to "LingFeiChen")
        )
        assertEquals(1, speakers.size)
        assertEquals("聆飞晨", speakers.single().name)
        assertEquals("lfeichen", speakers.single().code)
        assertTrue(speakers.single().hasLocalData)
    }

    @Test
    fun matchesDatUsingSpeakerDescriptionWhenCodeFilenameDiffers() {
        val yaml = """
            huoshan:
            - !!org.nobody.multitts.tts.speaker.Speaker
              code: lfeichen
              desc: LingFeiChen
              locale: zh-CN
              name: 聆飞晨
        """.trimIndent()
        val speakers = MultiTtsPackageImporter.parseSpeakers(
            yaml,
            listOf("isstts/xTTSRes/lfeizhe.voc.dat"),
            mapOf("lfeizhe" to "LingFeiChen")
        )
        assertTrue(speakers.single().hasLocalData)
        assertEquals("isstts/xTTSRes/lfeizhe.voc.dat", speakers.single().datPath)
    }

    @Test
    fun onlineVoiceWithoutDatIsNotMarkedAsLocal() {
        val yaml = """
            huoshan:
            - !!org.nobody.multitts.tts.speaker.Speaker
              code: tts.other.BV050_streaming
              desc: 动漫人物配音
              locale: zh-CN
              name: 男童-新新
        """.trimIndent()
        val speakers = MultiTtsPackageImporter.parseSpeakers(yaml, emptyList(), emptyMap())
        assertEquals("男童-新新", speakers.single().name)
        assertFalse(speakers.single().hasLocalData)
    }
}
