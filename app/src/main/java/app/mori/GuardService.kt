package app.mori

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Keeps Instagram to messages and YouTube Shorts to one at a time. It reads which screen is showing,
 * sends Instagram's Home and Reels tab to Messages, covers the Explore grid and the feed buttons,
 * and scrolls a reel or short back to the one that was opened.
 *
 * Events arrive on the main thread. Reading an app's view tree is slow, so it happens on [worker],
 * which also owns [guard]. Covers are drawn on the main thread by [Overlays].
 */
class GuardService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("guard").apply { start() }
    private val worker = Handler(workerThread.looper)

    private val guard = Guard()
    /** The app [guard] is following, so switching apps starts it over. */
    private var app: String? = null
    private lateinit var prefs: Prefs
    private lateinit var overlays: Overlays

    private var evaluationDueAt = NOT_SCHEDULED
    private val evaluation = Runnable {
        evaluationDueAt = NOT_SCHEDULED
        evaluate()
    }
    private var lastToastAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        overlays = Overlays(this)
    }

    override fun onServiceConnected() {
        // A switch changed, so start over: an app turned off loses its covers, one turned on gets a look.
        prefs.onChange {
            worker.post {
                stopGuarding()
                scheduleEvaluation()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val type = event.eventType
        if (!prefs.guards(pkg)) {
            // Another app came up, so the covers have to go.
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName) {
                worker.post { scheduleEvaluation() }
            }
            return
        }

        // The event is reused after this call returns, so copy what the worker needs now.
        val source = when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_VIEW_CLICKED -> event.source
            else -> null
        }
        val index = event.fromIndex
        worker.post {
            if (!prefs.guards(pkg)) return@post
            when (type) {
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (pkg == Instagram.PACKAGE) source?.let { onScroll(it, index) }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    guard.onTap(now())
                    if (pkg == Instagram.PACKAGE) source?.let(::onTap)
                }
                else -> Unit
            }
            scheduleEvaluation()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        prefs.stopListening()
        overlays.clear()
        workerThread.quitSafely()
        super.onDestroy()
    }

    private fun scheduleEvaluation(delayMs: Long = EVALUATION_DELAY_MS) {
        val due = now() + delayMs
        // Instagram sends events nonstop while a video plays, so keep the earlier time rather than
        // pushing the look back with every event.
        if (evaluationDueAt != NOT_SCHEDULED && evaluationDueAt <= due) return
        worker.removeCallbacks(evaluation)
        evaluationDueAt = due
        worker.postAtTime(evaluation, due)
    }

    private fun evaluate() {
        val root = activeRoot() ?: return
        val pkg = root.packageName?.toString()
        // Touching a cover makes it the active window. The guarded app is still underneath.
        if (pkg == packageName) return
        if (pkg != app) {
            stopGuarding()
            app = pkg
        }
        if (!prefs.guards(pkg)) return
        if (pkg == Instagram.PACKAGE) guardInstagram(root) else guardYouTube(root)
    }

    private fun guardInstagram(root: AccessibilityNodeInfo) {
        val (width, height) = screenSize()
        val snapshot = Snapshot.read(root)
        val view = InstagramView(snapshot.nodes, width, height)
        val seen = view.screen()
        guard.reelsAllowed = prefs.instagramReels
        guard.scrollsLeft = scrollsLeft(Instagram.PACKAGE)
        val action = guard.onScreen(seen, now())
        when (action) {
            Action.LookAgain -> scheduleEvaluation(LOOK_AGAIN_MS)
            Action.OpenMessages -> {
                openMessages(snapshot.button(Instagram.MESSAGES_TAB) ?: snapshot.button(Instagram.INBOX_BUTTON))
                scheduleEvaluation(Guard.REDIRECT_GAP_MS + LOOK_AGAIN_MS)
            }
            Action.LeaveInstagram -> performGlobalAction(GLOBAL_ACTION_HOME)
            Action.CloseReel -> closeReel(R.string.toast_reels_off)
            else -> Unit
        }
        overlays.show(instagramCovers(view, seen))
    }

    private fun guardYouTube(root: AccessibilityNodeInfo) {
        val snapshot = Snapshot.read(root)
        val view = YouTubeView(snapshot.nodes, screenSize().second)
        val seen = view.screen()
        val now = now()
        guard.reelsAllowed = prefs.youtubeShorts
        guard.scrollsLeft = scrollsLeft(YouTube.PACKAGE)
        val action = guard.onScreen(seen, now)
        when (action) {
            Action.LookAgain -> scheduleEvaluation(LOOK_AGAIN_MS)
            Action.CloseReel -> closeReel(R.string.toast_shorts_off)
            else -> Unit
        }
        val short = view.shortOnScreen()
        if (seen == Screen.REEL && short != null) {
            when (guard.onShortShown(short, now)) {
                Action.CountReel -> countScroll(YouTube.PACKAGE)
                // The Shorts player cannot be scrolled back, so a swipe past what is allowed closes it.
                Action.CloseReel -> {
                    sayScrollingStopped(YouTube.PACKAGE)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                else -> Unit
            }
        }
        // The Shorts button opens while the day's limit has shorts left.
        val shortsButton = view.shortsButton()?.takeIf { guard.scrollsLeft == 0 }
        overlays.show(listOfNotNull(shortsButton?.let { Cover(SHORTS_COVER, it.box) }))
    }

    private fun closeReel(message: Int) {
        showToast(message)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun instagramCovers(view: InstagramView, seen: Screen): List<Cover> = buildList {
        view.feedTabs(reelsTabOpen = guard.scrollsLeft > 0).forEach { add(Cover(it.id, it.box)) }
        // Only cover a feed once two looks agree, so a passing frame never gets a panel.
        if (seen != guard.screen) return@buildList
        when {
            seen == Screen.EXPLORE -> add(Cover(FEED_COVER, view.feedArea(), getString(R.string.cover_explore)))
            seen == Screen.HOME && guard.redirectFailed ->
                add(Cover(FEED_COVER, view.feedArea(), getString(R.string.cover_home)))
        }
    }

    private fun onScroll(pager: AccessibilityNodeInfo, index: Int) {
        val id = pager.viewIdResourceName?.substringAfter(":id/").orEmpty()
        if (!Instagram.isReelPager(id)) return
        guard.scrollsLeft = scrollsLeft(Instagram.PACKAGE)
        when (val action = guard.onReelScroll(index, now())) {
            Action.CountReel -> countScroll(Instagram.PACKAGE)
            is Action.ReturnToReel -> returnToReel(pager, action.direction)
            else -> Unit
        }
    }

    private fun returnToReel(pager: AccessibilityNodeInfo, direction: Int) {
        val scroll = if (direction < 0) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        if (pager.performAction(scroll)) {
            sayScrollingStopped(Instagram.PACKAGE)
        } else if (guard.backOutOfReel()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** What is left of the app's daily limit. Single reels turned off leave nothing to scroll. */
    private fun scrollsLeft(pkg: String): Int {
        val reelsAllowed = if (pkg == YouTube.PACKAGE) prefs.youtubeShorts else prefs.instagramReels
        val limit = prefs.limit(pkg)
        if (!reelsAllowed || limit == 0) return 0
        return (limit - prefs.scrolledToday(pkg)).coerceAtLeast(0)
    }

    private fun countScroll(pkg: String) {
        val count = prefs.addScroll(pkg)
        overlays.showCounter(getString(R.string.counter, count, prefs.limit(pkg)))
    }

    /** Tells the user why the next reel or short did not come: the day's limit, or no scrolling at all. */
    private fun sayScrollingStopped(pkg: String) {
        val limit = prefs.limit(pkg)
        when {
            limit > 0 -> overlays.showCounter(getString(R.string.counter_done, limit, limit))
            pkg == YouTube.PACKAGE -> showToast(R.string.toast_one_short)
            else -> showToast(R.string.toast_one_reel)
        }
    }

    /**
     * A cover goes up a moment after a chat closes, so a quick tap can still reach Home or Reels.
     * Instagram reports the tap, and the service answers it by going straight back to Messages.
     */
    private fun onTap(node: AccessibilityNodeInfo) {
        val id = node.viewIdResourceName?.substringAfter(":id/")
        if (id != Instagram.HOME_TAB && id != Instagram.REELS_TAB) return
        if (id == Instagram.REELS_TAB && scrollsLeft(Instagram.PACKAGE) > 0) return
        val messagesTab = activeRoot()
            ?.findAccessibilityNodeInfosByViewId("${Instagram.PACKAGE}:id/${Instagram.MESSAGES_TAB}")
            ?.firstOrNull()
        // Without a Messages tab, Home is the way to the inbox, so it stays.
        if (id == Instagram.HOME_TAB && messagesTab == null) return
        openMessages(messagesTab)
    }

    private fun openMessages(button: AccessibilityNodeInfo?) {
        if (button != null && click(button)) return
        val intent = Intent(Intent.ACTION_VIEW, Instagram.INBOX_LINK.toUri())
            .setPackage(Instagram.PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Instagram did not accept the inbox link", e)
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    private fun stopGuarding() {
        guard.onLeftApp()
        app = null
        overlays.clear()
    }

    private fun activeRoot(): AccessibilityNodeInfo? = rootInActiveWindow
        ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun showToast(message: Int) {
        val now = now()
        if (now - lastToastAt < TOAST_GAP_MS) return
        lastToastAt = now
        main.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun now() = SystemClock.uptimeMillis()

    private companion object {
        const val TAG = "GuardService"
        const val FEED_COVER = "feed"
        const val SHORTS_COVER = "shorts"
        const val EVALUATION_DELAY_MS = 100L
        const val LOOK_AGAIN_MS = 120L
        const val TOAST_GAP_MS = 4_000L
        const val NOT_SCHEDULED = -1L
    }
}

/** The visible views of the active window, plus handles to the Instagram buttons the service may press. */
private class Snapshot(val nodes: List<ViewNode>, private val buttons: Map<String, AccessibilityNodeInfo>) {

    fun button(id: String): AccessibilityNodeInfo? = buttons[id]

    companion object {
        private const val MAX_NODES = 3_000
        private val BUTTON_IDS = setOf(Instagram.MESSAGES_TAB, Instagram.INBOX_BUTTON)

        fun read(root: AccessibilityNodeInfo): Snapshot {
            val nodes = ArrayList<ViewNode>(256)
            val buttons = HashMap<String, AccessibilityNodeInfo>()
            val stack = ArrayDeque<AccessibilityNodeInfo>().apply { addLast(root) }
            val rect = Rect()
            while (stack.isNotEmpty() && nodes.size < MAX_NODES) {
                val node = stack.removeLast()
                node.getBoundsInScreen(rect)
                // Apps keep the pages of other tabs in the tree, hidden or with empty bounds.
                if (!node.isVisibleToUser || rect.isEmpty) continue
                val id = node.viewIdResourceName?.substringAfter(":id/").orEmpty()
                nodes += ViewNode(
                    id = id,
                    box = Box(rect.left, rect.top, rect.right, rect.bottom),
                    desc = node.contentDescription?.toString().orEmpty(),
                    selected = node.isSelected,
                    editable = node.isEditable,
                    key = node.hashCode(),
                )
                if (id in BUTTON_IDS) buttons.putIfAbsent(id, node)
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::addLast)
            }
            return Snapshot(nodes, buttons)
        }
    }
}
