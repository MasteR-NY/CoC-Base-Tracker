# iPhone and iPad app — source preview 0.1

The `apple/` directory adds a native SwiftUI app and a WidgetKit extension alongside the Android app. The deployment target is iOS / iPadOS 17.0. Both iPhone and iPad are enabled, with a compact phone layout and an account sidebar on larger displays. This is an initial source implementation, with no signed IPA, TestFlight build or App Store release yet.

## Included in this version

- Paste village JSON with the system Paste button, edit it before import, or choose a UTF-8 JSON/text file (1 MB limit).
- Save multiple accounts locally, choose an account, add a nickname, and remove an account with confirmation.
- Reject duplicate/older snapshots without replacing data or switching the selected account.
- Track builders, Laboratory, Pet House, Builder Base and Star Laboratory, including nested crafting timers and hired-worker flags.
- Estimate finishes from the export timestamp with finite potion windows, builder-effect priority, additive Study Soup and explicitly assigned helpers. No second event discount is applied to exported work.
- Show active boosts, estimated finish dates and work remaining. Timer expiry does not promote saved item levels.
- Optionally schedule the next 60 upcoming finish reminders across all accounts, replenished on app activation and account changes. Expired timers are not scheduled as new alerts.
- Add small, medium and large account-configured Home Screen widgets, plus extra-large on iPad. Each widget keeps its own account selection and opens that account when tapped. A removed account never silently becomes a different village.

Apple's system countdown text updates the displayed interval. Widget content/order is scheduled at upgrade/boost deadlines, with an hourly refresh request. WidgetKit decides actual refresh times. Widgets show a size-limited selection of upgrades; open the app for the full list. See [dynamic dates](https://developer.apple.com/documentation/widgetkit/displaying-dynamic-dates), [widget updates](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date), and [configurable widgets](https://developer.apple.com/documentation/widgetkit/making-a-configurable-widget).

The first Apple version does not include Android's Build Menu/planner, village progress and collection screens, helper-availability alerts, live foreground-service controls, automatic clipboard-on-return import, or GitHub APK updater. Accounts do not sync between Android and Apple devices. Use a fresh in-game export to import an account on each device. Only catalog names are reused as resources; the Apple UI uses system symbols and an original app icon.

## Open and run on a Mac

1. Copy/clone the complete repository to a Mac with Xcode 16 or later. Its installed iOS SDK must support the intended simulator/device. See [Xcode requirements](https://developer.apple.com/xcode/system-requirements/).
2. Open `apple/CoCBaseTracker.xcodeproj`. The local `TrackerCore` Swift package resolves from `apple/Package.swift`; no third-party Swift dependencies or project-generator installation are required.
3. Select the **CoCBaseTracker** scheme and an iPhone or iPad simulator. Build and run. If signing prevents a simulator build, the unsigned build command below can be used to check compilation; shared-container behavior still needs runtime verification.
4. For a physical device, edit `apple/Config/Signing.xcconfig`: choose a unique `TRACKER_BUNDLE_ID`, a matching `TRACKER_APP_GROUP`, and your `DEVELOPMENT_TEAM`. The widget bundle ID is derived by appending `.widgets`.
5. In Xcode **Signing & Capabilities**, verify that both **CoCBaseTracker** and **VillageWidget** use your team and the same registered App Group. Both inherit `Config/Tracker.entitlements`. A provisioning profile must support that group; device widget testing may require an Apple Developer Program team with the appropriate capability. See [App Groups setup](https://developer.apple.com/documentation/xcode/configuring-app-groups).
6. Run on the device, import a fresh village export, then add CoC Base Tracker through the Home Screen widget gallery. Touch and hold each widget → **Edit Widget → Account**. Import before configuring the widget so accounts appear in the picker.

The build deliberately reports unavailable shared storage rather than saving accounts to a separate fallback that widgets cannot read. If you see this message, check the team, registered group, both entitlements, and the `TrackerAppGroup` Info.plist value. Unlock the device once after restart before expecting protected storage to be readable by the widget.

`Signing.xcconfig` contains public identifiers, never private keys or certificates. Keep signing identities/profiles outside source control. The shared archive is `accounts-v1.json` inside the App Group container, written atomically with protection until first authentication. The app is its only writer; the widget reads it. An unreadable, corrupt or future-version archive produces an error and is not overwritten by a fresh import.

## Verify on a Mac

From the repository root:

```sh
python3 tools/verify_apple.py
cd apple
swift test
cd ..
xcodebuild -project apple/CoCBaseTracker.xcodeproj \
  -scheme CoCBaseTracker -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath apple/.derivedData CODE_SIGNING_ALLOWED=NO build
```

The Swift tests cover finite boosts and rounding, overlap priority, helper cycles, an independent second-by-second timing simulation, parser rejection cases, export timestamp anchoring, hired-worker slot exclusion, stale imports, nickname preservation, widget account isolation, atomic persistence and corrupt-archive handling. A resource contains **31 synthetic export fixtures** with expected finish seconds captured from the existing Android parser. No personal village export is used or bundled.

`.github/workflows/apple.yml` runs the preflight, `swift test`, and an unsigned universal simulator build on a macOS runner when the relevant source changes are pushed or a PR is opened. It can also be dispatched manually. Adding the workflow does not run it locally, publish the app, or sign a release.

Before distribution, run the app on an iPhone and an iPad, including landscape, split view and large text. Import two different exports; rename and remove accounts; confirm widgets remain bound to their chosen accounts, deep links open the correct village, countdowns stop at zero, and expired jobs do not change levels. Test denied/allowed notifications, reimported deadlines, removal/disable cancellation, restart/unlock, and a real boosted export against the game. Check widget text fit for each supported size. Simulator compilation does not replace these checks.

## Verification recorded in this Windows workspace

- Project/catalog preflight passed: both targets, source/resource membership, widget embedding, shared group configuration, manifests, icon and public source list.
- All 31 synthetic parity fixture expectations were replayed against the existing compiled Android parser.
- Swift compilation, the Swift XCTest suite, Xcode project loading, simulator rendering, provisioning and physical Apple device checks have **not** run here. This machine has no Swift/Xcode toolchain. The Apple implementation remains unverified until those checks pass on a Mac.
- Android production sources and version are unchanged. This addition does not create a new Android APK release.

## Maintain the Apple sources

After changing Apple source membership or Android catalog names, regenerate the checked-in project and name resource:

```sh
python3 tools/prepare_apple.py
python3 tools/verify_apple.py
```

`prepare_apple.py --check` is read-only. The generated `project.pbxproj` is an XML property list. Source files can be edited normally; add/remove files through the generator rather than hand-editing the generated project. Keep team identifiers in `Signing.xcconfig`. Generated catalog names follow Android's base → planner → identity override precedence, with source licenses in the app's **Settings & help → Data credits & licenses**. `tools/build_apple_icon.py` optionally redraws the committed original icon using Pillow; normal builds do not need Pillow.

To replay the Android fixture oracle after compiling the Android app, run with JDK 21+ and a classpath containing Android's compiled Kotlin classes, Kotlin stdlib, and the JVM `org.json` test dependency:

```sh
java -cp "$ANDROID_TEST_CLASSPATH" tools/AppleTimingParity.java \
  apple/Tests/TrackerCoreTests/Fixtures/android-parity.json
```

Appending `--refresh` intentionally replaces fixture expectations with current Android results. Review timing-rule changes in both implementations before refreshing them. These fixtures verify cross-platform expectations when `swift test` is run; replaying only the Java oracle does not execute Swift.

The public release-source packager includes the Apple project, sources, fixtures, offline names, icon and Mac CI definition, while excluding Xcode state, SwiftPM caches, build output, personal exports and signing material. It still requires the existing Android release artifacts/checks before producing a combined source ZIP.

## Privacy and distribution

Imports are explicit; there is no background clipboard polling or automatic upload. No server, login, Supercell session or Clash API token is required. The privacy manifest declares local app-preference access, and no tracking or collected data. Local device storage follows the user's device/OS backup settings; deleting the app may delete its accounts. Review these declarations and the existing fan-content/data attribution before a release. See [Apple privacy manifest guidance](https://developer.apple.com/documentation/technotes/tn3183-adding-required-reason-api-entries-to-your-privacy-manifest).

App Store or TestFlight distribution requires a separately signed/archive-validated Apple build and the corresponding Apple account setup. Android's APK cannot be installed on iPhone/iPad, and its in-app APK updater does not apply to this target. No publishing action has been performed.
