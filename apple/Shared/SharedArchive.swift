import Foundation
import TrackerCore

enum SharedArchive {
    static func file() throws -> ArchiveFile {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "TrackerAppGroup") as? String,
              !group.isEmpty, !group.contains("$("),
              let directory = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw ExportError.invalid("Shared storage is unavailable. Check the App Group and signing settings for both Apple targets.")
        }
        return ArchiveFile(url: directory.appendingPathComponent("accounts-v1.json"))
    }
}
