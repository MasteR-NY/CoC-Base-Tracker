import Foundation

public enum UpgradeQueue: String, Codable, CaseIterable, Sendable {
    case builders, laboratory, pets, builderBase, starLaboratory

    public var label: String {
        switch self {
        case .builders: return "Builders"
        case .laboratory: return "Laboratory"
        case .pets: return "Pet House"
        case .builderBase: return "Builder Base"
        case .starLaboratory: return "Star Laboratory"
        }
    }

    public var symbol: String {
        switch self {
        case .builders: return "hammer.fill"
        case .laboratory: return "flask.fill"
        case .pets: return "pawprint.fill"
        case .builderBase: return "wrench.and.screwdriver.fill"
        case .starLaboratory: return "sparkles"
        }
    }
}

public struct Upgrade: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let dataID: Int
    public let targetLevel: Int
    public let name: String
    public let queue: UpgradeQueue
    public let finishAt: Date
    public let extraWorker: Bool
    public let snapshotAt: Date
    public let timing: TimingPlan

    public func elapsed(at date: Date) -> Int64 {
        Int64(max(0, date.timeIntervalSince(snapshotAt)))
    }
}

public struct ActiveEffect: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let name: String
    public let endsAt: Date
}

public struct Village: Codable, Equatable, Identifiable, Sendable {
    public var id: String { tag }
    public let tag: String
    public let playerName: String
    public let exportedAt: Date
    public let townHall: Int
    public let builders: Int
    public let baseBuilders: Int
    public let upgrades: [Upgrade]
    public let effects: [ActiveEffect]

    public func widgetUpgrades(at date: Date) -> [Upgrade] {
        upgrades.filter { $0.finishAt > date } + upgrades.filter { $0.finishAt <= date }
    }

    public func freeBuilders(at date: Date) -> Int {
        max(0, builders - upgrades.filter {
            $0.queue == .builders && !$0.extraWorker && $0.finishAt > date
        }.count)
    }
}

public enum AccountName {
    public static func clean(_ value: String) -> String {
        let filtered = value.unicodeScalars.compactMap { scalar -> Unicode.Scalar? in
            if CharacterSet.whitespacesAndNewlines.contains(scalar) { return Unicode.Scalar(32) }
            let code = scalar.value
            if code < 32 || (0x7F...0x9F).contains(code) ||
                (0x202A...0x202E).contains(code) || (0x2066...0x2069).contains(code) { return nil }
            return scalar
        }
        let text = String(String.UnicodeScalarView(filtered))
            .split(whereSeparator: \.isWhitespace).joined(separator: " ")
        // Character boundaries preserve full emoji and international names.
        return String(text.prefix(32))
    }
}

public struct NameCatalog: Sendable {
    public let names: [String: String]
    public init(names: [String: String]) { self.names = names }
    public static let bundled: NameCatalog = {
        guard let url = Bundle.module.url(forResource: "item-names", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let names = try? JSONDecoder().decode([String: String].self, from: data) else {
            return NameCatalog(names: [:])
        }
        return NameCatalog(names: names)
    }()
}
