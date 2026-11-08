// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CoCBaseTracker",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "TrackerCore", targets: ["TrackerCore"])],
    targets: [
        .target(name: "TrackerCore", resources: [.process("Resources")]),
        .testTarget(name: "TrackerCoreTests", dependencies: ["TrackerCore"], resources: [.process("Fixtures")])
    ]
)
