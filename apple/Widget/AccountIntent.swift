import AppIntents
import TrackerCore

struct VillageEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Village"
    static var defaultQuery = VillageQuery()
    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", subtitle: "\(id)")
    }

    init(_ account: Account) { id = account.id; name = account.displayName }
}

struct VillageQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [VillageEntity] {
        let archive = try SharedArchive.file().load()
        return identifiers.compactMap { id in archive.accounts.first(where: { $0.id == id }).map(VillageEntity.init) }
    }

    func suggestedEntities() async throws -> [VillageEntity] {
        try SharedArchive.file().load().accounts.map(VillageEntity.init)
    }

    func defaultResult() async -> VillageEntity? {
        guard let archive = try? SharedArchive.file().load(),
              let account = archive.widgetAccount(boundTo: nil) else { return nil }
        return VillageEntity(account)
    }
}

struct AccountIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Village account"
    static var description = IntentDescription("Choose the village shown by this widget.")
    @Parameter(title: "Account") var account: VillageEntity?
}
