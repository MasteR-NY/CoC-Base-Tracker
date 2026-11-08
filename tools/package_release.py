"""Package only public source files; never include local signing material."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs'
OUT.mkdir(exist_ok=True)
APK = OUT / 'VillageClock-1.2.apk'
apk_hash = hashlib.sha256(APK.read_bytes()).hexdigest()
test_suites = [ET.parse(path).getroot() for path in sorted((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))]
assert len(test_suites) >= 4
tests = {key: sum(int(suite.attrib[key]) for suite in test_suites) for key in ('tests', 'failures', 'errors', 'skipped')}
assert tests['failures'] == 0 and tests['errors'] == 0
sarif = json.loads((ROOT / 'app/build/reports/lint-results-release.sarif').read_text(encoding='utf-8'))
lint = collections.Counter(r['ruleId'] for run in sarif['runs'] for r in run.get('results', []))
files = [ROOT / p for p in ['.gitignore', 'README.md', 'LICENSE', 'build.ps1', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'app/build.gradle.kts', 'tools/package_release.py']]
files += sorted((ROOT / 'app/src').rglob('*'))
files += sorted((ROOT / 'docs').rglob('*'))
source = OUT / 'VillageClock-1.2-source.zip'
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in files:
        if file.is_file():
            relative = file.relative_to(ROOT).as_posix()
            assert not any(part in ('.private', '.tools', '.gradle', 'apk-review', 'outputs', 'build') for part in file.relative_to(ROOT).parts)
            z.write(file, 'VillageClock/' + relative)
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert not any(name.endswith(('.p12', '.keystore', 'signing-password.txt', 'local.properties')) for name in z.namelist())
guide = '''VILLAGE CLOCK — ANDROID SETUP

1. Copy VillageClock-1.2.apk to your Android phone and open it. Install as an update over 1.0 or 1.1; do not uninstall first.
2. If Android asks, allow this one installation from your file manager.
3. Open Clash of Clans: Settings > More Settings > Export village data > Copy.
4. Open Village Clock and tap Paste village export.
5. Allow notifications if you want upgrade-finished reminders.
6. On Android 12+, tap Allow precise timers in the app for prompt alerts and finished-widget updates.
7. Tap Add widget and choose the village for that widget, or long-press your home screen > Widgets > Village Clock and choose a village when prompted.

The countdowns tick every second even when Clash is closed. After starting a new upgrade, finishing early, using a potion, or changing a helper assignment, copy a fresh export and tap Sync on the widget.

Scroll vertically inside a widget to see all upgrades. Tap an upgrade to open Clash. The widget does not switch your actual in-game Supercell account.

MULTIPLE ACCOUNTS: Copy and import the village export for each account. Each one is saved separately. Add two widgets and choose a different village for each. Tap the downward arrow beside Sync on an existing widget to change its village. Switching villages in the app does not change other widgets. A widget's Sync rejects clipboard data from the wrong account; use Sync from clipboard / add account in the app to save another account.

CLASH-STYLE DESIGN: This version adapts the reference repository's wooden panel, Lilita One font, gold controls, circular item icons, green countdown bars, and gold completed rows. Both app and widget group upgrades by queue. Green bars show countdown progress since the last exported snapshot. The widget remains scrollable and each widget retains its own selected account.

UPDATING FROM 1.0: Your original village, display name, and reminder cursor migrate automatically. Existing widgets retain that village and gain scrolling. Install over the old version to preserve app data. Names and reminders are now independent per account.

POTIONS AND EVENTS: Stacked doses extend the exported boost duration; they do not multiply the boost speed. Different boosts and the currently assigned helper contribute independently until their respective expirations. Event/Gold Pass reductions already present in the exported upgrade timer are preserved without a second blanket discount. A known Hammer Jam note is informational only. Unobserved future helper assignments or new event speed multipliers still require a fresh export or updated rule. See docs/TIMING.md in the source ZIP.

Explore a sample village is a clearly marked preview. It never schedules real reminders or saves sample data as your village.

PRIVACY: No Supercell login or codes are needed. There is no internet permission, screen capture, accessibility access, or automatic clipboard monitoring. Village data is stored on this phone with backup/transfer exclusions.

LIMITS: New upgrades cannot be discovered automatically with the currently available official data access. Android battery restrictions can delay inexact alarms. After force-stopping the app, reopen it to restore reminders. The export format is unofficial; compare the first imported timers with the game. Android framework tests verify widget list inflation and adapter contents, migration, and account isolation. This build has not been run on a physical phone or emulator here.

Keep the local .private folder secret and backed up for future updates. It is not included in the source ZIP. Do not replace it with the public key from the repository reviewed earlier.
'''
(OUT / 'Setup.txt').write_text(guide, encoding='utf-8')
report = {
    'app': 'Village Clock', 'version': '1.2', 'version_code': 3, 'package': 'com.villageclock.app',
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
    'permissions': ['android.permission.POST_NOTIFICATIONS', 'android.permission.RECEIVE_BOOT_COMPLETED', 'android.permission.SCHEDULE_EXACT_ALARM'],
    'internet_permission': False, 'backup': 'Disabled, with cloud and device transfer exclusions',
    'android_device_test': 'Not run: no attached device or configured emulator available',
    'android_framework_tests': 'Robolectric API 28 and API 35: list widget inflation, all 20 rows in adapter, migration, account isolation, startup',
    'live_game_validation': 'Not run: no private user village export supplied',
    'sync': 'Explicit village export import; native live countdowns afterward',
    'features': ['scrollable list widget', 'multiple saved accounts', 'per-widget account selection', 'per-account names/reminder cursors', '1.0 data migration', 'duration stacks with independent expiry', 'event discount non-duplication'],
    'design': 'Clash-style wooden panels, Lilita One font, gold controls, verified game item icons, grouped green/gold countdown rows; adapted from Coc-Widget at user request',
}
(OUT / 'Verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
(OUT / 'SHA256SUMS.txt').write_text(apk_hash + '  ' + APK.name + '\n' + report['source_sha256'] + '  ' + source.name + '\n', encoding='ascii')
print(json.dumps({'apk': str(APK), 'apk_bytes': APK.stat().st_size, 'apk_sha256': apk_hash,
                  'source': str(source), 'source_bytes': source.stat().st_size,
                  'tests': report['tests'], 'source_archive_integrity': 'passed', 'signing_material_excluded': True}, indent=2))
