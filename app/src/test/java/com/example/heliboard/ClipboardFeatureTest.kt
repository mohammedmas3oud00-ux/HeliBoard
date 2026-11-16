package com.example.heliboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.ImeInputTarget
import com.example.heliboard.ime.SandboxInputTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardFeatureTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "clipboard_images").deleteRecursively()
    }

    private fun createTempSourceImage(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6)): File {
        val f = File(context.cacheDir, name)
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun pinTwoTextsAndImage_reorder_persistAcrossRestart_unpinAndDeletePreserveOrder() {
        val repo = PreferencesRepository(context)
        // Clear seeded default pinned clip so order assertions are explicit
        repo.clipboard.value.forEach { repo.deleteClipboardEntry(it.id) }

        repo.addClipboardEntry("First text")
        repo.addClipboardEntry("Second text")
        val sourceImg = createTempSourceImage("sample_clip.png")
        repo.addClipboardImageEntry(Uri.fromFile(sourceImg), caption = "Pinned photo", mimeType = "image/png")

        val allBeforePin = repo.clipboard.value
        val firstId = allBeforePin.first { it.text == "First text" }.id
        val secondId = allBeforePin.first { it.text == "Second text" }.id
        val imageId = allBeforePin.first { it.isImage }.id

        // Pin in sequence: newly pinned must append to end
        repo.togglePinClipboard(firstId)
        repo.togglePinClipboard(secondId)
        repo.togglePinClipboard(imageId)

        assertEquals(
            listOf(firstId, secondId, imageId),
            repo.getOrderedPinnedEntries().map { it.id }
        )

        // Delete original source image (simulating source app closing / revoking access)
        assertTrue(sourceImg.delete())
        // HeliBoard's local copy must still exist and remain available
        val imageEntry = repo.clipboard.value.first { it.id == imageId }
        assertTrue(repo.isImageAvailable(imageEntry))
        assertNotNull(repo.resolveImageForPaste(imageEntry))

        // Reorder: move image to index 0 -> [imageId, firstId, secondId]
        repo.movePinnedEntry(fromIndex = 2, toIndex = 0)
        assertEquals(
            listOf(imageId, firstId, secondId),
            repo.getOrderedPinnedEntries().map { it.id }
        )

        // Simulate closing keyboard & restarting app with a brand-new PreferencesRepository
        val restartedRepo = PreferencesRepository(context)
        assertEquals(
            listOf(imageId, firstId, secondId),
            restartedRepo.getOrderedPinnedEntries().map { it.id }
        )
        assertTrue(restartedRepo.isImageAvailable(restartedRepo.clipboard.value.first { it.id == imageId }))

        // Unpin one text item ("First text") -> moves to unpinned recents, remaining keep order [imageId, secondId]
        restartedRepo.togglePinClipboard(firstId)
        assertEquals(
            listOf(imageId, secondId),
            restartedRepo.getOrderedPinnedEntries().map { it.id }
        )
        assertFalse(restartedRepo.clipboard.value.first { it.id == firstId }.isPinned)

        // Pin a new item -> appended at the end [imageId, secondId, thirdId]
        restartedRepo.addClipboardEntry("Third text")
        val thirdId = restartedRepo.clipboard.value.first { it.text == "Third text" }.id
        restartedRepo.togglePinClipboard(thirdId)
        assertEquals(
            listOf(imageId, secondId, thirdId),
            restartedRepo.getOrderedPinnedEntries().map { it.id }
        )

        // Delete middle pinned item ("Second text") -> remaining keep exact order [imageId, thirdId]
        restartedRepo.deleteClipboardEntry(secondId)
        assertEquals(
            listOf(imageId, thirdId),
            restartedRepo.getOrderedPinnedEntries().map { it.id }
        )
    }

    @Test
    fun legacyClipboardJsonMigration_preservesOrderTextImageAndPinStatus() {
        val legacySourceImg = createTempSourceImage("legacy_img.png", byteArrayOf(9, 8, 7, 6))
        val recentTimestamp = System.currentTimeMillis() - 10_000L
        val legacyJson = """
            [
              {"id": 101, "text": "Legacy Pinned A", "timestamp": 1000, "isPinned": true},
              {"id": 102, "content": "Legacy Pinned Image", "timeStamp": 5000, "pinned": true, "uri": "${Uri.fromFile(legacySourceImg)}", "mimeType": "image/png"},
              {"id": 103, "text": "Legacy Pinned B", "timestamp": 2000, "isPinned": true},
              {"id": 104, "text": "Legacy Recent C", "timestamp": $recentTimestamp, "isPinned": false}
            ]
        """.trimIndent()

        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("clipboard_json", legacyJson)
            .commit()

        val repo = PreferencesRepository(context)
        val pinned = repo.getOrderedPinnedEntries()

        // First load must keep exact saved order of legacy pinned entries (101, 102, 103), not re-sort by timestamp
        assertEquals(listOf(101L, 102L, 103L), pinned.map { it.id })
        assertEquals("Legacy Pinned A", pinned[0].text)
        assertTrue(pinned[1].isImage)
        assertTrue(repo.isImageAvailable(pinned[1]))
        assertEquals("Legacy Pinned B", pinned[2].text)

        val recent = repo.clipboard.value.filterNot { it.isPinned }
        assertEquals(listOf(104L), recent.map { it.id })

        // Verify display order honors clipboardPinnedFirst = true vs false
        assertEquals(listOf(101L, 102L, 103L, 104L), repo.clipboard.value.map { it.id })
        repo.updateSettings { it.copy(clipboardPinnedFirst = false) }
        assertEquals(listOf(104L, 102L, 103L, 101L), repo.clipboard.value.map { it.id })
        repo.updateSettings { it.copy(clipboardPinnedFirst = true) }
        assertEquals(listOf(101L, 102L, 103L, 104L), repo.clipboard.value.map { it.id })
    }

    @Test
    fun pasteTextAndImage_handlesSupportedAndUnsupportedTargets_andUnavailableImage() {
        val repo = PreferencesRepository(context)
        val assetRepo = AssetRepository(context)
        val suggestionEngine = SuggestionEngine()
        val sandbox = SandboxInputTarget()
        val controller = HeliKeyboardController(context, repo, assetRepo, suggestionEngine, sandbox)

        repo.addClipboardEntry("Hello HeliBoard")
        val textEntry = repo.clipboard.value.first { it.text == "Hello HeliBoard" }
        controller.onPasteClipboardEntry(textEntry)
        assertEquals("Hello HeliBoard", sandbox.value.value.text)
        assertNull(controller.uiState.value.clipboardFeedbackMessage)

        val imgFile = createTempSourceImage("paste_test.png")
        repo.addClipboardImageEntry(Uri.fromFile(imgFile), mimeType = "image/png")
        val imgEntry = repo.clipboard.value.first { it.isImage }

        // Target does NOT accept images -> clear feedback, nothing committed
        sandbox.setAcceptsImages(false)
        controller.onPasteClipboardEntry(imgEntry)
        assertTrue(sandbox.pastedImages.value.isEmpty())
        assertEquals(
            context.getString(R.string.clipboard_image_not_supported),
            controller.uiState.value.clipboardFeedbackMessage
        )

        // Target DOES accept images -> commits image and clears feedback
        sandbox.setAcceptsImages(true)
        controller.onPasteClipboardEntry(imgEntry)
        assertEquals(1, sandbox.pastedImages.value.size)
        assertNull(controller.uiState.value.clipboardFeedbackMessage)

        // If image file & source URI both become unavailable -> shows unavailable hint and stays deletable
        imgEntry.localImagePath?.let { File(it).delete() }
        imgFile.delete()
        assertFalse(repo.isImageAvailable(imgEntry))
        controller.onPasteClipboardEntry(imgEntry)
        assertEquals(
            context.getString(R.string.clipboard_image_unavailable_hint),
            controller.uiState.value.clipboardFeedbackMessage
        )
        repo.deleteClipboardEntry(imgEntry.id)
        assertFalse(repo.clipboard.value.any { it.id == imgEntry.id })
    }

    @Test
    fun retentionAndPrivacy_pinnedSurvivesExpiry_incognitoAndSensitiveFieldsAreNotSaved() {
        val oldTimestamp = System.currentTimeMillis() - (5 * 60 * 60_000L) // 5 hours ago
        val seedJson = """
            [
              {"id": 201, "text": "Old Pinned", "timestamp": $oldTimestamp, "isPinned": true, "pinnedOrder": 0},
              {"id": 202, "text": "Old Recent", "timestamp": $oldTimestamp, "isPinned": false}
            ]
        """.trimIndent()
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("clipboard_json", seedJson)
            .commit()

        val repo = PreferencesRepository(context) // default retention = 120 min
        assertTrue(repo.clipboard.value.any { it.id == 201L })
        assertFalse(repo.clipboard.value.any { it.id == 202L })

        // Sensitive clip extra should not be captured
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveClip = ClipData.newPlainText("pw", "SuperSecret123").apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(sensitiveClip)
        repo.captureSystemClipboard(isSensitiveField = false)
        assertFalse(repo.clipboard.value.any { it.text == "SuperSecret123" })

        // Sensitive EditorInfo field check
        val passwordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertTrue(ImeInputTarget.isSensitiveEditorInfo(passwordInfo))

        // EditorInfo supporting image/png check
        val imageInfo = EditorInfo().also {
            EditorInfoCompat.setContentMimeTypes(it, arrayOf("image/png", "image/jpeg"))
        }
        val imeTarget = ImeInputTarget(
            connectionProvider = { null as InputConnection? },
            editorInfoProvider = { imageInfo }
        )
        assertTrue(imeTarget.supportsImageMimeType("image/png"))
        assertFalse(imeTarget.supportsImageMimeType("image/gif"))

        // Incognito mode skips recording new clipboard entries
        repo.updateSettings { it.copy(incognitoMode = true) }
        repo.addClipboardEntry("Incognito secret")
        assertFalse(repo.clipboard.value.any { it.text == "Incognito secret" })
    }
}
