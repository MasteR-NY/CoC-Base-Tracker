import XCTest
@testable import TrackerCore

final class ArchiveTests: XCTestCase {
    private func village(_ tag: String, timestamp: Int = 1793491200) throws -> Village {
        try ExportParser.parse("{\"tag\":\"\(tag)\",\"timestamp\":\(timestamp),\"buildings\":[]}",
                               now: Date(timeIntervalSince1970: 1793500000))
    }

    func testMultipleAccountsStaleImportsAndNicknamePreservation() throws {
        var archive = AccountArchive()
        XCTAssertEqual(archive.importVillage(try village("#ALPHA")), .added)
        archive.rename("#ALPHA", to: "First village")
        XCTAssertEqual(archive.importVillage(try village("#BETA")), .added)
        XCTAssertEqual(archive.importVillage(try village("#ALPHA")), .unchanged)
        XCTAssertEqual(archive.selectedTag, "#BETA")
        XCTAssertEqual(archive.importVillage(try village("#ALPHA", timestamp: 1793491000)), .unchanged)
        XCTAssertEqual(archive.importVillage(try village("#ALPHA", timestamp: 1793491300)), .updated)
        XCTAssertEqual(archive.accounts[0].displayName, "First village")
        XCTAssertEqual(archive.accounts.count, 2)
    }

    func testWidgetAccountBindingSurvivesSelectionAndRemoval() throws {
        var archive = AccountArchive()
        archive.importVillage(try village("#ALPHA"))
        archive.importVillage(try village("#BETA"))
        archive.select("#BETA")
        XCTAssertEqual(archive.widgetAccount(boundTo: "#ALPHA")?.id, "#ALPHA")
        archive.remove("#ALPHA")
        XCTAssertNil(archive.widgetAccount(boundTo: "#ALPHA"))
        XCTAssertEqual(archive.widgetAccount(boundTo: nil)?.id, "#BETA")
        archive.remove("#BETA")
        XCTAssertNil(archive.selectedTag)
    }

    func testAtomicSaveRoundTripAndMissingFile() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = ArchiveFile(url: directory.appendingPathComponent("accounts.json"))
        XCTAssertEqual(try file.load(), AccountArchive())
        var archive = AccountArchive()
        archive.importVillage(try village("#ALPHA"))
        try file.save(archive)
        XCTAssertEqual(try file.load(), archive)
        archive.importVillage(try village("#BETA"))
        try file.save(archive)
        XCTAssertEqual(try file.load(), archive)
    }

    func testCorruptOrFutureArchiveIsNotTreatedAsEmpty() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = ArchiveFile(url: directory.appendingPathComponent("accounts.json"))
        try Data("broken".utf8).write(to: file.url)
        XCTAssertThrowsError(try file.load())
        try Data("{\"version\":99,\"accounts\":[]}".utf8).write(to: file.url)
        XCTAssertThrowsError(try file.load())
    }

    func testNamesRemoveBidiControlsAndPreserveEmoji() {
        XCTAssertEqual(AccountName.clean("  Village   👨‍👩‍👧‍👦  "), "Village 👨‍👩‍👧‍👦")
        XCTAssertEqual(AccountName.clean("A\u{202E}B"), "AB")
        XCTAssertEqual(AccountName.clean(String(repeating: "🏰", count: 40)).count, 32)
    }
}
