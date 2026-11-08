import XCTest
@testable import TrackerCore

final class ParserTests: XCTestCase {
    let now = Date(timeIntervalSince1970: 1_793_491_200)

    func parse(_ extra: String) throws -> Village {
        try ExportParser.parse("{\"tag\":\"#TEST01\",\"timestamp\":1793491200,\"buildings\":[],\(extra)}", now: now)
    }

    func testAndroidGoldenExports() throws {
        struct Vector: Decodable {
            let label: String
            let exportJSON: String
            let expectedFinishSeconds: [Int64]
        }
        let url = try XCTUnwrap(Bundle.module.url(forResource: "android-parity", withExtension: "json"))
        let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: url))
        XCTAssertGreaterThanOrEqual(vectors.count, 20)
        for vector in vectors {
            let village = try ExportParser.parse(vector.exportJSON, now: now)
            XCTAssertEqual(village.upgrades.map { Int64($0.finishAt.timeIntervalSince(village.exportedAt)) }.sorted(),
                           vector.expectedFinishSeconds.sorted(), vector.label)
        }
    }

    func testTimestampAnchorsEstimatesAndIdlePreferencesStayIdle() throws {
        let village = try ExportParser.parse("""
        {"tag":"#TEST01","timestamp":1793491100,"buildings":[{"data":1000001,"lvl":12}],
        "units":[{"data":4000000,"lvl":3,"helper_recurrent":true}],
        "spells":[{"data":26000000,"lvl":8,"timer":1000}]}
        """, now: now)
        XCTAssertEqual(village.upgrades.count, 1)
        XCTAssertEqual(village.upgrades[0].finishAt, now.addingTimeInterval(900))
        XCTAssertEqual(village.townHall, 12)
    }

    func testHiredWorkerDoesNotOccupyRegularSlot() throws {
        let village = try ExportParser.parse("""
        {"tag":"#TEST01","timestamp":1793491200,"buildings":[
          {"data":1000015,"cnt":5},{"data":1000008,"lvl":1,"timer":1000},
          {"data":1000009,"lvl":1,"timer":2000,"extra":true}]}
        """, now: now)
        XCTAssertEqual(village.freeBuilders(at: now), 4)
        XCTAssertEqual(village.upgrades.filter(\.extraWorker).count, 1)
        XCTAssertEqual(village.freeBuilders(at: now.addingTimeInterval(3000)), 5)
    }

    func testCooldownIsNotClockTowerBoost() throws {
        let village = try parse("\"boosts\":{\"clocktower_cooldown\":5000},\"units2\":[{\"data\":4000031,\"lvl\":1,\"timer\":1000}]")
        XCTAssertEqual(village.upgrades[0].finishAt, now.addingTimeInterval(1000))
        XCTAssertTrue(village.effects.isEmpty)
    }

    func testUnknownNamesAndCraftedModuleTimers() throws {
        let village = try ExportParser.parse("""
        {"tag":"#test01","timestamp":1793491200,"buildings":[{"data":1000000,"types":[
          {"modules":[{"data":102000033,"lvl":2,"timer":50}]}]}]}
        """, names: NameCatalog(names: [:]), now: now)
        XCTAssertEqual(village.tag, "#TEST01")
        XCTAssertEqual(village.upgrades[0].name, "Builders item #102000033")
        XCTAssertEqual(village.upgrades[0].targetLevel, 3)
    }

    func testNewConstructionMayOmitItsCurrentLevel() throws {
        let village = try parse("\"units\":[{\"data\":4000000,\"timer\":1000}]")
        XCTAssertEqual(village.upgrades[0].targetLevel, 1)
    }

    func testBundledNamesIncludeReviewedCorrections() {
        XCTAssertEqual(NameCatalog.bundled.names["90000016"], "Monolith Arrow")
        XCTAssertEqual(NameCatalog.bundled.names["90000059"], "Electro Fangs")
        XCTAssertEqual(NameCatalog.bundled.names["93000003"], "Prospector")
    }

    func testRejectsMalformedAndOversizedImports() {
        for text in ["", "hello", "[]", "{\"tag\":\"#TEST01\"}", String(repeating: "x", count: 1_000_001)] {
            XCTAssertThrowsError(try ExportParser.parse(text, now: now))
        }
    }

    func testRejectsInvalidNumericFields() {
        for value in ["true", "null", "-1", "0.5", "\"123\"", "316224001", "1e999"] {
            XCTAssertThrowsError(try parse("\"units\":[{\"data\":4000000,\"lvl\":1,\"timer\":\(value)}]"), value)
        }
    }

    func testRejectsFutureTimestampAndMissingItemID() {
        XCTAssertThrowsError(try ExportParser.parse("{\"tag\":\"#TEST01\",\"timestamp\":1793491501,\"buildings\":[]}", now: now))
        XCTAssertThrowsError(try parse("\"units\":[{\"lvl\":1,\"timer\":1000}]"))
        XCTAssertThrowsError(try parse("\"units\":[{\"data\":4000000,\"lvl\":1001,\"timer\":1}]"))
    }

    func testRejectsExcessiveActiveJobs() throws {
        let item = "{\"data\":1000008,\"lvl\":1,\"timer\":1000,\"cnt\":50}"
        let entries = Array(repeating: item, count: 11).joined(separator: ",")
        XCTAssertThrowsError(try ExportParser.parse("{\"tag\":\"#TEST01\",\"timestamp\":1793491200,\"buildings\":[\(entries)]}", now: now))
    }
}
