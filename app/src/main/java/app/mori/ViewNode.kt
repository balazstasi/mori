package app.mori

/** A rectangle in screen pixels. Plain Kotlin so the screen rules run in JVM unit tests. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    operator fun contains(other: Box): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

/**
 * One visible view of a guarded app. [id] is its view ID without the "<package>:id/" prefix. [key]
 * stays the same for as long as the underlying view lives, so a new key in the same place means
 * the app put up a different view.
 */
data class ViewNode(
    val id: String,
    val box: Box,
    val desc: String = "",
    val selected: Boolean = false,
    val editable: Boolean = false,
    val key: Int = 0,
)

/** The screens the rules tell apart. YouTube only ever shows [REEL] or [OTHER]. */
enum class Screen {
    HOME,
    REELS_TAB,
    EXPLORE,
    /** One reel or short, opened from a chat, a link, a profile, search or a recommendation. */
    REEL,
    MESSAGES,
    /** Chats, stories, profiles, search results and everything else that stays usable. */
    OTHER;

    val isReel: Boolean get() = this == REEL || this == REELS_TAB
}
