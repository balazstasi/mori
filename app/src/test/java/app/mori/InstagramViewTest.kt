package app.mori

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramViewTest {

    private val width = 1080
    private val height = 2400

    // Bottom bar of the current Instagram layout, with Messages as a tab.
    private fun tabs(selected: String, selectChild: Boolean = false): List<ViewNode> {
        val ids = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")
        return ids.flatMapIndexed { i, id ->
            val box = Box(i * 216, 2250, (i + 1) * 216, 2400)
            val icon = Box(box.left + 80, 2290, box.left + 136, 2346)
            listOf(
                ViewNode(id, box, selected = id == selected && !selectChild),
                ViewNode("tab_icon", icon, selected = id == selected && selectChild),
            )
        }
    }

    private fun fullScreen(id: String) = ViewNode(id, Box(0, 0, width, 2250))

    private fun screenOf(vararg nodes: List<ViewNode>) =
        InstagramView(nodes.toList().flatten(), width, height).screen()

    @Test
    fun homeMarksAChildOfItsTab() {
        assertEquals(Screen.HOME, screenOf(tabs("feed_tab", selectChild = true)))
    }

    @Test
    fun homeWithoutASelectedTabHasTheLogo() {
        // Instagram 455 marks nothing selected on Home.
        val logo = ViewNode("title_logo", Box(380, 90, 700, 200))
        assertEquals(Screen.HOME, screenOf(tabs(selected = ""), listOf(logo)))
    }

    @Test
    fun postsWithoutTheLogoAreNotHome() {
        val post = ViewNode("row_feed_photo_profile_name", Box(150, 400, 600, 460))
        assertEquals(Screen.OTHER, screenOf(tabs(selected = ""), listOf(post)))
    }

    @Test
    fun aTextBoxMeansAChatNotHome() {
        val composer = ViewNode("row_thread_composer_edittext", Box(0, 2100, width, 2200), editable = true)
        assertEquals(Screen.OTHER, screenOf(tabs("feed_tab", selectChild = true), listOf(composer)))
    }

    @Test
    fun reelsTabWithItsViewer() {
        assertEquals(Screen.REELS_TAB, screenOf(tabs("clips_tab"), listOf(fullScreen("clips_viewer_view_pager"))))
    }

    @Test
    fun reelsTabWhileItsBottomBarIsHidden() {
        assertEquals(Screen.REEL, screenOf(listOf(fullScreen("clips_viewer_view_pager"))))
    }

    @Test
    fun reelFromAChatKeepsItsReplyBarEvenUnderTheReelsTab() {
        val replyBar = ViewNode("reply_bar_edittext", Box(0, 2100, width, 2200), editable = true)
        assertEquals(
            Screen.REEL,
            screenOf(tabs("clips_tab"), listOf(fullScreen("clips_viewer_view_pager"), replyBar)),
        )
    }

    @Test
    fun storiesAreNotReels() {
        assertEquals(Screen.OTHER, screenOf(listOf(fullScreen("reel_viewer_media_container"))))
    }

    @Test
    fun aSmallReelPreviewIsNotTheViewer() {
        val preview = ViewNode("clips_video_container", Box(0, 600, 540, 1200))
        assertEquals(Screen.OTHER, screenOf(listOf(preview)))
    }

    @Test
    fun messagesTab() {
        assertEquals(Screen.MESSAGES, screenOf(tabs("direct_tab")))
    }

    @Test
    fun inboxWithoutAMessagesTab() {
        val inbox = ViewNode("inbox_refreshable_thread_list_recyclerview", Box(0, 300, width, 2250))
        assertEquals(Screen.MESSAGES, screenOf(listOf(inbox)))
    }

    @Test
    fun theUnreadBadgeIsNotTheInbox() {
        val badge = ViewNode("inbox_badge", Box(500, 2260, 540, 2300))
        assertEquals(Screen.HOME, screenOf(tabs("feed_tab", selectChild = true), listOf(badge)))
    }

    @Test
    fun exploreGridUnderTheSearchTab() {
        val tile = ViewNode("image_button", Box(0, 300, 360, 660), desc = "Reel by someone at row 1, column 1")
        assertEquals(Screen.EXPLORE, screenOf(tabs("search_tab"), listOf(tile)))
    }

    @Test
    fun searchResultsStayUsable() {
        val result = ViewNode("row_search_user_container", Box(0, 300, width, 450), desc = "someone")
        assertEquals(Screen.OTHER, screenOf(listOf(result)))
    }

    @Test
    fun hidesHomeAndReelsWhenMessagesHasItsOwnTab() {
        val view = InstagramView(tabs("direct_tab"), width, height)
        assertEquals(listOf("feed_tab", "clips_tab"), view.feedTabs().map { it.id })
    }

    @Test
    fun leavesReelsTappableWhileTheLimitHasReelsLeft() {
        val view = InstagramView(tabs("direct_tab"), width, height)
        assertEquals(listOf("feed_tab"), view.feedTabs(reelsTabOpen = true).map { it.id })
    }

    @Test
    fun keepsHomeTappableWhenItIsTheWayToMessages() {
        val oldTabs = tabs("feed_tab").filterNot { it.id == "direct_tab" }
        val view = InstagramView(oldTabs, width, height)
        assertEquals(listOf("clips_tab"), view.feedTabs().map { it.id })
    }

    @Test
    fun feedAreaSitsBetweenTheBars() {
        val searchBar = ViewNode("action_bar_search_edit_text", Box(40, 120, 1040, 250))
        val view = InstagramView(tabs("search_tab") + searchBar, width, height)
        assertEquals(Box(0, 250, width, 2250), view.feedArea())
    }

    @Test
    fun recognizesTheReelPager() {
        assertEquals(true, Instagram.isReelPager("clips_viewer_view_pager"))
        assertEquals(false, Instagram.isReelPager("reel_viewer_pager"))
    }
}
