import XCTest
@testable import TrackerCore

final class TimingTests: XCTestCase {
    private func potion(_ seconds: Int64) -> TimingPlan.Boost {
        .init("potion", extraSpeed: 9, seconds: seconds, group: "builder-speed")
    }
    private func bite(_ seconds: Int64) -> TimingPlan.Boost {
        .init("bite", extraSpeed: 1, seconds: seconds, group: "builder-speed")
    }

    func testFinitePotionAndRounding() {
        XCTAssertEqual(TimingPlan(workSeconds: 172800, boosts: [.init("research", extraSpeed: 23, seconds: 3600)]).secondsToFinish(), 90000)
        XCTAssertEqual(TimingPlan(workSeconds: 3601, boosts: [potion(7200)]).secondsToFinish(), 361)
        XCTAssertEqual(TimingPlan(workSeconds: 3600, boosts: [potion(100)]).secondsToFinish(), 2700)
    }

    func testStackedDosesAndDuplicateFamilies() {
        XCTAssertEqual(TimingPlan(workSeconds: 72000, boosts: [potion(7200)]).secondsToFinish(), 7200)
        XCTAssertEqual(TimingPlan(workSeconds: 72000, boosts: [potion(3600), potion(7200)]).secondsToFinish(), 7200)
    }

    func testPotionTakesPriorityOverBite() {
        XCTAssertEqual(TimingPlan(workSeconds: 39600, boosts: [potion(3600), bite(3600)]).secondsToFinish(), 7200)
        XCTAssertEqual(TimingPlan(workSeconds: 3300, boosts: [potion(200), bite(300), .init("helper", extraSpeed: 8, seconds: 100)]).secondsToFinish(), 600)
    }

    func testSoupAndHelperAreAdditive() {
        let plan = TimingPlan(workSeconds: 140400, boosts: [
            .init("research", extraSpeed: 23, seconds: 3600), .init("soup", extraSpeed: 3, seconds: 3600)
        ], helper: .init(level: 12, currentSeconds: 3600, nextStartSeconds: nil, label: "Lab Assistant"))
        XCTAssertEqual(plan.speed(after: 0), 39)
        XCTAssertEqual(plan.secondsToFinish(), 3600)
        XCTAssertEqual(plan.speed(after: 3600), 1)
    }

    func testHelperForecastUses23HourDay() {
        let helper = TimingPlan.Helper(level: 8, currentSeconds: 0, nextStartSeconds: 100, label: "Apprentice")
        let plan = TimingPlan(workSeconds: 1_000_000, helper: helper)
        XCTAssertEqual(plan.workDone(after: 99), 99)
        XCTAssertEqual(plan.speed(after: 100), 9)
        XCTAssertEqual(plan.speed(after: 3700), 1)
        XCTAssertEqual(plan.speed(after: 100 + 23 * 3600), 9)
        XCTAssertEqual(plan.workDone(after: 100 + 23 * 3600 + 3600), 86500 + 2 * 3600 * 8)
    }

    func testZeroAndLargeWork() {
        XCTAssertEqual(TimingPlan(workSeconds: 0, boosts: [potion(3600)]).secondsToFinish(), 0)
        XCTAssertEqual(TimingPlan(workSeconds: Int64.max).secondsToFinish(), Int64.max)
        XCTAssertEqual(TimingPlan(workSeconds: Int64.max, boosts: [potion(1)]).secondsToFinish(), Int64.max - 9)
    }

    func testIntegratorAgainstIndependentSecondBySecondSimulation() {
        for seed in 1...30 {
            let boosts = [potion(Int64(seed * 7)), bite(Int64(seed * 13)),
                          TimingPlan.Boost("soup", extraSpeed: 3, seconds: Int64(seed * 4))]
            let helper = TimingPlan.Helper(level: Int64(seed % 8 + 1), currentSeconds: Int64(seed * 3),
                                           nextStartSeconds: Int64(seed * 20), label: "Helper")
            let work: Int64 = 100_000 + Int64(seed * 173)
            let plan = TimingPlan(workSeconds: work, boosts: boosts, helper: helper)
            var done: Int64 = 0
            var elapsed: Int64 = 0
            while done < work {
                let potionRate: Int64 = elapsed < Int64(seed * 7) ? 9 : 0
                let biteRate: Int64 = elapsed < Int64(seed * 13) ? 1 : 0
                let soupRate: Int64 = elapsed < Int64(seed * 4) ? 3 : 0
                let helperActive = elapsed < Int64(seed * 3) ||
                    (elapsed >= Int64(seed * 20) && (elapsed - Int64(seed * 20)) % 82800 < 3600)
                done += 1 + max(potionRate, biteRate) + soupRate + (helperActive ? Int64(seed % 8 + 1) : 0)
                elapsed += 1
            }
            XCTAssertEqual(plan.secondsToFinish(), elapsed, "seed \(seed)")
        }
    }
}
