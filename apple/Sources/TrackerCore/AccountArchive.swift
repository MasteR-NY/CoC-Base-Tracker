import Foundation

public struct Account: Codable, Equatable, Identifiable, Sendable {
    public var id: String { village.tag }
    public var nickname: String
    public var village: Village
    public var displayName: String {
        if !nickname.isEmpty { return nickname }
        return village.playerName.isEmpty ? village.tag : village.playerName
    }
}

public enum ImportOutcome: Equatable, Sendable { case added, updated, unchanged }

public struct AccountArchive: Codable, Equatable, Sendable {
    public private(set) var version = 1
    public private(set) var accounts: [Account] = []
    public private(set) var selectedTag: String?
    public init() {}

    public mutating func importVillage(_ village: Village) -> ImportOutcome {
        if let index = accounts.firstIndex(where: { $0.id == village.tag }) {
            guard village.exportedAt > accounts[index].village.exportedAt else { return .unchanged }
            accounts[index].village = village
            selectedTag = village.tag
            return .updated
        }
        accounts.append(Account(nickname: "", village: village))
        selectedTag = village.tag
        return .added
    }

    public mutating func select(_ tag: String) {
        if accounts.contains(where: { $0.id == tag }) { selectedTag = tag }
    }

    public mutating func rename(_ tag: String, to name: String) {
        guard let index = accounts.firstIndex(where: { $0.id == tag }) else { return }
        accounts[index].nickname = AccountName.clean(name)
    }

    public mutating func remove(_ tag: String) {
        accounts.removeAll { $0.id == tag }
        if selectedTag == tag { selectedTag = accounts.first?.id }
    }

    /// A deleted explicitly bound widget must never switch to a different village.
    public func widgetAccount(boundTo tag: String?) -> Account? {
        if let tag { return accounts.first { $0.id == tag } }
        return accounts.first { $0.id == selectedTag } ?? accounts.first
    }
}

public struct ArchiveFile {
    public let url: URL
    public init(url: URL) { self.url = url }

    public func load() throws -> AccountArchive {
        let data: Data
        do { data = try Data(contentsOf: url) }
        catch let error as CocoaError where error.code == .fileReadNoSuchFile { return AccountArchive() }
        let archive = try JSONDecoder().decode(AccountArchive.self, from: data)
        guard archive.version == 1 else {
            throw ExportError.invalid("This saved data needs a newer version of CoC Base Tracker.")
        }
        return archive
    }

    public func save(_ archive: AccountArchive) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(archive)
        #if os(iOS)
        try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        #else
        try data.write(to: url, options: .atomic)
        #endif
    }
}
