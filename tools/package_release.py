"""Package only public source files; never include local signing material."""
import collections
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs'
OUT.mkdir(exist_ok=True)
config = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
VERSION = re.search(r'versionName\s*=\s*"([0-9.]+)"', config).group(1)
VERSION_CODE = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
APK = OUT / f'CoC-Base-Tracker-{VERSION}.apk'
apk_hash = hashlib.sha256(APK.read_bytes()).hexdigest()
scan = (OUT / f'Defender-{VERSION}.txt').read_text(encoding='utf-8-sig')
assert 'ScanExitCode: 0' in scan and f'APK-SHA256: {apk_hash}' in scan
for preview in ('app-design', 'build-menu-design', 'widget-design'):
    assert (ROOT / f'docs/images/{preview}.png').read_bytes() == (OUT / f'previews/{preview}.png').read_bytes()
test_suites = [ET.parse(path).getroot() for path in sorted((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))]
assert len(test_suites) >= 4
tests = {key: sum(int(suite.attrib[key]) for suite in test_suites) for key in ('tests', 'failures', 'errors', 'skipped')}
assert tests['failures'] == 0 and tests['errors'] == 0
sarif = json.loads((ROOT / 'app/build/reports/lint-results-release.sarif').read_text(encoding='utf-8'))
lint = collections.Counter(r['ruleId'] for run in sarif['runs'] for r in run.get('results', []))
files = [ROOT / p for p in ['.gitignore', 'README.md', 'LICENSE', 'build.ps1', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'app/build.gradle.kts', 'tools/package_release.py']]
files += sorted((ROOT / 'app/src').rglob('*'))
files += sorted((ROOT / 'docs').rglob('*'))
files += [ROOT / 'tools' / name for name in ('fetch_planner_data.py', 'compile_planner_catalog.py', 'planner-sources.json')]
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

1. Copy CoC-Base-Tracker-{VERSION}.apk to your Android phone and open it. Install as an update over versions 1.0–1.6; do not uninstall first.
2. If Android asks, allow this one installation from your file manager.
3. Open Clash of Clans: Settings > More Settings > Export village data > Copy.
4. Open CoC Base Tracker and tap Sync from clipboard / add account.
5. Allow notifications if you want upgrade-finished reminders.
6. On Android 12+, tap Allow precise timers in the app for prompt alerts and finished-widget updates.
7. Tap Add widget and choose the village for that widget, or long-press your home screen > Widgets > CoC Base Tracker and choose a village when prompted.

READABLE TIMERS: App, Build Menu and live widgets show days, hours, minutes and seconds (for example 1d 12h 18m 56s). Live readable widget timers are enabled by default and use an ongoing foreground-service notification. Updates pause with the screen off or locked and recalculate on unlock. No wake lock is used. Pause live updates in the notification or turn the setting off to use native clock-style widget timers. Re-enable in Settings to resume. If Android prevents a background start, open the app. The service stops when no widget has a running upgrade. Finish reminders remain independent.

After starting a new upgrade, finishing early, using a potion, or changing a helper assignment, copy a fresh export and tap Sync on the widget.

PUBLISHING: Commit/push the changed source, README and docs/images. Create the version's GitHub release, attach the signed CoC-Base-Tracker-{VERSION}.apk, and mark it as latest. README's download button follows the latest release page without depending on a fixed APK filename. Users open Assets and choose the APK.

Scroll vertically inside a widget to see all upgrades. Tap an upgrade to open Clash. The widget does not switch your actual in-game Supercell account.

MULTIPLE ACCOUNTS: Copy and import the village export for each account. Each one is saved separately. Add two widgets and choose a different village for each. Tap the downward arrow beside Sync on an existing widget to change its village. Switching villages in the app does not change other widgets. A widget's Sync rejects clipboard data from the wrong account; use Sync from clipboard / add account in the app to save another account.

IN-GAME NAMES: Names appear in account lists, app/Build Menu headers, widget selection/headers and finish reminders, alongside player tags. Exports may omit the name; enter it once after first import or use Set In-game Name / Settings > In-game Name. A plain-text name supplied by an export is used when present. Names survive sync and are stored separately per tag. Manual names take priority. Old village labels remain optional Account Nicknames (Main/Mini). Nothing is looked up online or renamed in your actual game account.

CLASH-STYLE DESIGN: Gray headers, brown upgrade cards and bottom category tabs adapt Supercell's October 2026 Build Menu. Timers retain the reference repository's wooden panel, Lilita One font, gold controls, item art and green/gold countdown rows. Bars show progress since export. README includes actual framework-rendered app, planner and widget previews, refreshed by normal builds.

BUILD MENU: Tap Build Menu above the timers. Switch Village Base / Builder Base. Village has Buildings & Heroes, Army Upgrades, Hero Equipment and Hero Pets; Builder Base has the first two. Cards show remaining standard upgrades, grouped levels/counts, baseline per-item costs/times and prerequisites. Tap for details. Use the filter button for resource/type/hero filters and Cost Min/Max sorting. Extra status/time/name options are available. Different active timers remain separate.

PLANNER LIMITS: The pinned community catalog contains 223 items, checked 8 Nov 2026, through TH18 / BH10. Planning costs/times are baseline values, without future event, Gold Pass, potion or helper reductions. Running timers still use exported boost-adjusted countdowns. Requirements met does not promise resources or a free builder. Supercharges, some crafted modules, merges and special hall/unlock conditions require confirmation in game. Sync to confirm ended levels. See docs/UPGRADE-PLANNER.md.

UPDATING FROM 1.0: Your original village, display name, and reminder cursor migrate automatically. Existing widgets retain that village and gain scrolling. Install over the old version to preserve app data. Names and reminders are now independent per account.

POTIONS AND EVENTS: Stacked doses extend the exported boost duration; they do not multiply the boost speed. Different boosts and the currently assigned helper contribute independently until their respective expirations. Event/Gold Pass reductions already present in the exported upgrade timer are preserved without a second blanket discount. A known Hammer Jam note is informational only. Unobserved future helper assignments or new event speed multipliers still require a fresh export or updated rule. See docs/TIMING.md in the source ZIP.

Explore a sample village is a clearly marked preview. It never schedules real reminders or saves sample data as your village.

PRIVACY: No Supercell login or codes are needed. There is no internet permission, screen capture, accessibility access, or automatic clipboard monitoring. Village data is stored on this phone with backup/transfer exclusions.

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
    'internet_permission': False, 'backup': 'Disabled, with cloud and device transfer exclusions',
    'android_device_test': 'Not run: no attached device or configured emulator available',
    'android_framework_tests': 'Robolectric API 28 and API 35: widget inflation/adapters, migration, account/name isolation and persistence, widget name/tag headers, startup, base/category navigation, readable rows, countdown focus lifecycle, foreground notification/pause and bound-account snapshots',
    'live_game_validation': 'Not run: no private user village export supplied',
    'sync': 'Explicit village export import; readable live countdowns afterward (foreground service for widgets)',
    'readable_widget_service': 'User-selected specialUse foreground service; ongoing notification with pause, screen-off/lock suspension, no wake lock',
    'features': ['per-account in-game names with optional nicknames and visible tags', 'explicit days/hours/minutes/seconds countdowns', 'foreground live widget service with notification pause', 'README latest-release download button and title-case headings', 'offline Build Menu with Village/Builder Base switching', 'remaining standard upgrades and catalog gates', 'resource/type/hero/status filters', 'cost/time/name sorting', 'scrollable list widget', 'multiple saved accounts', 'per-widget account selection', 'per-account names/reminder cursors', '1.0 data migration', 'duration stacks with independent expiry', 'event discount non-duplication'],
    'planner_catalog': {'items': 223, 'revision': '7afaa0375e5865c04021836a3a4192ffcfea092d', 'coverage': 'standard levels through TH18/BH10; special rules require in-game confirmation'},
    'design': 'October 2026 Build Menu structure and gray/brown cards; Lilita One, game icons, grouped green/gold countdown rows; README previews refreshed from Android layouts',
}
(OUT / 'Verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
(OUT / 'SHA256SUMS.txt').write_text(apk_hash + '  ' + APK.name + '\n' + report['source_sha256'] + '  ' + source.name + '\n', encoding='ascii')
print(json.dumps({'apk': str(APK), 'apk_bytes': APK.stat().st_size, 'apk_sha256': apk_hash,
                  'source': str(source), 'source_bytes': source.stat().st_size,
                  'tests': report['tests'], 'source_archive_integrity': 'passed', 'signing_material_excluded': True}, indent=2))
