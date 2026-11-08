"""Read-only Apple project/catalog preflight. This does not compile Swift.

Run this on Windows or macOS, then use swift test and xcodebuild on a Mac.
"""
from __future__ import annotations

import json
from pathlib import Path
import plistlib
import struct
import xml.etree.ElementTree as ET

from prepare_apple import APPLE, ROOT, artifacts, public_source_files


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def verify():
    for path, expected in artifacts().items():
        require(path.exists() and path.read_bytes() == expected, f"Stale artifact: {path.relative_to(ROOT)}; run tools/prepare_apple.py")
    project = plistlib.loads((APPLE / "CoCBaseTracker.xcodeproj/project.pbxproj").read_bytes())
    objects = project["objects"]
    reference_keys = {"buildConfigurationList", "mainGroup", "productRefGroup", "productReference", "fileRef", "productRef",
                      "baseConfigurationReference", "target", "targetProxy", "containerPortal", "remoteGlobalIDString"}
    reference_lists = {"children", "buildConfigurations", "buildPhases", "files", "dependencies", "targets",
                       "packageProductDependencies", "packageReferences"}
    for obj in objects.values():
        for key, value in obj.items():
            references = [value] if key in reference_keys else value if key in reference_lists else []
            for reference in references:
                require(reference in objects, f"Dangling {key}: {reference}")
        if obj["isa"] == "PBXFileReference" and obj.get("sourceTree") == "<group>":
            require((APPLE / obj["path"]).exists(), f"Missing Xcode source: {obj['path']}")

    targets = {o["name"]: o for o in objects.values() if o["isa"] == "PBXNativeTarget"}
    require(set(targets) == {"CoCBaseTracker", "VillageWidget"}, "Missing app/widget target")
    for name, target in targets.items():
        configs = objects[target["buildConfigurationList"]]["buildConfigurations"]
        for config in configs:
            settings = objects[config]["buildSettings"]
            require(settings["CODE_SIGN_ENTITLEMENTS"] == "Config/Tracker.entitlements", "App Group entitlement mismatch")
        linked = [objects[key] for key in target["packageProductDependencies"]]
        require(any(p["productName"] == "TrackerCore" for p in linked), f"Core not linked: {name}")
        sources = [objects[key] for key in target["buildPhases"] if objects[key]["isa"] == "PBXSourcesBuildPhase"]
        paths = {objects[objects[key]["fileRef"]]["path"] for phase in sources for key in phase["files"]}
        expected = {p.relative_to(APPLE).as_posix() for folder in ("Shared", "App" if name == "CoCBaseTracker" else "Widget")
                    for p in (APPLE / folder).glob("*.swift")}
        require(paths == expected, f"Source membership mismatch: {name}")
        resources = [objects[key] for key in target["buildPhases"] if objects[key]["isa"] == "PBXResourcesBuildPhase"]
        paths = {objects[objects[key]["fileRef"]]["path"] for phase in resources for key in phase["files"]}
        require("Config/PrivacyInfo.xcprivacy" in paths, "Privacy manifest not copied")
    embed = [objects[key] for key in targets["CoCBaseTracker"]["buildPhases"] if objects[key]["isa"] == "PBXCopyFilesBuildPhase"]
    require(len(embed) == 1 and embed[0]["dstSubfolderSpec"] == "13", "Widget not embedded in PlugIns")
    project_settings = objects[project["rootObject"]]["buildConfigurationList"]
    for key in objects[project_settings]["buildConfigurations"]:
        settings = objects[key]["buildSettings"]
        require(settings["TARGETED_DEVICE_FAMILY"] == "1,2", "Must support iPhone and iPad")
        require(settings["IPHONEOS_DEPLOYMENT_TARGET"] == "17.0", "Unexpected deployment target")
    for path in (APPLE / "Config").glob("*"):
        if path.suffix in (".plist", ".entitlements", ".xcprivacy"):
            plistlib.loads(path.read_bytes())
    for name in ("App", "Widget"):
        info = plistlib.loads((APPLE / f"Config/{name}-Info.plist").read_bytes())
        require(info["TrackerAppGroup"] == "$(TRACKER_APP_GROUP)", "Storage group setting mismatch")
    entitlements = plistlib.loads((APPLE / "Config/Tracker.entitlements").read_bytes())
    require(entitlements["com.apple.security.application-groups"] == ["$(TRACKER_APP_GROUP)"], "Signing group mismatch")
    scheme = ET.parse(APPLE / "CoCBaseTracker.xcodeproj/xcshareddata/xcschemes/CoCBaseTracker.xcscheme")
    for buildable in scheme.findall(".//BuildableReference"):
        require(buildable.attrib["BlueprintIdentifier"] in objects, "Scheme has an unknown target")
    content = json.loads((APPLE / "Resources/Assets.xcassets/AppIcon.appiconset/Contents.json").read_text())
    for icon in content["images"]:
        path = APPLE / "Resources/Assets.xcassets/AppIcon.appiconset" / icon["filename"]
        header = path.read_bytes()[:26]
        require(header[:8] == b"\x89PNG\r\n\x1a\n", "Invalid icon PNG")
        require(struct.unpack(">II", header[16:24]) == (1024, 1024) and header[25] == 2, "App icon must be opaque 1024x1024 RGB")
    vectors = json.loads((APPLE / "Tests/TrackerCoreTests/Fixtures/android-parity.json").read_text())
    require(len(vectors) >= 20, "Missing timing parity cases")
    for vector in vectors:
        export = json.loads(vector["exportJSON"])
        require(export["tag"] == "#SYNTH01", "Use synthetic exports only")
        require(all(type(value) is int and value >= 0 for value in vector["expectedFinishSeconds"]), "Invalid golden finish")
    files = list(public_source_files())
    require(all("xcuserdata" not in p.parts and ".build" not in p.parts for p in files), "Personal/build files in source package")
    require(APPLE / "CoCBaseTracker.xcodeproj/project.pbxproj" in files, "Source package omits Xcode project")
    print(f"Apple preflight passed: 2 universal targets, {len(vectors)} synthetic parity cases, {len(files)} public Apple files.")
    print("Swift compilation, XCTest execution, simulator and device validation require a Mac and have not run in this preflight.")


if __name__ == "__main__":
    verify()
