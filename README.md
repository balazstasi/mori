<p align="center">
  <img src="assets/banner.svg" alt="Mori. You're gonna die soon, don't waste it." width="100%">
</p>

<p align="center">
  <a href="https://github.com/balazstasi/mori/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/balazstasi/mori?style=flat-square&color=1E4034&label=release"></a>
  <img alt="Android 8.0 or newer" src="https://img.shields.io/badge/Android-8.0%2B-1E4034?style=flat-square&logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-1E4034?style=flat-square&logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="GPL 3.0" src="https://img.shields.io/github/license/balazstasi/mori?style=flat-square&color=1E4034"></a>
</p>

<p align="center">
  <b>Instagram for messages. Shorts one at a time.</b><br>
  An Android app that turns off the endless feeds in Instagram and YouTube and leaves the parts you came for.
</p>

<p align="center">
  <a href="https://github.com/balazstasi/mori/releases/latest"><b>Download the APK</b></a>
</p>

## What it does

|  | Instagram | YouTube |
| --- | --- | --- |
| **Turned off** | Home feed, Reels tab, Explore grid, scrolling on to the next reel | Shorts button, scrolling on to the next short |
| **Still works** | Messages and calls, search, stories, profiles | Home, search, subscriptions, regular videos |
| **Single reels and shorts** | A reel from a chat or a link plays on its own. Switch it off and reels close as they open. | A short from Home, search or a link plays on its own. Switch it off and shorts close as they open. |
| **Daily scroll limit** | 10 to 300 reels a day. While some are left, the Reels tab opens and you can scroll on. | 10 to 300 shorts a day. While some are left, the Shorts button opens and you can scroll on. |

Instagram opens straight to your messages. To see more, set Instagram's **Home** to **Stories**, which opens Home with only its row of stories, or **Feed**, which opens all of Home. While Home is open, only reels from chats play. A covered button looks dimmed and ignores taps. When you swipe past what is allowed, Mori takes you back to the reel you opened, or closes the short. With a daily limit, a small counter shows "32/100" as you scroll, and once the limit is used up, everything is blocked again until midnight.

Each app has its own switch, so you can guard only Instagram, only YouTube, or both.

## Requirements

- Android 8.0 or newer. The Material You colors need Android 12 or newer.
- Instagram or YouTube from Google Play.
- Accessibility access for Mori. It is the only permission Mori asks for.

Mori was tested on a Samsung Galaxy S23 FE with Android 16, Instagram 455 and YouTube 21.45.

## Install

1. Download `Mori-0.2.1.apk` from [Releases](https://github.com/balazstasi/mori/releases/latest) and open it. If Android asks, allow your browser or file manager to install apps.
2. Open Mori. On Android 13 and newer, tap **App info** in the setup card, open the menu in the top corner and choose **Allow restricted settings**. Android asks for this for every app installed from outside an app store.
3. Tap **Turn on** and switch on Mori in Accessibility.
4. Pick what each app allows with its switches and scroll limit.

The setup card shows a check next to each step once it is done.

To check the download, compare the signing certificate with `apksigner verify --print-certs Mori-0.2.1.apk`. Its SHA-256 digest is:

```
ef5abb3ee2479e90062bc21df2f9e96852d1aa532265eeb8304cd465b435b757
```

## Privacy

Mori has no internet permission, so it cannot send anything anywhere. It reads the names, positions and labels of the elements on screen in Instagram and YouTube to tell screens apart. It keeps nothing except its settings and today's scroll counts, on your phone. It ignores every other app.

## Known limitations

- Instagram and YouTube rename their screen elements without notice. After an app update, a rule can stop working until Mori is updated.
- YouTube's Shorts button is found by its label, which is in English. On a phone set to another language, the button may stay uncovered. The one-short rule still works.
- YouTube's Shorts player cannot be scrolled back, so a swipe past the allowed short closes the player.
- On YouTube, every change of short counts against the daily limit, including going back to one you already saw.
- You may see the first frame of a reel or short before Mori closes it.
- With Home set to Stories, the cover swallows swipes too. Once the stories scroll away, the whole feed stays covered until you tap Home.
- The Explore grid cover and the YouTube swipe handling have not been checked on a device yet.

## Roadmap

More apps with endless short videos:

- **TikTok.** The whole app is a short video feed, so a daily limit or a full block.
- **Facebook.** The Reels and Watch feeds, with Messenger left alone.
- **Snapchat.** Spotlight and Discover, with chats and the camera left alone.
- **X.** The full-screen video player that scrolls on to the next video.
- **Reddit.** The full-screen video feed.
- **LinkedIn.** The vertical video feed.
- **Pinterest and Twitch.** Their short video and clips feeds.

Quality of life:

- A strict mode that makes you wait before you can turn Mori off or raise a limit.
- Schedules, such as reels only in the evening or no feeds during work hours.
- Separate limits for weekdays and weekends.
- Weekly stats of reels and shorts watched, and a home screen widget with today's count.
- A quick settings tile that pauses Mori for 15 minutes.
- A warning in Mori when an app update breaks a rule.
- A Shorts button detection that works in every language.
- Grayscale for guarded apps.
- Updates through F-Droid or Obtainium, and an update check in the app.

## How it works

Mori is one accessibility service. It reads which screen an app shows from the app's view IDs, then steers instead of throwing you out of the app.

On Instagram, Home opens Messages right away, through the Messages tab or the `instagram://direct-inbox` link. Going back from Messages onto Home leaves Instagram, the way going back from an app's first screen would. When Home is set to Stories or Feed, Home stays open instead. Stories covers the feed below the row of stories, which Mori finds by its story avatars because Instagram leaves the row itself out of the accessibility tree. A reel without a chat's reply bar under it then closes, unless the daily limit has reels left. The Home and Reels buttons get a dim cover that swallows taps. A tap that lands in the moment before a cover appears goes straight back to Messages. The Explore grid gets an opaque panel, and searching hides it. In the reel viewer, Mori follows the page numbers Instagram reports and scrolls back when you swipe past what is allowed.

On YouTube, the Shorts player reports no page numbers and accepts no scroll commands. Mori watches which short fills the middle of the screen, and when a different one shows up, it counts it or presses Back once.

The counts live in their own preferences file, because the service starts over whenever a setting changes.

The rules are plain Kotlin and run in unit tests:

- `InstagramView.kt` and `YouTubeView.kt` sort the visible views into screens. Each app's view IDs live in its `Instagram` or `YouTube` object.
- `Guard.kt` is the state machine that decides what to do about each screen, reel swipe and short.
- `GuardService.kt` reads the active app's view tree on a worker thread and carries out what `Guard` decides.
- `Overlays.kt` draws the covers and the counter.
- `SettingsScreen.kt` is the settings screen in Jetpack Compose with Material 3.

## Build from source

You need the Android SDK and JDK 17.

```bash
./gradlew testDebugUnitTest          # rule tests
scripts/install-debug.sh [serial]    # build, install and turn on the debug build over adb
./gradlew assembleRelease            # release APK
```

`install-debug.sh` works with a phone connected over USB with USB debugging on. Release builds are signed only when a `keystore.properties` file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword` sits in the project root. Without it, `assembleRelease` makes an unsigned APK.

The Compose BOM stays at 2026.06.01. Later releases need Android Gradle Plugin 9.1 and compile SDK 37.

### When an app update breaks a rule

1. Connect the phone (`adb devices`) and install the debug build.
2. Add a temporary `Log.d` in `GuardService` that prints the ID of each `ViewNode` from `Snapshot.read`, then read it with `adb logcat -s GuardService`. Do not use `uiautomator dump` while measuring, because it can pause other accessibility services.
3. Update the ID in `InstagramView.kt` or `YouTubeView.kt`, add a unit test with the new layout, and check the cases below.

Check these on a device after each change:

- Opening Instagram lands on Messages. Back from Messages leaves Instagram.
- Instagram's Home and Reels buttons do nothing when tapped, also right after leaving a chat.
- A reel sent in a chat plays. Swiping up scrolls back to it with "Just this reel".
- Search works and the Explore grid is covered. Stories and profiles open.
- YouTube's Shorts button is greyed out and does nothing.
- A short opened from YouTube Home plays. Swiping up closes the player with "Just this short".
- With **Open reels from chats** off, a reel sent in a chat closes with "Reels are off". With **Open shorts from Home** off, a short closes with "Shorts are off".
- With a scroll limit of 10, the Reels tab and the Shorts button open. Scrolling shows "1/10" up to "10/10", the next swipe stops with "10/10 · Done for today", and the buttons are covered again.
- Turning an app's switch off removes its covers at once.
- With Home set to Stories, Home opens with its stories uncovered and the feed below them covered. Scrolling the stories away covers the feed with "Tap Home to get back to the stories".
- With Home set to Feed, the feed scrolls. A reel tapped in it closes with "Only reels from chats", and a reel sent in a chat still plays.

## License

GPL 3.0, see [LICENSE](LICENSE). Mori is inspired by [Curbox](https://github.com/curbox-app/curbox-android) (GPL 3.0), whose Instagram reel detection and DM reel handling showed how this can work.

The settings screen shows the Instagram and YouTube icons from the apps installed on the phone, so this repository contains no third-party logos. The chat, play, block, hourglass and status icons are Material Symbols (Apache 2.0).
