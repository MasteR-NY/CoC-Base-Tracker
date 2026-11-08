"""Generate the public changelog from the release guide bundled in the APK."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def update_changelog():
    config = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
    releases = json.loads((ROOT / 'app/src/main/assets/release-notes.json').read_text(encoding='utf-8'))['releases']
    releases.sort(key=lambda entry: entry['versionCode'], reverse=True)
    assert releases[0]['version'] == version and releases[0]['versionCode'] == code, 'Add notes for this build first.'
    assert len({entry['versionCode'] for entry in releases}) == len(releases), 'Duplicate release code.'
    assert len({entry['version'] for entry in releases}) == len(releases), 'Duplicate release version.'
    lines = ['# Release Notes', '', 'Changes and usage notes for CoC Base Tracker. The installed app includes this guide under **What’s New**.', '']
    for entry in releases:
        lines += [f"## v{entry['version']} — {entry['title']}", '']
        for point in entry['highlights']:
            assert point['title'].strip() and point['body'].strip()
            lines += [f"- **{point['title']}:** {point['body']}"]
        lines += ['']
        if entry.get('steps'):
            lines += ['### How to Use It', '']
            lines += [f'{index}. {step}' for index, step in enumerate(entry['steps'], 1)]
            lines += ['']
    (ROOT / 'CHANGELOG.md').write_text('\n'.join(lines), encoding='utf-8')


if __name__ == '__main__':
    update_changelog()
    print('Updated CHANGELOG.md from the bundled release guide.')
