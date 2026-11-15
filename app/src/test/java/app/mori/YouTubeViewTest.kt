package app.mori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeViewTest {

    private val width = 1080
    private val height = 2340

    private fun viewOf(vararg nodes: ViewNode) = YouTubeView(nodes.toList(), height)

    private fun bottomBar() = listOf("Home", "Shorts", "Create", "Subscriptions", "You").mapIndexed { i, label ->
        ViewNode("", Box(i * 216, 2180, (i + 1) * 216, 2300), desc = label)
    }

    @Test
    fun aShortFillsTheScreen() {
        assertEquals(Screen.REEL, viewOf(ViewNode("reel_recycler", Box(0, 0, width, 2180))).screen())
    }

    @Test
    fun aLeftoverTimeBarIsNotAShort() {
        val timeBar = ViewNode("reel_player_time_bar", Box(0, 2160, width, 2180))
        assertEquals(Screen.OTHER, viewOf(timeBar).screen())
    }

    @Test
    fun theShortOnScreenIsThePageInTheMiddle() {
        val leaving = ViewNode("reel_player_page_container", Box(0, -1500, width, 580), key = 1)
        val arriving = ViewNode("reel_player_page_container", Box(0, 580, width, 2660), key = 2)
        assertEquals(2, viewOf(leaving, arriving).shortOnScreen())
    }

    @Test
    fun findsTheShortsButtonInTheBottomBar() {
        val view = YouTubeView(bottomBar(), height)
        assertEquals(Box(216, 2180, 432, 2300), view.shortsButton()?.box)
    }

    @Test
    fun theShortsShelfOnHomeIsNotTheButton() {
        val shelf = ViewNode("", Box(40, 900, 400, 960), desc = "Shorts")
        assertNull(viewOf(shelf).shortsButton())
    }
}
