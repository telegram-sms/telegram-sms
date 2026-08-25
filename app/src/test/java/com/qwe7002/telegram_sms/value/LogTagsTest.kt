package com.qwe7002.telegram_sms.value

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards for the logcat allow-lists in `value/LogTags.kt`.
 *
 * `LogActivity.startLogcat` and `ChatService.readLogcat` both build a logcat filterspec
 * out of these arrays and append `*:S`, which silences everything else. A class whose
 * tag is missing from the lists therefore logs into the void as far as the in-app log
 * viewer and the `/logcat` command are concerned - and nothing at the call site says so.
 */
class LogTagsTest {

    /**
     * Matches a log-tag declaration in any of the spellings the codebase uses --
     * `val logTag = "..."`, `private const val logTag = "..."`, and the same with an
     * explicit `: String` annotation. It cannot follow a tag assembled from several
     * constants; if that spelling is ever introduced, widen this rather than letting the
     * cross-check quietly stop seeing the tag.
     */
    private val logTagPattern = Regex("val\\s+logTag\\s*(?::\\s*String\\s*)?=\\s*\"([^\"]*)\"")

    // --- structural invariants (always run) -----------------------------------

    @Test
    fun tagFilterHasNoDuplicates() {
        assertEquals(TAG_FILTER.size, TAG_FILTER.toSet().size)
    }

    @Test
    fun debugTagFilterHasNoDuplicates() {
        assertEquals(DEBUG_TAG_FILTER.size, DEBUG_TAG_FILTER.toSet().size)
    }

    @Test
    fun theTwoFiltersAreDisjoint() {
        // LogActivity does TAG_FILTER.plus(DEBUG_TAG_FILTER) on debug builds; an entry in
        // both would be emitted twice into the filterspec, which is the visible symptom
        // of a tag having been promoted out of debug-only without deleting the old row.
        val overlap = TAG_FILTER.intersect(DEBUG_TAG_FILTER.toSet())
        assertTrue("tags listed in both filters: $overlap", overlap.isEmpty())
    }

    @Test
    fun noTagIsBlankOrCarriesThePrefix() {
        // The arrays hold bare class names; the "Telegram-SMS." prefix is added when the
        // filterspec is built, so a pre-prefixed entry would produce "Telegram-SMS.Telegram-SMS.X".
        for (tag in TAG_FILTER + DEBUG_TAG_FILTER) {
            assertTrue("blank tag in filter", tag.isNotBlank())
            assertTrue("tag '$tag' must not contain the app TAG prefix", !tag.contains(TAG))
            assertTrue("tag '$tag' must not contain a separator", !tag.contains(':'))
        }
    }

    // --- cross-check against the source tree ----------------------------------

    @Test
    fun everyLogTagInTheSourceTreeIsCoveredByAFilter() {
        val sourceRoot = findSourceRoot()
        assumeTrue(
            "source tree not reachable from ${System.getProperty("user.dir")}; skipping",
            sourceRoot != null
        )

        val declared = TAG_FILTER.toSet() + DEBUG_TAG_FILTER.toSet()
        val missing = sortedSetOf<String>()

        sourceRoot!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                for (match in logTagPattern.findAll(file.readText())) {
                    val literal = match.groupValues[1]
                    if (!literal.contains("{TAG}.")) continue
                    val tag = literal.substringAfterLast('.')
                    if (tag !in declared) {
                        missing.add("$tag (${file.name})")
                    }
                }
            }

        assertTrue(
            "log tags declared in the source but absent from TAG_FILTER / DEBUG_TAG_FILTER: " +
                    "$missing - add them to value/LogTags.kt or their logs will never reach " +
                    "LogActivity and /logcat",
            missing.isEmpty()
        )
    }

    @Test
    fun everyFilteredTagStillExistsInTheSourceTree() {
        val sourceRoot = findSourceRoot()
        assumeTrue(
            "source tree not reachable from ${System.getProperty("user.dir")}; skipping",
            sourceRoot != null
        )

        val declaredInSource = sourceRoot!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                logTagPattern.findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { it.contains("{TAG}.") }
                    .map { it.substringAfterLast('.') }
            }
            .toSet()

        val stale = (TAG_FILTER + DEBUG_TAG_FILTER).filter { it !in declaredInSource }.sorted()
        assertTrue(
            "filter entries with no matching logTag in the source (renamed or deleted class?): $stale",
            stale.isEmpty()
        )
    }

    /** Locates `.../java/com/qwe7002/telegram_sms` whether tests run from the module dir or the repo root. */
    private fun findSourceRoot(): File? {
        val relative = "src/main/java/com/qwe7002/telegram_sms"
        var dir: File? = File(System.getProperty("user.dir") ?: return null).absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, relative), File(dir, "app/$relative"))) {
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }
}
