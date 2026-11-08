"""Generate the checked-in Apple project and offline names. No network or SDK needed.

Run after adding Apple sources or refreshing Android catalog names. --check is
read-only and fails if generated artifacts differ. Xcode also reads XML plists
for project.pbxproj, letting us validate the entire object graph with plistlib.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APPLE = ROOT / "apple"
ASSETS = ROOT / "app/src/main/assets"


def public_source_files():
    """Explicit public Apple source roots; exclude Xcode state and build outputs."""
    allowed = {".swift", ".json", ".plist", ".xcprivacy", ".entitlements", ".xcconfig", ".png", ".txt"}
    yield APPLE / "Package.swift"
    for folder in ("App", "Shared", "Widget", "Config", "Sources", "Tests", "Resources"):
        for path in sorted((APPLE / folder).rglob("*")):
            if path.is_file() and path.suffix in allowed:
                yield path
    yield APPLE / "CoCBaseTracker.xcodeproj/project.pbxproj"
    yield APPLE / "CoCBaseTracker.xcodeproj/xcshareddata/xcschemes/CoCBaseTracker.xcscheme"


def identifier(name: str) -> str:
    return hashlib.sha256(name.encode()).hexdigest()[:24].upper()


def names_data() -> bytes:
    def read(name):
        return json.loads((ASSETS / name).read_text(encoding="utf-8"))
    names = {key: value["n"] for key, value in read("gamedata.json").items()}
    for item in read("planner-catalog.json")["items"]:
        if item.get("dataId") is not None and item.get("name"):
            names[str(item["dataId"])] = item["name"]
    for key, item in read("item-identities.json")["items"].items():
        if item.get("name"):
            names[key] = item["name"]
    return (json.dumps(names, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def notices_data() -> bytes:
    sections = [
        "CoC Base Tracker — Apple data credits\n\n"
        "Original application code: MIT (see repository LICENSE).\n"
        "Clash of Clans and game names © Supercell. Unofficial fan companion; "
        "not endorsed by Supercell. No third-party game artwork or fonts are bundled in this Apple preview.\n"
        "Names derive from the Android catalogs: Christopher Pezza, chiefpansancolt/clash-of-clans-data, "
        "ClashKing/Clashy, and the verified overrides documented in docs/DESIGN-CREDITS.md "
        "and docs/ITEM-IDENTITIES.md.\n",
        (ASSETS / "data-license.txt").read_text(encoding="utf-8"),
        (ROOT / "docs/licenses/Clashy-MIT.txt").read_text(encoding="utf-8"),
        (ROOT / "LICENSE").read_text(encoding="utf-8"),
    ]
    return ("\n\n".join(sections) + "\n").encode()


def project_data() -> tuple[bytes, bytes]:
    objects = {}

    def add(object_name, isa, **values):
        key = identifier(object_name)
        assert key not in objects
        objects[key] = {"isa": isa, **values}
        return key

    files = {}
    paths = sorted([p.relative_to(APPLE).as_posix() for folder in ("App", "Shared", "Widget", "Config")
                    for p in (APPLE / folder).rglob("*") if p.is_file()])
    paths += ["Resources/THIRD-PARTY-NOTICES.txt", "Resources/Assets.xcassets"]
    # The generated notices file may already be present in neither source folder.
    for path in paths:
        suffix = Path(path).suffix
        kind = {".swift": "sourcecode.swift", ".plist": "text.plist.xml",
                ".entitlements": "text.plist.entitlements", ".xcconfig": "text.xcconfig",
                ".xcprivacy": "text.xml", ".xcassets": "folder.assetcatalog"}.get(suffix, "text")
        files[path] = add("file:" + path, "PBXFileReference", lastKnownFileType=kind, path=path, sourceTree="<group>")

    app_product = add("product:app", "PBXFileReference", explicitFileType="wrapper.application",
                      path="CoCBaseTracker.app", sourceTree="BUILT_PRODUCTS_DIR")
    widget_product = add("product:widget", "PBXFileReference", explicitFileType="wrapper.app-extension",
                         path="VillageWidget.appex", sourceTree="BUILT_PRODUCTS_DIR")
    products = add("group:products", "PBXGroup", children=[app_product, widget_product], name="Products", sourceTree="<group>")
    main_group = add("group:main", "PBXGroup", children=list(files.values()) + [products], sourceTree="<group>")
    package = add("package:core", "XCLocalSwiftPackageReference", relativePath=".")

    def configuration_list(name, settings, base=False):
        configurations = []
        for mode in ("Debug", "Release"):
            build = dict(settings)
            build["SWIFT_OPTIMIZATION_LEVEL"] = "-Onone" if mode == "Debug" else "-O"
            build["DEBUG_INFORMATION_FORMAT"] = "dwarf" if mode == "Debug" else "dwarf-with-dsym"
            if mode == "Debug":
                build["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG $(inherited)"
                build["ENABLE_TESTABILITY"] = "YES"
            extra = {"baseConfigurationReference": files["Config/Signing.xcconfig"]} if base else {}
            configurations.append(add(f"config:{name}:{mode}", "XCBuildConfiguration", name=mode, buildSettings=build, **extra))
        return add("configlist:" + name, "XCConfigurationList", buildConfigurations=configurations,
                   defaultConfigurationIsVisible="0", defaultConfigurationName="Release")

    project_config = configuration_list("project", {
        "SDKROOT": "iphoneos", "IPHONEOS_DEPLOYMENT_TARGET": "17.0", "SWIFT_VERSION": "5.0",
        "CLANG_ENABLE_MODULES": "YES", "CLANG_ENABLE_OBJC_ARC": "YES",
        "TARGETED_DEVICE_FAMILY": "1,2", "CODE_SIGN_STYLE": "Automatic",
        "MARKETING_VERSION": "0.1.0", "CURRENT_PROJECT_VERSION": "1",
        "ENABLE_USER_SCRIPT_SANDBOXING": "YES", "SWIFT_EMIT_LOC_STRINGS": "YES",
    }, base=True)

    target_ids = {}
    for name, source_folder, product, plist in (
        ("app", "App", app_product, "App-Info.plist"),
        ("widget", "Widget", widget_product, "Widget-Info.plist"),
    ):
        source_files = []
        resource_files = []
        for path, reference in files.items():
            if path.endswith(".swift") and path.split("/")[0] in (source_folder, "Shared"):
                source_files.append(add(f"build:{name}:{path}", "PBXBuildFile", fileRef=reference))
            if path == "Config/PrivacyInfo.xcprivacy" or (name == "app" and path.startswith("Resources/")):
                resource_files.append(add(f"build:{name}:{path}", "PBXBuildFile", fileRef=reference))
        core = add("core-product:" + name, "XCSwiftPackageProductDependency", productName="TrackerCore")
        core_build = add("core-build:" + name, "PBXBuildFile", productRef=core)
        sources = add("sources:" + name, "PBXSourcesBuildPhase", buildActionMask="2147483647", files=source_files, runOnlyForDeploymentPostprocessing="0")
        resources = add("resources:" + name, "PBXResourcesBuildPhase", buildActionMask="2147483647", files=resource_files, runOnlyForDeploymentPostprocessing="0")
        frameworks = add("frameworks:" + name, "PBXFrameworksBuildPhase", buildActionMask="2147483647", files=[core_build], runOnlyForDeploymentPostprocessing="0")
        settings = {
            "PRODUCT_NAME": "CoCBaseTracker" if name == "app" else "VillageWidget",
            "PRODUCT_BUNDLE_IDENTIFIER": "$(TRACKER_BUNDLE_ID)" + (".widgets" if name == "widget" else ""),
            "INFOPLIST_FILE": "Config/" + plist, "GENERATE_INFOPLIST_FILE": "NO",
            "CODE_SIGN_ENTITLEMENTS": "Config/Tracker.entitlements",
            "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks"] + (["@executable_path/../../Frameworks"] if name == "widget" else []),
            "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator", "SUPPORTS_MACCATALYST": "NO",
        }
        if name == "widget":
            settings.update(APPLICATION_EXTENSION_API_ONLY="YES", SKIP_INSTALL="YES")
        else:
            settings["ASSETCATALOG_COMPILER_APPICON_NAME"] = "AppIcon"
        config = configuration_list(name, settings)
        target_ids[name] = add("target:" + name, "PBXNativeTarget", name=settings["PRODUCT_NAME"],
                               productName=settings["PRODUCT_NAME"], productReference=product,
                               productType="com.apple.product-type.application" if name == "app" else "com.apple.product-type.app-extension",
                               buildConfigurationList=config, buildPhases=[sources, frameworks, resources],
                               buildRules=[], dependencies=[], packageProductDependencies=[core])

    proxy = add("widget-proxy", "PBXContainerItemProxy", containerPortal=identifier("project"), proxyType="1",
                remoteGlobalIDString=target_ids["widget"], remoteInfo="VillageWidget")
    dependency = add("widget-dependency", "PBXTargetDependency", target=target_ids["widget"], targetProxy=proxy)
    embed_file = add("embed-widget-file", "PBXBuildFile", fileRef=widget_product, settings={"ATTRIBUTES": ["RemoveHeadersOnCopy"]})
    embed = add("embed-widget-phase", "PBXCopyFilesBuildPhase", buildActionMask="2147483647", dstPath="",
                dstSubfolderSpec="13", files=[embed_file], name="Embed App Extensions", runOnlyForDeploymentPostprocessing="0")
    objects[target_ids["app"]]["dependencies"] = [dependency]
    objects[target_ids["app"]]["buildPhases"].append(embed)
    root = add("project", "PBXProject", attributes={"BuildIndependentTargetsInParallel": "YES", "LastUpgradeCheck": "1600",
               "TargetAttributes": {key: {"CreatedOnToolsVersion": "16.0", "SystemCapabilities": {
                   "com.apple.ApplicationGroups.iOS": {"enabled": "1"}}} for key in target_ids.values()}},
               buildConfigurationList=project_config, compatibilityVersion="Xcode 14.0",
               developmentRegion="en", hasScannedForEncodings="0", knownRegions=["en", "Base"],
               mainGroup=main_group, productRefGroup=products, projectDirPath="", projectRoot="",
               targets=list(target_ids.values()), packageReferences=[package])

    scheme = ET.Element("Scheme", LastUpgradeVersion="1600", version="1.3")
    def reference(parent):
        ET.SubElement(parent, "BuildableReference", BuildableIdentifier="primary", BlueprintIdentifier=target_ids["app"],
                      BuildableName="CoCBaseTracker.app", BlueprintName="CoCBaseTracker",
                      ReferencedContainer="container:CoCBaseTracker.xcodeproj")
    build = ET.SubElement(scheme, "BuildAction", parallelizeBuildables="YES", buildImplicitDependencies="YES")
    build_entry = ET.SubElement(ET.SubElement(build, "BuildActionEntries"), "BuildActionEntry",
                                buildForTesting="YES", buildForRunning="YES", buildForProfiling="YES", buildForArchiving="YES", buildForAnalyzing="YES")
    reference(build_entry)
    ET.SubElement(scheme, "TestAction", buildConfiguration="Debug", selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB",
                  selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB", shouldUseLaunchSchemeArgsEnv="YES")
    launch = ET.SubElement(scheme, "LaunchAction", buildConfiguration="Debug", selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB",
                           selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB", launchStyle="0", useCustomWorkingDirectory="NO",
                           ignoresPersistentStateOnLaunch="NO", debugDocumentVersioning="YES", debugServiceExtension="internal", allowLocationSimulation="YES")
    reference(ET.SubElement(launch, "BuildableProductRunnable", runnableDebuggingMode="0"))
    profile = ET.SubElement(scheme, "ProfileAction", buildConfiguration="Release", shouldUseLaunchSchemeArgsEnv="YES", savedToolIdentifier="",
                            useCustomWorkingDirectory="NO", debugDocumentVersioning="YES")
    reference(ET.SubElement(profile, "BuildableProductRunnable", runnableDebuggingMode="0"))
    ET.SubElement(scheme, "AnalyzeAction", buildConfiguration="Debug")
    ET.SubElement(scheme, "ArchiveAction", buildConfiguration="Release", revealArchiveInOrganizer="YES")
    ET.indent(scheme)
    project = {"archiveVersion": "1", "classes": {}, "objectVersion": "56", "objects": objects, "rootObject": root}
    return plistlib.dumps(project, sort_keys=False), ET.tostring(scheme, encoding="utf-8", xml_declaration=True) + b"\n"


def artifacts() -> dict[Path, bytes]:
    project, scheme = project_data()
    return {
        APPLE / "Sources/TrackerCore/Resources/item-names.json": names_data(),
        APPLE / "Resources/THIRD-PARTY-NOTICES.txt": notices_data(),
        APPLE / "CoCBaseTracker.xcodeproj/project.pbxproj": project,
        APPLE / "CoCBaseTracker.xcodeproj/xcshareddata/xcschemes/CoCBaseTracker.xcscheme": scheme,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, content in artifacts().items():
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                stale.append(path.relative_to(ROOT).as_posix())
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if stale:
        sys.exit("Run python tools/prepare_apple.py to refresh: " + ", ".join(stale))
    print("Apple project, catalog names and notices " + ("verified." if args.check else "prepared."))


if __name__ == "__main__":
    main()
