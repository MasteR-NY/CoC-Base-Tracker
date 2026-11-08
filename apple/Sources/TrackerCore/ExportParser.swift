import Foundation
import CoreFoundation

public enum ExportError: LocalizedError, Equatable {
    case invalid(String)
    public var errorDescription: String? {
        switch self { case .invalid(let message): return message }
    }
}

public struct ExportParser {
    public static let maximumBytes = 1_000_000
    private static let maximumSeconds: Int64 = 10 * 366 * 86400

    public static func parse(_ text: String, names: NameCatalog = .bundled,
                             now: Date = Date()) throws -> Village {
        guard !text.isEmpty, text.utf8.count <= maximumBytes else {
            throw ExportError.invalid("Export is empty or too large (maximum 1 MB).")
        }
        let data = Data(text.utf8)
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw ExportError.invalid("The copied text is not valid village JSON.")
        }
        let timestamp = try bounded(root, "timestamp", maximum: 253_402_300_799)
        guard timestamp >= 1_420_070_400, Double(timestamp) <= now.timeIntervalSince1970 + 300 else {
            throw ExportError.invalid("The export timestamp is invalid. Copy a fresh village export from Clash.")
        }
        guard root["buildings"] is [Any] || root["buildings2"] is [Any] else {
            throw ExportError.invalid("Use Clash → Settings → More Settings → Export village data.")
        }
        let tag = (root["tag"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard tag.range(of: "^#[A-Z0-9]{2,16}$", options: .regularExpression) != nil else {
            throw ExportError.invalid("This export has no valid player tag.")
        }
        let at = Date(timeIntervalSince1970: Double(timestamp))
        let boosts = root["boosts"] as? [String: Any] ?? [:]
        var levels: [Int: Int] = [:]
        var cooldowns: [Int: Int64] = [:]
        let helperEntries = root["helpers"] as? [Any] ?? []
        guard helperEntries.count <= 10_000 else { throw ExportError.invalid("Too many helper entries.") }
        for case let helper as [String: Any] in helperEntries {
            let id = integer(helper["data"], fallback: -1)
            levels[id] = min(20, max(0, integer(helper["lvl"])))
            if helper["helper_cooldown"] != nil {
                cooldowns[id] = try bounded(helper, "helper_cooldown")
            }
        }
        let legacyCooldown = try bounded(boosts, "helper_cooldown")
        let sections: [(String, UpgradeQueue)] = [
            ("buildings", .builders), ("traps", .builders), ("heroes", .builders), ("guardians", .builders),
            ("units", .laboratory), ("spells", .laboratory), ("siege_machines", .laboratory),
            ("pets", .pets), ("buildings2", .builderBase), ("traps2", .builderBase),
            ("heroes2", .builderBase), ("units2", .starLaboratory)
        ]
        var upgrades: [Upgrade] = []
        var townHall = 0
        var builders = 0
        var baseBuilders = 0
        var total = 0

        func append(_ item: [String: Any], count: Int, queue: UpgradeQueue) throws {
            let work = try bounded(item, "timer")
            guard work > 0 else { return }
            let id = integer(item["data"], fallback: -1)
            // A newly constructed item may omit its current level (Android uses 0).
            let level = item["lvl"] == nil ? 0 : integer(item["lvl"], fallback: -1)
            guard id >= 0, (0...1000).contains(level) else {
                throw ExportError.invalid("An upgrade has an invalid item ID or level.")
            }
            var windows: [TimingPlan.Boost] = []
            func boost(_ key: String, _ extra: Int64, _ group: String, _ label: String) throws {
                let duration = try bounded(boosts, key)
                if duration > 0 {
                    windows.append(.init(key, extraSpeed: extra, seconds: duration, group: group, label: label))
                }
            }
            switch queue {
            case .builders:
                try boost("builder_boost", 9, "builder-speed", "Builder Potion")
                try boost("builder_consumable", 1, "builder-speed", "Builder Bite")
            case .laboratory:
                try boost("lab_boost", 23, "research", "Research Potion")
                try boost("lab_consumable", 3, "soup", "Study Soup")
            case .pets:
                try boost("pet_boost", 23, "pet", "Pet Potion")
                try boost("lab_consumable", 3, "soup", "Study Soup")
            case .builderBase, .starLaboratory:
                try boost("clocktower_boost", 9, "clock-tower", "Clock Tower")
            }
            let helperID = queue == .builders ? 93000000 : queue == .laboratory ? 93000001 : 0
            let current = try bounded(item, "helper_timer")
            let cooldown = cooldowns[helperID] ?? legacyCooldown
            let recurrent = boolean(item["helper_recurrent"] ?? item["recurrent_helper"])
            let helper: TimingPlan.Helper?
            if helperID != 0, let level = levels[helperID], level > 0 {
                helper = .init(level: Int64(level), currentSeconds: current,
                               nextStartSeconds: recurrent && cooldown > 0 ? cooldown : nil,
                               label: queue == .builders ? "Builder's Apprentice" : "Lab Assistant")
            } else { helper = nil }
            let plan = TimingPlan(workSeconds: work, boosts: windows, helper: helper)
            let finish = at.addingTimeInterval(Double(plan.secondsToFinish()))
            let name = names.names[String(id)].flatMap { $0.isEmpty ? nil : $0 } ?? "\(queue.label) item #\(id)"
            guard upgrades.count + count <= 500 else {
                throw ExportError.invalid("Too many active upgrades in this export.")
            }
            for _ in 0..<count {
                upgrades.append(Upgrade(id: "\(queue.rawValue):\(id):\(level + 1):\(upgrades.count)",
                                        dataID: id, targetLevel: level + 1, name: name, queue: queue,
                                        finishAt: finish, extraWorker: boolean(item["extra"]),
                                        snapshotAt: at, timing: plan))
            }
        }

        for (section, queue) in sections {
            let entries = root[section] as? [Any] ?? []
            total += entries.count
            guard total <= 10_000 else { throw ExportError.invalid("This export contains too many entries.") }
            for case let item as [String: Any] in entries {
                let id = integer(item["data"], fallback: -1)
                let count = min(50, max(1, integer(item["cnt"], fallback: 1)))
                if section == "buildings" {
                    if id == 1000001 { townHall = min(100, max(0, integer(item["lvl"]))) }
                    if id == 1000015 { builders += count }
                    if id == 1000064 { builders += 1 }
                }
                if section == "buildings2" {
                    if id == 1000034 { baseBuilders = max(1, baseBuilders) }
                    if id == 1000078 || id == 1000047 { baseBuilders = 2 }
                }
                try append(item, count: count, queue: queue)
                for case let type as [String: Any] in (item["types"] as? [Any] ?? []).prefix(100) {
                    for case let module as [String: Any] in (type["modules"] as? [Any] ?? []).prefix(100) {
                        try append(module, count: 1, queue: queue)
                    }
                }
            }
        }
        let effectNames = ["builder_boost": "Builder Potion", "builder_consumable": "Builder Bite",
                           "lab_boost": "Research Potion", "pet_boost": "Pet Potion",
                           "lab_consumable": "Study Soup", "clocktower_boost": "Clock Tower"]
        var effects: [ActiveEffect] = []
        for key in effectNames.keys.sorted() {
            let duration = try bounded(boosts, key)
            if duration > 0 {
                effects.append(.init(id: key, name: effectNames[key]!, endsAt: at.addingTimeInterval(Double(duration))))
            }
        }
        return Village(tag: tag, playerName: AccountName.clean(root["name"] as? String ?? ""),
                       exportedAt: at, townHall: townHall, builders: min(7, builders),
                       baseBuilders: baseBuilders,
                       upgrades: upgrades.sorted { $0.finishAt == $1.finishAt ? $0.id < $1.id : $0.finishAt < $1.finishAt },
                       effects: effects)
    }

    private static func bounded(_ object: [String: Any], _ key: String,
                                maximum: Int64 = maximumSeconds) throws -> Int64 {
        guard let raw = object[key] else { return 0 }
        guard let value = raw as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() else {
            throw ExportError.invalid("Invalid numeric field: \(key)")
        }
        let number = value.doubleValue
        guard number.isFinite, number >= 0, number <= Double(maximum), number.rounded(.down) == number else {
            throw ExportError.invalid("Out-of-range field: \(key)")
        }
        return Int64(number)
    }

    private static func integer(_ raw: Any?, fallback: Int = 0) -> Int {
        guard let value = raw as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() else { return fallback }
        let number = value.doubleValue
        guard number.isFinite, number >= -2_147_483_648, number <= 2_147_483_647,
              number.rounded(.down) == number else { return fallback }
        return Int(number)
    }

    private static func boolean(_ raw: Any?) -> Bool {
        guard let value = raw as? NSNumber, CFGetTypeID(value) == CFBooleanGetTypeID() else { return false }
        return value.boolValue
    }
}
