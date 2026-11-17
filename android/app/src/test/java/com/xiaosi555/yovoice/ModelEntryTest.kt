package com.xiaosi555.yovoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEntryTest {
    private fun model(
        repo: String? = null,
        modelScope: String? = null,
        revision: String? = null,
        files: List<ModelAssetFile> = emptyList(),
        directory: String? = null,
        stripPrefix: String? = null
    ) = ModelEntry(
        id = "test-model",
        name = "Test model",
        family = "kokoro_tts",
        version = "1.0",
        precision = "Q8",
        remotePath = "voices/test model.gguf",
        size = 100L,
        sha256 = "a".repeat(64),
        repo = repo,
        modelScope = modelScope,
        revision = revision,
        directory = directory,
        stripPrefix = stripPrefix,
        engineMinimum = null,
        files = files
    )

    @Test
    fun singleFilePackageUsesCatalogMainFile() {
        val model = model()
        assertEquals(1, model.packageFiles().size)
        assertEquals(100L, model.packageSize())
        assertEquals("voices/test model.gguf", model.packageFiles().first().path)
    }

    @Test
    fun multiFilePackageSumsEveryCompanionFile() {
        val model = model(files = listOf(
            ModelAssetFile("main.gguf", 100L, "a".repeat(64), null, null),
            ModelAssetFile("sidecars/config.json", 25L, "b".repeat(64), null, null)
        ))
        assertEquals(2, model.packageFiles().size)
        assertEquals(125L, model.packageSize())
    }

    @Test
    fun stripsOnlyTheConfiguredLeadingDirectory() {
        val model = model(stripPrefix = "q8_0")
        assertEquals("model.gguf", model.localRelativePath("q8_0/model.gguf"))
        assertEquals("nested/model.gguf", model.localRelativePath("nested/model.gguf"))
    }

    @Test
    fun defaultHuggingFaceUrlUsesPinnedCatalogRevision() {
        val url = model().fileUrl(model().packageFiles().first(), "huggingface", "1234567890123456789012345678901234567890")
        assertEquals(
            "https://huggingface.co/audio-cpp/audio.cpp-gguf/resolve/1234567890123456789012345678901234567890/voices/test%20model.gguf",
            url
        )
    }

    @Test
    fun customRepositoryAndMirrorSourceAreRespected() {
        val model = model(repo = "owner/model-repo", revision = "release-1")
        assertEquals(
            "https://hf-mirror.com/owner/model-repo/resolve/release-1/voices/test%20model.gguf",
            model.fileUrl(model.packageFiles().first(), "mirror", "ignored")
        )
    }

    @Test
    fun modelScopeSourceUsesItsMirrorRepository() {
        val model = model(repo = "owner/model-repo", modelScope = "owner/model-scope")
        assertEquals(
            "https://modelscope.cn/models/owner/model-scope/resolve/master/voices/test%20model.gguf",
            model.fileUrl(model.packageFiles().first(), "modelscope", "ignored")
        )
    }

    @Test
    fun modelScopeDefaultRepositoryUsesPinnedRevision() {
        val url = model().fileUrl(model().packageFiles().first(), "modelscope", "ignored")
        assertTrue(url.contains("/resolve/c5046e531f656253f082b7f74384ca59ec6d2a4c/"))
    }

    @Test
    fun fileSizesAreFormattedForPeople() {
        assertEquals("189.55 MB", ModelEntry.formatBytes(198_757_376L))
        assertEquals("2.00 GB", ModelEntry.formatBytes(2_147_483_648L))
    }
}
