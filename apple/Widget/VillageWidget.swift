import SwiftUI
import WidgetKit
import TrackerCore

struct VillageEntry: TimelineEntry {
    let date: Date
    let account: Account?
    let message: String?
}

struct VillageProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> VillageEntry {
        VillageEntry(date: Date(), account: nil, message: "Your upgrade timers")
    }

    func snapshot(for configuration: AccountIntent, in context: Context) async -> VillageEntry {
        entry(for: configuration, at: Date())
    }

    func timeline(for configuration: AccountIntent, in context: Context) async -> Timeline<VillageEntry> {
        let now = Date()
        let initial = entry(for: configuration, at: now)
        // Entries change row ordering/finished labels at real deadlines. Countdown
        // Text updates are rendered by the system, with no per-second reloads.
        let dates = Set((initial.account?.village.upgrades.map(\.finishAt) ?? []) +
                        (initial.account?.village.effects.map(\.endsAt) ?? []))
            .filter { $0 > now && $0 <= now.addingTimeInterval(24 * 3600) }.sorted().prefix(40)
        let entries = [initial] + dates.map { VillageEntry(date: $0, account: initial.account, message: initial.message) }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(3600)))
    }

    private func entry(for configuration: AccountIntent, at date: Date) -> VillageEntry {
        do {
            let archive = try SharedArchive.file().load()
            // No implicit fallback: a widget always binds to a chosen village.
            guard let tag = configuration.account?.id,
                  let account = archive.widgetAccount(boundTo: tag) else {
                return VillageEntry(date: date, account: nil,
                                    message: archive.accounts.isEmpty ? "Import a village in the app." : "Edit Widget to choose an account.")
            }
            return VillageEntry(date: date, account: account, message: nil)
        } catch {
            return VillageEntry(date: date, account: nil, message: "Open the app to check shared storage. If the device just restarted, unlock it first.")
        }
    }
}

struct VillageWidgetView: View {
    let entry: VillageEntry
    @Environment(\.widgetFamily) private var family

    private var rowLimit: Int {
        switch family {
        case .systemSmall: return 1
        case .systemMedium: return 2
        case .systemExtraLarge: return 9
        default: return 5
        }
    }

    private var accountURL: URL? {
        guard let account = entry.account else { return URL(string: "cocbasetracker://open") }
        var components = URLComponents()
        components.scheme = "cocbasetracker"
        components.host = "account"
        components.queryItems = [URLQueryItem(name: "tag", value: account.id)]
        return components.url
    }

    var body: some View {
        Group {
            if let account = entry.account {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "house.fill").foregroundStyle(.orange)
                        Text(account.displayName).font(.headline).lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    if family != .systemSmall {
                        Text("\(account.id) · TH \(account.village.townHall)")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                    let jobs = account.village.widgetUpgrades(at: entry.date)
                    if jobs.isEmpty {
                        Text("No upgrades in this export").font(.caption).foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(jobs.prefix(rowLimit))) { job in
                            HStack(alignment: .top, spacing: 7) {
                                Image(systemName: job.queue.symbol).foregroundStyle(.orange).font(.caption)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("\(job.name) → \(job.targetLevel)")
                                        .font(.caption.weight(.semibold)).lineLimit(1)
                                    CountdownView(start: job.snapshotAt, finish: job.finishAt, now: entry.date)
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    Spacer(minLength: 0)
                    Text("Last export \(account.village.exportedAt.formatted(date: .omitted, time: .shortened))")
                        .font(.system(size: 9)).foregroundStyle(.secondary).lineLimit(1)
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Base Tracker", systemImage: "house.fill").font(.headline).foregroundStyle(.orange)
                    Text(entry.message ?? "Import a village in the app.").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .containerBackground(for: .widget) { Color(.systemBackground) }
        .widgetURL(accountURL)
    }
}

@main
struct CoCBaseTrackerWidget: Widget {
    let kind = "CoCBaseTracker.Village"
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: AccountIntent.self, provider: VillageProvider()) { entry in
            VillageWidgetView(entry: entry)
        }
        .configurationDisplayName("Village upgrades")
        .description("Upgrade countdowns for one Clash of Clans account.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
    }
}
