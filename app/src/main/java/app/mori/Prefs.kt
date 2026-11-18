package app.mori

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate

/** How much of Instagram's Home opens: none of it, only the stories at the top, or the whole feed. */
enum class InstagramHome { OFF, STORIES, FEED }

class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // The service starts over when a setting changes, so the counts it writes on every scroll live apart.
    private val counts = context.getSharedPreferences("counts", Context.MODE_PRIVATE)

    // SharedPreferences keeps listeners weakly, so this field keeps ours alive.
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var instagram: Boolean
        get() = prefs.getBoolean(KEY_INSTAGRAM, true)
        set(value) = prefs.edit { putBoolean(KEY_INSTAGRAM, value) }

    var youtube: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE, true)
        set(value) = prefs.edit { putBoolean(KEY_YOUTUBE, value) }

    /** Whether a reel opened from a chat or a link may play, one at a time. */
    var instagramReels: Boolean
        get() = prefs.getBoolean(KEY_INSTAGRAM_REELS, true)
        set(value) = prefs.edit { putBoolean(KEY_INSTAGRAM_REELS, value) }

    var instagramHome: InstagramHome
        get() = InstagramHome.entries.firstOrNull { it.name == prefs.getString(KEY_INSTAGRAM_HOME, null) } ?: InstagramHome.OFF
        set(value) = prefs.edit { putString(KEY_INSTAGRAM_HOME, value.name) }

    /** Whether a short opened from Home, search or a link may play, one at a time. */
    var youtubeShorts: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE_SHORTS, true)
        set(value) = prefs.edit { putBoolean(KEY_YOUTUBE_SHORTS, value) }

    /** How many reels or shorts the user may scroll on to each day in this app. Zero means none. */
    fun limit(pkg: String): Int = prefs.getInt(limitKey(pkg), 0)

    fun setLimit(pkg: String, limit: Int) = prefs.edit { putInt(limitKey(pkg), limit) }

    /** Reels or shorts scrolled on to today in this app. The count starts over at midnight. */
    fun scrolledToday(pkg: String): Int =
        if (counts.getLong(appKey(pkg) + DAY_SUFFIX, NO_DAY) == today()) counts.getInt(appKey(pkg), 0) else 0

    /** Counts one more scroll for today and returns the new count. */
    fun addScroll(pkg: String): Int {
        val count = scrolledToday(pkg) + 1
        counts.edit {
            putLong(appKey(pkg) + DAY_SUFFIX, today())
            putInt(appKey(pkg), count)
        }
        return count
    }

    /** Whether the app with this package name is guarded. Apps Mori does not know never are. */
    fun guards(pkg: String?): Boolean = when (pkg) {
        Instagram.PACKAGE -> instagram
        YouTube.PACKAGE -> youtube
        else -> false
    }

    fun onChange(block: () -> Unit) {
        stopListening()
        val newListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> block() }
        listener = newListener
        prefs.registerOnSharedPreferenceChangeListener(newListener)
    }

    fun stopListening() {
        listener?.let(prefs::unregisterOnSharedPreferenceChangeListener)
        listener = null
    }

    private fun appKey(pkg: String) = if (pkg == YouTube.PACKAGE) KEY_YOUTUBE else KEY_INSTAGRAM

    private fun limitKey(pkg: String) = appKey(pkg) + LIMIT_SUFFIX

    private fun today() = LocalDate.now().toEpochDay()

    private companion object {
        const val LIMIT_SUFFIX = "_limit"
        const val DAY_SUFFIX = "_day"
        const val NO_DAY = Long.MIN_VALUE
        const val KEY_INSTAGRAM = "instagram"
        const val KEY_YOUTUBE = "youtube"
        const val KEY_INSTAGRAM_REELS = "instagram_reels"
        const val KEY_INSTAGRAM_HOME = "instagram_home"
        const val KEY_YOUTUBE_SHORTS = "youtube_shorts"
    }
}
