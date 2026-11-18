package app.mori

/** What the service does next. */
sealed interface Action {
    data object None : Action

    /** Look at the screen again soon, because the last look may have caught a transition. */
    data object LookAgain : Action

    data object OpenMessages : Action

    data object LeaveInstagram : Action

    /** Scroll the reel viewer one page back (-1) or forward (+1), toward the reel that was opened. */
    data class ReturnToReel(val direction: Int) : Action

    /** Close the reel or short viewer, because single reels are off or scrolling on is not allowed. */
    data object CloseReel : Action

    /** The user scrolled on to a reel or short not seen yet, and it used up one of the day's scrolls. */
    data object CountReel : Action
}

/**
 * Decides what to do about each Instagram screen and each reel swipe. It takes plain values and
 * times, so the rules run in unit tests. [GuardService] does the reading and the acting.
 */
class Guard {

    /** The screen two looks in a row agreed on. */
    var screen: Screen? = null
        private set

    /** Opening Messages did not move Instagram off a feed, so the feed gets covered instead. */
    var redirectFailed = false
        private set

    /** Whether a reel or short opened on its own may play. The service sets it from the app's switch. */
    var reelsAllowed = true

    /** How many more reels or shorts the user may scroll on to today. The service sets it from the daily limit. */
    var scrollsLeft = 0

    /**
     * Whether Instagram's Home stays open instead of going to Messages. Its feed is full of reels, so
     * while it is open only reels from chats play, unless the day's limit has some left.
     */
    var homeOpen = false

    private var candidate: Screen? = null
    private var sawMessages = false
    private var lastTapAt = NEVER
    private var lastRedirectAt = NEVER
    private var redirects = 0

    private var reelSince = NEVER
    // The pages of the reel viewer the user has seen, which they may scroll between freely.
    private var firstSeen = NO_PAGE
    private var lastSeen = NO_PAGE
    private var lastReturnAt = NEVER
    private var backedOutOfReel = false
    private var currentShort: Int? = null

    fun onLeftApp() {
        screen = null
        candidate = null
        sawMessages = false
        lastTapAt = NEVER
        lastRedirectAt = NEVER
        redirects = 0
        redirectFailed = false
        endReel()
    }

    fun onTap(now: Long) {
        lastTapAt = now
    }

    /** [fromChat] says the reel on screen was opened from a chat. */
    fun onScreen(seen: Screen, now: Long, fromChat: Boolean = false): Action {
        if (seen.isReel && reelSince == NEVER) reelSince = now
        if (seen != screen) {
            // Screens flicker while they change, so a new screen counts once two looks in a row agree.
            if (seen != candidate) {
                candidate = seen
                return Action.LookAgain
            }
            screen = seen
        }
        candidate = null
        if (!seen.isReel) endReel()

        return when (seen) {
            Screen.MESSAGES -> {
                sawMessages = true
                redirects = 0
                redirectFailed = false
                Action.None
            }
            Screen.HOME -> if (homeOpen) Action.None else onHome(now)
            // The Reels tab is a feed of reels, so it is open only while the day's limit has some left.
            Screen.REELS_TAB -> if (scrollsLeft > 0) Action.None else openMessages(now)
            Screen.REEL -> if (reelMayPlay(fromChat) || !backOutOfReel()) Action.None else Action.CloseReel
            else -> Action.None
        }
    }

    fun onReelScroll(index: Int, now: Long): Action {
        if (index < 0) return Action.None
        if (reelSince == NEVER) reelSince = now
        if (now - reelSince < OPENING_MS) {
            // The viewer scrolls itself to the chosen reel while it opens. That is not the user.
            firstSeen = index
            lastSeen = index
            return Action.None
        }
        // No scroll while opening means the viewer opened on its first page.
        if (firstSeen == NO_PAGE) {
            firstSeen = 0
            lastSeen = 0
        }
        if (index in firstSeen..lastSeen) return Action.None
        if (scrollsLeft > 0) {
            scrollsLeft--
            if (index > lastSeen) lastSeen = index else firstSeen = index
            return Action.CountReel
        }
        if (now - lastReturnAt < RETURN_GAP_MS) return Action.None
        lastReturnAt = now
        return Action.ReturnToReel(if (index > lastSeen) -1 else 1)
    }

    /**
     * YouTube's Shorts player has no page numbers to follow, so it is followed by which short is on
     * screen. YouTube reuses the views of shorts, so one seen before cannot be told apart and every
     * change counts.
     */
    fun onShortShown(short: Int, now: Long): Action {
        if (reelSince == NEVER) reelSince = now
        // The first short seen, or the last one seen while the player opens, is the one the user chose.
        if (currentShort == null || now - reelSince < OPENING_MS) {
            currentShort = short
            return Action.None
        }
        if (short == currentShort) return Action.None
        if (scrollsLeft > 0) {
            scrollsLeft--
            currentShort = short
            return Action.CountReel
        }
        return if (backOutOfReel()) Action.CloseReel else Action.None
    }

    /** True once per reel, when the viewer cannot be scrolled back and should be closed instead. */
    fun backOutOfReel(): Boolean {
        if (backedOutOfReel) return false
        backedOutOfReel = true
        return true
    }

    private fun reelMayPlay(fromChat: Boolean) = reelsAllowed && (fromChat || !homeOpen || scrollsLeft > 0)

    // Messages acts as Instagram's first screen, so going back from it onto Home leaves Instagram.
    // A tap shortly before means the user pressed Home, which only happens when Home is the way to Messages.
    private fun onHome(now: Long): Action {
        val tapped = now - lastTapAt < TAP_WINDOW_MS
        if (!sawMessages || tapped) return openMessages(now)
        if (now - lastRedirectAt < REDIRECT_GAP_MS) return Action.None
        lastRedirectAt = now
        return Action.LeaveInstagram
    }

    private fun openMessages(now: Long): Action {
        if (now - lastRedirectAt < REDIRECT_GAP_MS) return Action.None
        if (redirects >= MAX_REDIRECTS) {
            redirectFailed = true
            return Action.None
        }
        redirects++
        lastRedirectAt = now
        return Action.OpenMessages
    }

    private fun endReel() {
        reelSince = NEVER
        firstSeen = NO_PAGE
        lastSeen = NO_PAGE
        lastReturnAt = NEVER
        backedOutOfReel = false
        currentShort = null
    }

    companion object {
        const val OPENING_MS = 1_000L
        const val RETURN_GAP_MS = 350L
        const val REDIRECT_GAP_MS = 1_500L
        const val TAP_WINDOW_MS = 1_500L
        const val MAX_REDIRECTS = 3

        private const val NEVER = Long.MIN_VALUE / 2
        private const val NO_PAGE = -1
    }
}
