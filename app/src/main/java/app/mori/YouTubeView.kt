package app.mori

/**
 * YouTube's view IDs, from other open source blockers and not yet checked on YouTube 21.45
 * (November 2026). YouTube calls Shorts "reels" inside the app.
 */
object YouTube {
    const val PACKAGE = "com.google.android.youtube"

    /** The list the Shorts player pages through. It reports no page numbers and cannot be scrolled back. */
    const val SHORTS_PAGER = "reel_recycler"

    /** One short in the Shorts player. */
    const val SHORTS_PAGE = "reel_player_page_container"

    /** The Shorts button in the bottom bar is found by this label. */
    const val SHORTS_LABEL = "Shorts"
}

/** The visible views of one YouTube screen, and the rules that read them. */
class YouTubeView(
    private val nodes: List<ViewNode>,
    private val screenHeight: Int,
) {

    /** A short fills the screen, whether it came from the Shorts button or a recommendation. */
    fun screen(): Screen = if (hasShortsPlayer()) Screen.REEL else Screen.OTHER

    /** The short in the middle of the screen, as a key that changes when a swipe brings up another one. */
    fun shortOnScreen(): Int? = nodes.firstOrNull {
        it.id == YouTube.SHORTS_PAGE && it.box.top <= screenHeight / 2 && it.box.bottom >= screenHeight / 2
    }?.key

    /** The Shorts button in the bottom bar. The Shorts shelf on Home has the same label, higher up. */
    fun shortsButton(): ViewNode? = nodes.firstOrNull {
        it.desc.equals(YouTube.SHORTS_LABEL, ignoreCase = true) && it.box.top > screenHeight * 0.8
    }

    // Small parts of a short, such as its time bar, stay on other tabs, so only a full-height player counts.
    private fun hasShortsPlayer() = nodes.any { node ->
        val id = node.id
        val isPlayerPart = id == YouTube.SHORTS_PAGER || id.startsWith("reel_player") || id.startsWith("reel_watch")
        isPlayerPart && node.box.height > screenHeight * 0.6
    }
}
