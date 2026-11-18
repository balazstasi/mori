package app.mori

/**
 * Instagram's view IDs, checked against Instagram 455 (November 2026). Instagram renames views
 * without notice, so a rule that stops working usually needs a new ID here.
 */
object Instagram {
    const val PACKAGE = "com.instagram.android"
    const val INBOX_LINK = "instagram://direct-inbox"

    const val HOME_TAB = "feed_tab"
    const val REELS_TAB = "clips_tab"
    const val SEARCH_TAB = "search_tab"
    const val MESSAGES_TAB = "direct_tab"

    /** The paper plane in Home's top bar, where Messages lived before it moved to the bottom bar. */
    const val INBOX_BUTTON = "action_bar_inbox_button"

    /** The Instagram wordmark in Home's top bar. Instagram 455 no longer marks the Home tab selected. */
    const val HOME_LOGO = "title_logo"

    /** One story in the row at the top of Home, around its avatar and name. */
    const val STORY = "outer_container"
    const val STORY_AVATAR = "avatar_image_view"

    /** Bars above a feed that stay uncovered: Home's top bar and Explore's search bar. */
    val TOP_BARS = setOf("action_bar_container", "action_bar_left_button", "action_bar_search_edit_text", INBOX_BUTTON, HOME_LOGO)

    fun isReelPager(id: String): Boolean = id.startsWith("clips_view")
}

/** The visible views of one Instagram screen, and the rules that read them. */
class InstagramView(
    private val nodes: List<ViewNode>,
    private val screenWidth: Int,
    private val screenHeight: Int,
) {

    fun screen(): Screen {
        val reelsTab = isTabSelected(Instagram.REELS_TAB)
        if (hasReelViewer()) {
            // A reel opened from a chat has a reply bar. The Reels tab never does.
            return if (reelsTab && !isChatReel()) Screen.REELS_TAB else Screen.REEL
        }
        if (reelsTab) return Screen.REELS_TAB
        if (isTabSelected(Instagram.MESSAGES_TAB) || hasInbox()) return Screen.MESSAGES
        // A chat has a text box, so a Home tab still marked selected under it never counts as Home.
        if ((isTabSelected(Instagram.HOME_TAB) || hasHomeLogo()) && nodes.none { it.editable }) return Screen.HOME
        // Searching hides the bottom bar, so search results never match this.
        if (isTabSelected(Instagram.SEARCH_TAB) && nodes.any(::isGridTile)) return Screen.EXPLORE
        return Screen.OTHER
    }

    /** A reel opened from a chat has a reply bar under it. */
    fun isChatReel(): Boolean = nodes.any { it.id.startsWith("reply_bar") }

    /**
     * Bottom bar buttons that lead to a feed. Home stays tappable while [homeOpen], or when Messages
     * has no button of its own, because Home is then the only way back to the inbox. Reels stays
     * tappable while [reelsTabOpen], when the day's scroll limit has reels left.
     */
    fun feedTabs(reelsTabOpen: Boolean = false, homeOpen: Boolean = false): List<ViewNode> {
        val ids = buildList {
            if (!homeOpen && tab(Instagram.MESSAGES_TAB) != null) add(Instagram.HOME_TAB)
            if (!reelsTabOpen) add(Instagram.REELS_TAB)
        }
        return ids.mapNotNull(::tab)
    }

    /** The feed between the top bar or search bar and the bottom bar. */
    fun feedArea(): Box {
        val top = nodes
            .filter { it.id in Instagram.TOP_BARS && it.box.bottom <= screenHeight * 0.3 }
            .maxOfOrNull { it.box.bottom }
            ?: nodes.filter(::isGridTile).minOfOrNull { it.box.top }
            ?: 0
        val bottom = bottomBarTabs().minOfOrNull { it.box.top } ?: screenHeight
        return Box(0, top, screenWidth, bottom)
    }

    /**
     * Where the row of stories ends on Home, or null once it has scrolled away. The row itself is
     * left out of the accessibility tree, so its stories stand in for it.
     */
    fun storiesBottom(): Int? {
        val avatars = nodes.filter { it.id == Instagram.STORY_AVATAR }
        return nodes
            .filter { story ->
                story.id == Instagram.STORY && story.box.top <= screenHeight * 0.3 && avatars.any { it.box in story.box }
            }
            .maxOfOrNull { it.box.bottom }
    }

    private fun bottomBarTabs() = nodes.filter { it.id.endsWith("_tab") && it.box.top > screenHeight * 0.75 }

    private fun tab(id: String): ViewNode? = bottomBarTabs().firstOrNull { it.id == id }

    // Home marks a child of its tab as selected, Reels marks the tab itself.
    private fun isTabSelected(id: String): Boolean {
        val tab = tab(id) ?: return false
        return nodes.any { it.selected && it.box in tab.box }
    }

    private fun hasHomeLogo() =
        tab(Instagram.HOME_TAB) != null && nodes.any { it.id == Instagram.HOME_LOGO && it.box.bottom <= screenHeight * 0.3 }

    // The story viewer is called "reel_viewer" inside Instagram, so it never matches these.
    private fun hasReelViewer() = nodes.any { node ->
        val id = node.id
        val isViewerPart = id.startsWith("clips_view") || id.startsWith("clips_video") || id == "root_clips_layout"
        isViewerPart && node.box.height > screenHeight * 0.6
    }

    // The unread badge on the Messages button has "inbox" in its ID too, but it is small.
    private fun hasInbox() = nodes.any {
        it.id.contains("inbox") && !it.id.startsWith("action_bar") && it.box.height > screenHeight / 3
    }

    private fun isGridTile(node: ViewNode) =
        node.id == "grid_card_layout_container" || GRID_TILE.containsMatchIn(node.desc)

    private companion object {
        /** Explore tiles are labelled like "Reel by someone at row 1, column 3". */
        val GRID_TILE = Regex("""row \d+, column \d+""")
    }
}
