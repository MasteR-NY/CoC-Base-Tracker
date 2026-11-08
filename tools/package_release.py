"""Package only public source files; never include local signing material."""
import collections
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
from update_release_notes import update_changelog

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs'
OUT.mkdir(exist_ok=True)
update_changelog()
config = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
VERSION = re.search(r'versionName\s*=\s*"([0-9.]+)"', config).group(1)
VERSION_CODE = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
APK = OUT / f'CoC-Base-Tracker-{VERSION}.apk'
apk_hash = hashlib.sha256(APK.read_bytes()).hexdigest()
scan = (OUT / f'Defender-{VERSION}.txt').read_text(encoding='utf-8-sig')
assert 'ScanExitCode: 0' in scan and f'APK-SHA256: {apk_hash}' in scan
for preview in ('app-design', 'build-menu-design', 'widget-design', 'whats-new-design', 'overview-design', 'crafting-design', 'helpers-design', 'collection-design', 'cosmetics-design', 'update-design', 'build-menu-filter-design', 'build-menu-army-design', 'build-menu-builder-design', 'build-menu-equipment-design', 'busy-builders-design', 'time-format-design'):
    assert (ROOT / f'docs/images/{preview}.png').read_bytes() == (OUT / f'previews/{preview}.png').read_bytes()
test_suites = [ET.parse(path).getroot() for path in sorted((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))]
assert len(test_suites) >= 4
tests = {key: sum(int(suite.attrib[key]) for suite in test_suites) for key in ('tests', 'failures', 'errors', 'skipped')}
assert tests['failures'] == 0 and tests['errors'] == 0
sarif = json.loads((ROOT / 'app/build/reports/lint-results-release.sarif').read_text(encoding='utf-8'))
lint = collections.Counter(r['ruleId'] for run in sarif['runs'] for r in run.get('results', []))
files = [ROOT / p for p in ['.gitignore', 'README.md', 'CHANGELOG.md', 'LICENSE', 'build.ps1', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'app/build.gradle.kts', 'tools/package_release.py', 'tools/update_release_notes.py']]
files += sorted((ROOT / 'app/src').rglob('*'))
files += sorted((ROOT / 'docs').rglob('*'))
files += [ROOT / 'tools' / name for name in ('fetch_planner_data.py', 'compile_planner_catalog.py', 'planner-sources.json', 'planner-corrections.json', 'build_identity_catalog.py', 'identity-overrides.json', 'check_cosmetic_ids.py', 'build_level_art.py', 'build_effect_icons.py', 'build_hired_icon.py')]
source = OUT / f'CoC-Base-Tracker-{VERSION}-source.zip'
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in files:
        if file.is_file():
            relative = file.relative_to(ROOT).as_posix()
            assert not any(part in ('.private', '.tools', '.gradle', 'apk-review', 'outputs', 'build') for part in file.relative_to(ROOT).parts)
            z.write(file, 'CoC-Base-Tracker/' + relative)
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert not any(name.endswith(('.p12', '.keystore', 'signing-password.txt', 'local.properties')) for name in z.namelist())
guide = f'''COC BASE TRACKER — ANDROID SETUP

1. Copy CoC-Base-Tracker-{VERSION}.apk to your Android phone and open it. Install as an update over earlier versions; do not uninstall first.
2. If Android asks, allow this one installation from your file manager.
3. Open Clash of Clans: Settings > More Settings > Export village data > Copy.
4. Return to CoC Base Tracker. Auto-sync on return imports a fresh export automatically; Sync / Add Account remains available for manual import.
5. Allow notifications if you want upgrade-finished reminders.
6. On Android 12+, tap Allow precise timers in the app for prompt alerts and finished-widget updates.
7. Tap Add widget and choose the village for that widget, or long-press your home screen > Widgets > CoC Base Tracker and choose a village when prompted.

READABLE TIMERS: App, Build Menu and live widgets show days, hours, minutes and seconds (for example 1d 12h 18m 56s). Live readable widget timers are enabled by default and use an ongoing foreground-service notification. Updates pause with the screen off or locked and recalculate on unlock. No wake lock is used. Pause live updates in the notification or turn the setting off to use native clock-style widget timers. Re-enable in Settings to resume. If Android prevents a background start, open the app. The service stops when no bound widget has a running upgrade or active displayed boost/event countdown. Finish reminders remain independent.

After starting a new upgrade, finishing early, using a potion, or changing a helper assignment, copy a fresh export and tap Sync on the widget.

PUBLISHING: Commit/push the changed source, README and docs/images. Create the version's GitHub release, attach the signed CoC-Base-Tracker-{VERSION}.apk, and mark it as latest. README's download button follows the latest release page without depending on a fixed APK filename. Users open Assets and choose the APK.

LEVEL ARTWORK: 1,376 level mappings across 142 items use 1,143 deduplicated model thumbnails and nine currency icons. Running app/widget upgrades and Build Menu cards preview the target level, with the current level retained on the badge. Unsupported level variants keep a generic portrait. All art is bundled offline; fresh exports refresh levels, and expired timers never promote saved levels. See docs/LEVEL-ART.md.

FINISH CLOCKS AND BOOSTS: Current upgrades show a local finish clock beside their countdown. Settings > Time Format selects Device Default, 12-Hour or 24-Hour for app, planner and widgets, with future dates labeled. Active Boosts groups icons, names, rates, time left and end clocks before the upgrade sections. Builder Potion priority and Study Soup additive speed remain unchanged. Next free in reports fully occupied regular queues, excluding Goblin jobs and keeping laboratories separate from builders. Widgets scroll boosts and upgrades together. The bundled Hammer Jam event shows its official November 16, 2026 08:00 UTC end, with no second discount. Fresh sync is required after changing upgrades or effects. See docs/TIMING.md.

WHAT'S NEW: A release summary appears after installing or updating. Tap Read Guide for highlights and instructions, or Got It to dismiss the summary for that version. The What's New button at the bottom reopens the offline guide with expandable earlier releases. CHANGELOG.md is generated from the same bundled notes; copy the matching version section into GitHub's release description when publishing.

UPDATE CHECKS: The header shows the installed version. Scroll to the bottom and tap Check for Updates. The app compares with GitHub's latest stable release, confirms when current, and offers Download Update when a newer APK is available. Android Downloads shows progress; tap Install Update after verification. If Android requests permission, allow installs from CoC Base Tracker, return and tap Install Update again. Android confirms installation. Update Download and the ready notification reopen a retained download. If your local build is newer than the published release, it says so. Failures are reported with a manual Releases option. No automatic release checks run on launch or on a background schedule. A requested check or approved download may finish after leaving the screen. Downloads start only after your confirmation; installation always requires Android confirmation.

UPDATE RELIABILITY: If the API fails, a second anonymous HTTPS GET checks the public latest-release redirect, accepting only a stable numeric tag in this exact repository. If both app connections fail, Android Downloads first fetches the public release HTML (2 MiB limit, 20 seconds per attempt), accepting only a same-repository stable tag from canonical/og:url metadata. It retries a transient 500/502/503/504 once, never automatically retries 403/429, and tries the API only if the page fails. Error dialogs start with a short explanation and collapsed technical details. An EPERM socket restriction still needs phone-level validation. A redirect-only result tries the repository's versioned filename convention, with package/signature/version checks required before installation. Both paths retain normal certificate and hostname validation; errors distinguish DNS, TLS, timeouts and server status with Retry and Open Releases buttons. Android 8.0+ remains supported; Android 8 and 10 framework checks are included, but physical-phone networking still needs validation.

AUTO-SYNC: Enabled by default in Settings and available before your first account import. Copy the village export in Clash, then switch back through Recent Apps, the launcher or the live-timer notification. A focused tracker imports a fresh export without an extra Sync tap. Duplicate/older exports are ignored, manual widget account guards remain enforced, and each widget keeps its selected account. Unrelated clipboard text is ignored and URIs are never opened. Disable Auto-sync on return for manual imports. Android 10+ does not let this tracker detect Clash's Copy button from the background or automatically bring itself forward. No background clipboard monitoring service is added.

EXPORT FIELD REFERENCE: docs/EXPORT-DATA.md contains a non-identifying review of the supplied snapshot, supported fields, missing information, the potion calculation example and potential features. The original personal export is excluded from this source ZIP. Helper repeat flags support helper_recurrent and recurrent_helper; idle preferences do not fabricate active jobs, and helper cooldowns are kept separate.

VILLAGE OVERVIEW: Tap Overview beside Build Menu. Progress compares building, wall, trap, army and hero level/count data for each base with Current Hall or Catalog Max targets. Percentages are level steps, not costs or time. Crafting includes every exported module, with unknown caps/names clearly marked. Collection includes equipment levels and cosmetic quantities; it does not invent unowned collectibles or ore balances. Helpers shows exported availability estimates, with Clock Tower under Builder Base. Enable Helper & Clock Tower alerts and Android notifications for a one-time reminder per exported deadline. Fresh sync replaces deadlines; no recurring uses are invented. News opens bundled official references and optional browser links. See docs/OVERVIEW.md and docs/GAME-DATA.md.

NAMES AND ARTWORK: The offline overlay has 882 identities and 873 artwork thumbnails. The nine current crafting modules have names, caps, defense portraits and stat symbols. Alchemist is helper 93000002 and Prospector is 93000003. The sample's helpers, nine modules and 93 of 95 cosmetic IDs are resolved. Official game tables identify 18000244 as Amber Hot Streak and 18000248 as Sour Elixir Fountain. Skins 52000268/52000337 still have blank display-name and artwork fields. Shared-art variants retain their data ID in the cosmetic details dialog. No artwork or identity requests run in the installed app. See docs/ITEM-IDENTITIES.md for pinned sources, credits, reproduction and limits.

SMOOTHER TIMERS AND HOME: Boosted Work left updates through intermediate work seconds in the app and Build Menu, recalculated from fractional real seconds with a 16 ms minimum display interval. Widgets show steady real time remaining and a boost badge with the existing one-second service refresh. Settings starts collapsed; expand it for account and notification controls. Help & About contains the longer sync, timer, widget, privacy and credit information.

Scroll vertically inside a widget to see all upgrades. Tap an upgrade to open Clash. The widget does not switch your actual in-game Supercell account.

MULTIPLE ACCOUNTS: Copy and import the village export for each account. Each one is saved separately. Add two widgets and choose a different village for each. Tap the downward arrow beside Sync on an existing widget to change its village. Switching villages in the app does not change other widgets. A widget's Sync rejects clipboard data from the wrong account; use Sync / Add Account in the app to save another account.

IN-GAME NAMES: Names appear in account lists, app/Build Menu headers, widget selection/headers and finish reminders, alongside player tags. Exports may omit the name; enter it once after first import or use Set In-game Name / Settings > In-game Name. A plain-text name supplied by an export is used when present. Names survive sync and are stored separately per tag. Manual names take priority. Old village labels remain optional Account Nicknames (Main/Mini). Nothing is looked up online or renamed in your actual game account.

KOTLIN: Most production classes now use Kotlin, with MainActivity, PlannerActivity and OverviewActivity retained in Java. Tracking calculations, export validation, planner, storage, widgets, services, reminders and update handling were migrated. Package, signing identity, file format, preference keys and widget bindings remain compatible. See docs/KOTLIN-MIGRATION.md. Sample previews use Anonymous, and hired upgrades use the official Goblin Builder head icon instead of the GOBLIN text badge.

CLASH-STYLE DESIGN: Gray headers, brown upgrade cards and bottom category tabs adapt Supercell's October 2026 Build Menu. Timers retain the reference repository's wooden panel, Lilita One font, gold controls, item art and green/gold countdown rows. Bars show progress since export. README includes actual framework-rendered app, planner and widget previews, refreshed by normal builds.

COMPACT LAYOUT AND TH12: Smaller portraits and text, shorter controls and single-layer card padding fit more upgrades on screen while retaining inner margins and Android font scaling. Monolith Arrow is equipment 90000016 (Archer Queen), and Electro Fangs is 90000059 (Dragon Duke). A level-17 Monolith Arrow offers level 18; Stone Slammer levels 1-3 have TH12 gates. The reviewed, reproducible corrections are in tools/planner-corrections.json and docs/CATALOG-CORRECTIONS.md.

BUILD MENU: Current Hall Only is enabled by default: current upgrades stay first, reachable remaining upgrades are capped to the exported hall, and the next hall upgrade is shown separately. Facility upgrades possible at this hall remain included. Tap Build Menu above the timers. Switch Village Base / Builder Base. Village has Buildings & Heroes, Army Upgrades, Hero Equipment and Hero Pets; Builder Base has the first two. Cards show remaining standard upgrades, grouped levels/counts, baseline per-item costs/times and prerequisites. Tap for details. Use the sliders button for resource/type/hero button filters and Cost Max/Min sorting. Costs and material icons appear on the right. Turn off Current Hall Only for future next steps; More Options contains status and Suggested Order. Cancel discards staged changes; Apply commits them per base/category, and empty matches do not clear choices. Different active timers remain separate.

PLANNER LIMITS: The pinned community catalog contains 223 items, checked 8 Nov 2026, through TH18 / BH10. Planning costs/times are baseline values, without future event, Gold Pass, potion or helper reductions. Running timers still use exported boost-adjusted countdowns. Requirements met does not promise resources or a free builder. Supercharges, some crafted modules, merges and special hall/unlock conditions require confirmation in game. Sync to confirm ended levels. See docs/UPGRADE-PLANNER.md.

UPDATING FROM 1.0: Your original village, display name, and reminder cursor migrate automatically. Existing widgets retain that village and gain scrolling. Install over the old version to preserve app data. Names and reminders are now independent per account.

POTIONS AND HELPERS: Boosted upgrades show effective speed, accelerated Work left and estimated real Finishes in time. Boosts & Helpers lists imported effects and their next speed change. Research Potion runs Laboratory work at 24x for its remaining duration, Pet Potion applies to pets, Builder Potion to Home builders, and Clock Tower to Builder Base/Star Laboratory. Study Soup supports lab/pets; Builder Potion takes priority over Builder Bite while both run. Stacked doses extend duration. Assigned helper work adds separately. Explicit recurring assignments with a known exported cooldown are forecast on one-hour sessions every 23 hours. Missing future assignments are not invented. Books, Hammers and gem finishes require a fresh export; unused inventory and combat/resource effects do not accelerate these queues.

EVENTS: Event/Gold Pass reductions already present in the exported work are retained without another blanket discount. The Hammer Jam note is informational only. Unsupported future effects require an updated rule. See docs/TIMING.md.

Explore a sample village is a clearly marked preview. It never schedules real reminders or saves sample data as your village.

PRIVACY: Tracking/planning work offline. Internet permission is used for manual HTTPS update checks to this repository's GitHub API and public latest-release redirect, with Android Downloads as a metadata fallback. Explicitly approved APK downloads use this repository's HTTPS release assets and their download redirects. REQUEST_INSTALL_PACKAGES allows the user to open Android's installer for a verified update; it does not permit silent installation. Requests include the app version, not village exports, account names, player tags or credentials; GitHub sees normal connection information such as IP address. Browser navigation opens the public Releases page. Clipboard reads occur for manual Sync or while the tracker is focused with Auto-sync on return enabled; unrelated text is ignored and there is no background clipboard monitoring. No screen capture, accessibility access, analytics or ads. Village data stays on this phone with backup/transfer exclusions.

LIMITS: New upgrades cannot be discovered automatically with the currently available official data access. Android battery restrictions can delay inexact alarms. After force-stopping the app, reopen it to restore reminders. The export format is unofficial; compare the first imported timers with the game. Android framework tests verify widget list inflation and adapter contents, migration, and account isolation. This build has not been run on a physical phone or emulator here.

Keep the local .private folder secret and backed up for future updates. It is not included in the source ZIP. Do not replace it with the public key from the repository reviewed earlier.
'''
(OUT / 'Setup.txt').write_text(guide, encoding='utf-8')
report = {
    'app': 'CoC Base Tracker', 'version': VERSION, 'version_code': VERSION_CODE, 'package': 'com.villageclock.app',
    'review_date': '2026-11-08', 'timezone': 'Asia/Manila',
    'apk_file': APK.name, 'apk_bytes': APK.stat().st_size, 'apk_sha256': apk_hash,
    'source_file': source.name, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'tests': tests,
    'test_suites': [{'name': suite.attrib['name'], 'tests': int(suite.attrib['tests'])} for suite in test_suites],
    'lint_task': 'passed (no errors)', 'lint_findings': dict(lint),
    'signature': {'verified_by': 'Android SDK apksigner', 'v2': True, 'v3': True,
                  'certificate_sha256': '607696bd8e6ae0130aa00429915fab8de31ce5383fcf8886a4306a1db8767a71',
                  'signing_key': 'Locally generated private RSA-3072 key; not included in APK or source ZIP'},
    'antivirus': {'product': 'Microsoft Defender', 'result': 'found no threats', 'scan_exit_code': 0},
    'permissions': [e.attrib['{http://schemas.android.com/apk/res/android}name'] for e in ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().findall('uses-permission')],
    'internet_permission': True, 'network_scope': 'User-initiated HTTPS GitHub API and fixed public Releases redirect checks, Android Download Manager metadata fallback and explicitly approved APK asset downloads with system-managed redirects; normal TLS; no village/account data sent; user-confirmed Android installation',
    'backup': 'Disabled, with cloud and device transfer exclusions',
    'android_device_test': 'Not run: no attached device or configured emulator available',
    'android_framework_tests': 'Robolectric API 26, 28, 29 and 35: Android 8/10/modern update UI and compact-home checks; widget/account isolation, fractional boost work and focus pause, foreground service, update prompts, safe asset URLs, fallback selection, download ownership, checksum/package/version/signing-key checks, source settings and installer URI grants, offline release guide and account preservation',
    'live_game_validation': 'Supplied export parsed and modeled offline; not compared with a live game session',
    'sync': 'Explicit village export import; readable live countdowns afterward (foreground service for widgets)',
    'readable_widget_service': 'User-selected specialUse foreground service; ongoing notification with pause, screen-off/lock suspension, no wake lock',
    'features': ['installed version in app header', 'manual GitHub update checks with numeric comparison, system-service fallback and direct APK downloads', 'accelerated Work left and real Finishes in countdowns with imported boost sources', 'finite potion/snack windows and builder overlap priority', 'known recurring helper forecasts on a 23-hour work day', 'per-account in-game names with optional nicknames and visible tags', 'explicit days/hours/minutes/seconds countdowns', 'foreground live widget service with notification pause', 'README latest-release download button and title-case headings', 'offline Build Menu with Village/Builder Base switching', 'remaining standard upgrades and catalog gates', 'resource/type/hero/status filters', 'cost/time/name sorting', 'scrollable list widget', 'multiple saved accounts', 'per-widget account selection', 'per-account names/reminder cursors', '1.0 data migration', 'duration stacks with independent expiry', 'event discount non-duplication'],
    'planner_catalog': {'items': 223, 'revision': '7afaa0375e5865c04021836a3a4192ffcfea092d', 'coverage': 'standard levels through TH18/BH10; special rules require in-game confirmation'},
    'design': 'October 2026 Build Menu structure and gray/brown cards; Lilita One, game icons, grouped green/gold countdown rows; README previews refreshed from Android layouts',
}
report['features'][:0] = ['public GitHub release redirect fallback with specific connection diagnostics', 'fractional-time accelerated work countdowns in app and planner', 'steady real-time widget countdowns with boost badges', 'collapsed settings and Help & About', 'offline release guide and per-version launch summary', 'expandable earlier release notes', 'shared bundled notes and generated changelog']
report['features'][:0] = ['foreground clipboard auto-sync on return with duplicate/stale suppression', 'first-import settings and auto-sync opt-out', 'per-helper cooldowns and helper_recurrent compatibility', 'export field reference and grounded feature recommendations']
report['clipboard'] = 'User-selected foreground auto-sync (default enabled), or manual Sync; resumed focused activity only; plain-text export validation; no background reads, URI coercion, unrelated-text persistence or uploads'
report['features'][:0] = ['per-base Current Hall and Catalog Max level progress', 'nested crafting module inventory', 'per-account helper and Clock Tower availability alerts', 'equipment and cosmetic collection views', 'bundled dated official game references with browser links']
report['availability_alerts'] = 'Exported deadlines only; one-time per account cursor; shared alarm receiver, separate notification ID 8; missing cooldowns unknown; fresh import resets cursor; settings and notification permissions respected'
report['official_references'] = 'Reviewed 2026-11-08; Supercell blog, crafted defense and helper support, official channel directory; bundled notes, no background feed or automatic rule downloads'
report['identity_catalog'] = {'identities': 882, 'artwork_files': 873, 'sample_cosmetic_names_and_art': '93 of 95 distinct IDs', 'unresolved_sample_ids': [52000268, 52000337], 'runtime_downloads': False, 'provenance': 'docs/ITEM-IDENTITIES.md'}
report['features'][:0] = ['offline crafting, helper and cosmetic identity catalog', 'verified module caps and semantic stat symbols', 'corrected Alchemist/Prospector IDs', 'bundled cosmetic portraits and thumbnails with shared-variant details']
report['android_framework_tests'] += '; Overview tab/base navigation, sample safety, availability one-shot delivery, replacement deadlines, Clock Tower alarm scheduling and account removal on API 26/29/35'
report['finish_clocks'] = {'modes': ['device default', '12-hour', '24-hour'], 'timezone': 'device-local with destination DST', 'boosts': 'one shared panel, including idle active effects and bundled verified event', 'availability': 'regular fully occupied queues; excludes Goblin and separates labs', 'runtime_event_feed': False}
report['features'][:0] = ['local finish clocks with global time-format settings', 'shared active potion/snack/event panel with expiry clocks', 'regular-slot Next free countdown and local availability time', 'scrollable boost and upgrade widget collection']
report['android_framework_tests'] += '; 12/24/device clocks, DST and future days, idle/stacked/deduplicated effects, regular-slot/Goblin separation, widget expiry and stable rows, boost-only service lifetime'
report['level_artwork'] = {'mapped_items': 142, 'mapped_levels': 1376, 'model_files': 1143, 'currency_files': 9, 'runtime_downloads': False, 'sources': 'docs/level-art-sources.json'}
report['features'][:0] = ['current-hall Build Menu default with next hall upgrade', 'destination-level artwork in planner and app/widget timers', 'resource/type/hero button filters and Cost Max/Min', 'side-aligned resource and multi-ore costs', 'staged filters with empty-match retention']
report['android_framework_tests'] += '; current-hall caps and reachable facilities, hero gates, unknown timers, filter Apply/Cancel and isolation, recreation, exact-level artwork and currency rendering'
report['app_updates'] = {'metadata_fallback': 'Android Download Manager public latest-release HTML before API, bounded responses, strict same-repository canonical/og:url parsing, one transient-server retry and no automatic 403/429 retry', 'apk_validation': 'repository URL, size, optional GitHub SHA-256, package, newer version code/name and matching installed signing key', 'install': 'explicit button, source permission and Android confirmation', 'device_socket_exception': 'Not reproduced on a physical phone; diagnostics and alternative system network path added'}
report['features'][:0] = ['compact timer, planner and Overview layouts with normal font scaling', 'correct Monolith Arrow and Electro Fangs identity and artwork mappings', 'TH12 Stone Slammer levels 1-3', 'public release HTML system-service fallback with compact expandable error diagnostics']
report['planner_catalog']['reviewed_corrections'] = 'tools/planner-corrections.json; docs/CATALOG-CORRECTIONS.md'
report['android_framework_tests'] += '; TH12 Monolith Arrow level 17 and Stone Slammer visibility, corrected equipment hero gates, strict release-page metadata, 403 no-retry and one transient 504 retry'
kotlin_bytes = sum(p.stat().st_size for p in (ROOT/'app/src/main/java').rglob('*.kt'))
java_bytes = sum(p.stat().st_size for p in (ROOT/'app/src/main/java').rglob('*.java'))
assert kotlin_bytes > java_bytes, 'Production implementation must remain mostly Kotlin'
report['language_migration'] = {'production_kotlin_bytes': kotlin_bytes, 'production_java_bytes': java_bytes, 'production_kotlin_files': len(list((ROOT/'app/src/main/java').rglob('*.kt'))), 'production_java_files': len(list((ROOT/'app/src/main/java').rglob('*.java'))), 'java_screens': ['MainActivity', 'PlannerActivity', 'OverviewActivity'], 'data_format': 'unchanged', 'build': 'AGP built-in Kotlin, JVM 17'}
report['features'][:0] = ['Kotlin tracking, storage, planner, widgets, service and updater', 'Anonymous sample name', 'official Goblin Builder head markers for hired rows and queue headings', 'expanded README permissions, setup, battery, troubleshooting and build guidance']
(OUT / 'Verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
(OUT / 'SHA256SUMS.txt').write_text(apk_hash + '  ' + APK.name + '\n' + report['source_sha256'] + '  ' + source.name + '\n', encoding='ascii')
print(json.dumps({'apk': str(APK), 'apk_bytes': APK.stat().st_size, 'apk_sha256': apk_hash,
                  'source': str(source), 'source_bytes': source.stat().st_size,
                  'tests': report['tests'], 'source_archive_integrity': 'passed', 'signing_material_excluded': True}, indent=2))
