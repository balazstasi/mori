# Changelog

## 0.3.0 (2026-11-18)

### Changed

- The app ID is now `io.github.balazstasi.mori`. The old ID, `app.mori`, looked like the domain `mori.app`, which belongs to someone else, and F-Droid rejects app IDs that match another owner's domain. Android treats the new ID as a different app, so uninstall Mori 0.2.1 or older before installing this one, then turn Mori on in Accessibility again. Settings start over.

## 0.2.1 (2026-11-18)

### Changed

- Release builds are reproducible. The APK no longer records the git state of the checkout, so a build from any clone of a tag matches the signed release, and F-Droid can ship the same signed APK as GitHub.
- The repository has a store listing in `fastlane/metadata` for F-Droid and IzzyOnDroid.

## 0.2.0 (2026-11-18)

### Added

- Instagram **Home** setting with three choices: **Off** sends Home to Messages as before, **Stories** opens Home with only its row of stories, and **Feed** opens all of Home.
- While Home is open, only reels from chats play. A reel tapped in the feed closes with "Only reels from chats", unless the daily scroll limit has reels left.

### Fixed

- The cover over Instagram's Home feed starts below the top bar instead of at the top of the screen. Mori could not see the old top bar views on Instagram 455.

### Changed

- `scripts/install-debug.sh` installs the debug build for the current user only, so it no longer lands in work or dual app profiles.

## 0.1.0 (2026-11-15)

The first release.

- Instagram opens straight to Messages. The home feed, the Reels tab and the Explore grid are covered, and you cannot scroll on from a reel someone sends you.
- YouTube's Shorts button is covered, and you cannot scroll on from a short you open.
- Single reels and shorts can be switched off, so they close as they open.
- A daily scroll limit per app, from 10 to 300.
