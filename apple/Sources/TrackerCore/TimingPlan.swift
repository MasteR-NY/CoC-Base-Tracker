import Foundation

/// Same finite-window work integrator as Android's UpgradeTiming.
public struct TimingPlan: Codable, Equatable, Sendable {
    public struct Boost: Codable, Equatable, Sendable {
        public let family: String
        public let extraSpeed: Int64
        public let seconds: Int64
        public let group: String
        public let label: String

        public init(_ family: String, extraSpeed: Int64, seconds: Int64,
                    group: String? = nil, label: String? = nil) {
            self.family = family
            self.extraSpeed = extraSpeed
            self.seconds = seconds
            self.group = group ?? family
            self.label = label ?? family
        }
    }

    public struct Helper: Codable, Equatable, Sendable {
        public let level: Int64
        public let currentSeconds: Int64
        public let nextStartSeconds: Int64?
        public let label: String

        public init(level: Int64, currentSeconds: Int64, nextStartSeconds: Int64?, label: String) {
            let current = max(0, currentSeconds)
            self.level = max(0, level)
            self.currentSeconds = current
            self.nextStartSeconds = nextStartSeconds.flatMap { $0 < 0 ? nil : max(current, $0) }
            self.label = label
        }

        func activeSeconds(_ elapsed: Int64) -> Int64 {
            var active = min(max(0, elapsed), currentSeconds)
            if let next = nextStartSeconds, elapsed > next {
                let since = elapsed - next
                active += (since / (23 * 3600)) * 3600 + min(since % (23 * 3600), 3600)
            }
            return active
        }

        func isActive(_ elapsed: Int64) -> Bool {
            guard level > 0 else { return false }
            if elapsed < currentSeconds { return true }
            guard let next = nextStartSeconds, elapsed >= next else { return false }
            return (elapsed - next) % (23 * 3600) < 3600
        }
    }

    public let workSeconds: Int64
    public let boosts: [Boost]
    public let helper: Helper?

    public init(workSeconds: Int64, boosts: [Boost] = [], helper: Helper? = nil) {
        self.workSeconds = max(0, workSeconds)
        var families: [String: Boost] = [:]
        for boost in boosts where boost.extraSpeed > 0 && boost.seconds > 0 {
            if families[boost.family].map({ $0.seconds < boost.seconds }) ?? true {
                families[boost.family] = boost
            }
        }
        self.boosts = families.values.sorted { $0.family < $1.family }
        self.helper = helper
    }

    // Imported work is capped at ten years; overflow saturation also keeps this
    // public model safe when callers supply larger synthetic values.
    private func add(_ a: Int64, _ b: Int64) -> Int64 {
        let (value, overflow) = a.addingReportingOverflow(b)
        return overflow ? Int64.max : value
    }

    private func multiply(_ a: Int64, _ b: Int64) -> Int64 {
        let (value, overflow) = a.multipliedReportingOverflow(by: b)
        return overflow ? Int64.max : value
    }

    public func workDone(after elapsed: Int64) -> Int64 {
        let time = max(0, elapsed)
        var work = time
        for group in Dictionary(grouping: boosts, by: \.group).values {
            var previous: Int64 = 0
            for end in Set(group.map { min(time, $0.seconds) }).sorted() {
                let extra = group.filter { $0.seconds >= end }.map(\.extraSpeed).max() ?? 0
                work = add(work, multiply(end - previous, extra))
                previous = end
            }
        }
        if let helper { work = add(work, multiply(helper.activeSeconds(time), helper.level)) }
        return work
    }

    public func remainingWork(after elapsed: Int64) -> Int64 {
        workSeconds - min(workSeconds, workDone(after: elapsed))
    }

    public func speed(after elapsed: Int64) -> Int64 {
        let time = max(0, elapsed)
        var speed: Int64 = 1
        for group in Dictionary(grouping: boosts, by: \.group).values {
            speed = add(speed, group.filter { time < $0.seconds }.map(\.extraSpeed).max() ?? 0)
        }
        if let helper, helper.isActive(time) { speed = add(speed, helper.level) }
        return speed
    }

    public func secondsToFinish() -> Int64 {
        var low: Int64 = 0
        var high = workSeconds
        while low < high {
            let middle = low + (high - low) / 2
            if workDone(after: middle) >= workSeconds { high = middle } else { low = middle + 1 }
        }
        return low
    }
}
