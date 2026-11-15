package app.mori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {

    private val guard = Guard()

    /** Shows [screen] for two looks in a row, which confirms it, and returns the second answer. */
    private fun show(screen: Screen, now: Long): Action {
        assertEquals(Action.LookAgain, guard.onScreen(screen, now))
        return guard.onScreen(screen, now + 100)
    }

    @Test
    fun aNewScreenNeedsTwoLooks() {
        assertEquals(Action.LookAgain, guard.onScreen(Screen.HOME, 0))
        assertEquals(Action.OpenMessages, guard.onScreen(Screen.HOME, 100))
    }

    @Test
    fun openingInstagramOnHomeGoesToMessages() {
        assertEquals(Action.OpenMessages, show(Screen.HOME, 0))
        assertEquals(Action.None, guard.onScreen(Screen.HOME, 500))
        assertEquals(Action.None, show(Screen.MESSAGES, 800))
    }

    @Test
    fun goingBackFromMessagesLeavesInstagram() {
        show(Screen.MESSAGES, 0)
        assertEquals(Action.LeaveInstagram, show(Screen.HOME, 1_000))
    }

    @Test
    fun tappingHomeGoesToMessages() {
        show(Screen.MESSAGES, 0)
        guard.onTap(1_000)
        assertEquals(Action.OpenMessages, show(Screen.HOME, 1_100))
    }

    @Test
    fun aFreshVisitStartsOverOnMessages() {
        show(Screen.MESSAGES, 0)
        guard.onLeftApp()
        assertEquals(Action.OpenMessages, show(Screen.HOME, 10_000))
    }

    @Test
    fun reelsTabGoesToMessages() {
        assertEquals(Action.OpenMessages, show(Screen.REELS_TAB, 0))
    }

    @Test
    fun theReelsTabOpensWhileTheLimitHasReelsLeft() {
        guard.scrollsLeft = 5
        assertEquals(Action.None, show(Screen.REELS_TAB, 0))
        guard.scrollsLeft = 0
        assertEquals(Action.OpenMessages, guard.onScreen(Screen.REELS_TAB, 2_000))
    }

    @Test
    fun coversTheFeedWhenOpeningMessagesKeepsFailing() {
        var now = 0L
        repeat(Guard.MAX_REDIRECTS) {
            assertFalse(guard.redirectFailed)
            now += Guard.REDIRECT_GAP_MS
            guard.onScreen(Screen.HOME, now)
            guard.onScreen(Screen.HOME, now)
        }
        guard.onScreen(Screen.HOME, now + Guard.REDIRECT_GAP_MS)
        assertTrue(guard.redirectFailed)

        show(Screen.MESSAGES, now + 10_000)
        assertFalse(guard.redirectFailed)
    }

    @Test
    fun aReelPlaysWhileReelsAreAllowed() {
        assertEquals(Action.None, show(Screen.REEL, 0))
    }

    @Test
    fun aReelClosesOnceWhenReelsAreOff() {
        guard.reelsAllowed = false
        assertEquals(Action.CloseReel, show(Screen.REEL, 0))
        assertEquals(Action.None, guard.onScreen(Screen.REEL, 300))
        show(Screen.OTHER, 1_000)
        assertEquals(Action.CloseReel, show(Screen.REEL, 2_000))
    }

    @Test
    fun theReelsTabStillGoesToMessagesWhenReelsAreOff() {
        guard.reelsAllowed = false
        assertEquals(Action.OpenMessages, show(Screen.REELS_TAB, 0))
    }

    @Test
    fun theOpeningScrollSetsTheReel() {
        show(Screen.REEL, 0)
        assertEquals(Action.None, guard.onReelScroll(4, 300))
        assertEquals(Action.None, guard.onReelScroll(4, 2_000))
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(5, 2_500))
        assertEquals(Action.ReturnToReel(1), guard.onReelScroll(3, 3_000))
    }

    @Test
    fun withoutAnOpeningScrollTheFirstReelIsTheOne() {
        show(Screen.REEL, 0)
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(1, 2_000))
    }

    @Test
    fun returnsAreSpacedOut() {
        show(Screen.REEL, 0)
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(1, 2_000))
        assertEquals(Action.None, guard.onReelScroll(1, 2_100))
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(1, 2_000 + Guard.RETURN_GAP_MS))
    }

    @Test
    fun theReelsTabHidingItsBarIsStillTheSameReel() {
        show(Screen.REELS_TAB, 0)
        guard.onReelScroll(0, 2_000)
        show(Screen.REEL, 2_100)
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(1, 3_000))
    }

    @Test
    fun leavingTheReelForgetsIt() {
        show(Screen.REEL, 0)
        guard.onReelScroll(3, 200)
        show(Screen.OTHER, 5_000)
        show(Screen.REEL, 10_000)
        assertEquals(Action.None, guard.onReelScroll(0, 10_300))
        assertEquals(Action.None, guard.onReelScroll(0, 12_000))
    }

    @Test
    fun aDifferentShortIsCaught() {
        show(Screen.REEL, 0)
        assertEquals(Action.None, guard.onShortShown(7, 200))
        assertEquals(Action.None, guard.onShortShown(7, 2_000))
        assertEquals(Action.CloseReel, guard.onShortShown(9, 2_500))
        assertEquals(Action.None, guard.onShortShown(11, 3_000))
    }

    @Test
    fun theShortShownWhileOpeningIsTheOne() {
        show(Screen.REEL, 0)
        guard.onShortShown(7, 200)
        guard.onShortShown(9, 600)
        assertEquals(Action.None, guard.onShortShown(9, 2_000))
        assertEquals(Action.CloseReel, guard.onShortShown(7, 2_500))
    }

    @Test
    fun leavingTheShortsPlayerForgetsTheShort() {
        show(Screen.REEL, 0)
        guard.onShortShown(7, 200)
        show(Screen.OTHER, 5_000)
        show(Screen.REEL, 10_000)
        assertEquals(Action.None, guard.onShortShown(9, 10_200))
        assertEquals(Action.None, guard.onShortShown(9, 12_000))
    }

    @Test
    fun reelsCountUntilTheDayRunsOut() {
        show(Screen.REEL, 0)
        guard.onReelScroll(0, 200)
        guard.scrollsLeft = 2
        assertEquals(Action.CountReel, guard.onReelScroll(1, 2_000))
        assertEquals(Action.None, guard.onReelScroll(1, 2_100))
        assertEquals(Action.CountReel, guard.onReelScroll(2, 2_500))
        assertEquals(Action.ReturnToReel(-1), guard.onReelScroll(3, 3_000))
    }

    @Test
    fun goingBackToASeenReelIsFree() {
        show(Screen.REEL, 0)
        guard.onReelScroll(0, 200)
        guard.scrollsLeft = 2
        guard.onReelScroll(1, 2_000)
        guard.onReelScroll(2, 2_500)
        assertEquals(Action.None, guard.onReelScroll(1, 3_000))
        assertEquals(Action.None, guard.onReelScroll(0, 3_500))
    }

    @Test
    fun shortsCountUntilTheDayRunsOut() {
        show(Screen.REEL, 0)
        guard.onShortShown(7, 200)
        guard.scrollsLeft = 1
        assertEquals(Action.CountReel, guard.onShortShown(9, 2_000))
        assertEquals(Action.None, guard.onShortShown(9, 2_100))
        assertEquals(Action.CloseReel, guard.onShortShown(11, 2_500))
    }

    @Test
    fun backsOutOfAReelOnlyOnce() {
        show(Screen.REEL, 0)
        assertTrue(guard.backOutOfReel())
        assertFalse(guard.backOutOfReel())
    }
}
