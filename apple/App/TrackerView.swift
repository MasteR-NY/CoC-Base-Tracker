import SwiftUI
import TrackerCore

struct TrackerView: View {
    @EnvironmentObject private var store: TrackerStore
    @State private var importing = false
    @State private var settings = false
    @State private var renaming = false
    @State private var removing = false
    @State private var nickname = ""
    @State private var editingTag: String?
    @State private var column: NavigationSplitViewColumn = .sidebar

    private var account: Account? { store.archive.widgetAccount(boundTo: store.archive.selectedTag) }

    var body: some View {
        NavigationSplitView(preferredCompactColumn: $column) {
            List {
                Section("Your villages") {
                    ForEach(store.archive.accounts) { account in
                        Button {
                            store.select(account.id)
                            column = .detail
                        } label: {
                            HStack {
                                Label {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(account.displayName).font(.headline)
                                        Text(account.id).font(.caption).foregroundStyle(.secondary)
                                    }
                                } icon: {
                                    Image(systemName: "house.fill").foregroundStyle(.orange)
                                }
                                Spacer()
                                if account.id == store.archive.selectedTag {
                                    Image(systemName: "checkmark").foregroundStyle(.orange)
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }
                }
                Section {
                    Button { importing = true } label: { Label("Import village", systemImage: "plus.circle.fill") }
                        .disabled(store.storageError != nil)
                    Button { settings = true } label: { Label("Settings & help", systemImage: "gearshape") }
                }
            }
            .navigationTitle("Base Tracker")
            .overlay {
                if let error = store.storageError {
                    ContentUnavailableView {
                        Label("Storage unavailable", systemImage: "externaldrive.badge.exclamationmark")
                    } description: { Text(error) } actions: { Button("Retry") { store.reload() } }
                }
            }
        } detail: {
            if let account {
                VillageDetail(account: account)
                    .navigationTitle(account.displayName)
                    .toolbar {
                        ToolbarItemGroup(placement: .topBarTrailing) {
                            Button { importing = true } label: { Label("Import", systemImage: "arrow.triangle.2.circlepath") }
                            Menu {
                                Button("Rename account", systemImage: "pencil") {
                                    editingTag = account.id
                                    nickname = account.nickname
                                    renaming = true
                                }
                                Button(role: .destructive) {
                                    editingTag = account.id
                                    removing = true
                                } label: { Label("Remove account", systemImage: "trash") }
                                Button("Settings & help", systemImage: "gearshape") { settings = true }
                            } label: { Label("Account options", systemImage: "ellipsis.circle") }
                        }
                    }
            } else {
                ContentUnavailableView {
                    Label("Your village, at a glance", systemImage: "house.and.flag.fill")
                } description: {
                    Text("Copy a village export in Clash of Clans, then import it here to track upgrades and add a Home Screen widget.")
                } actions: {
                    Button("Import village") { importing = true }.buttonStyle(.borderedProminent)
                        .disabled(store.storageError != nil)
                }
            }
        }
        .sheet(isPresented: $importing) {
            ImportView { text in
                let problem = store.importText(text)
                if problem == nil { column = .detail }
                return problem
            }
        }
        .sheet(isPresented: $settings) { SettingsView() }
        .alert("Account name", isPresented: $renaming) {
            TextField("Nickname (optional)", text: $nickname)
            Button("Save") { if let tag = editingTag { store.rename(tag, to: nickname) } }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Leave blank to use the exported name or player tag.") }
        .confirmationDialog("Remove this account and its saved timers?", isPresented: $removing, titleVisibility: .visible) {
            Button("Remove account", role: .destructive) { if let tag = editingTag { store.remove(tag) } }
        }
        .alert("CoC Base Tracker", isPresented: Binding(get: { store.message != nil }, set: { if !$0 { store.message = nil } })) {
            Button("OK") { store.message = nil }
        } message: { Text(store.message ?? "") }
        .onAppear { if account != nil { column = .detail } }
        .onChange(of: store.archive.selectedTag) { _, tag in if tag != nil { column = .detail } }
    }
}

private struct VillageDetail: View {
    let account: Account

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let village = account.village
            let now = context.date
            List {
                Section {
                    HStack(alignment: .top) {
                        Image(systemName: "house.fill").font(.largeTitle).foregroundStyle(.orange)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(village.tag).font(.headline)
                            Text("Town Hall \(village.townHall) · \(village.freeBuilders(at: now))/\(village.builders) builders free")
                                .font(.subheadline)
                            Text("Exported \(village.exportedAt.formatted(date: .abbreviated, time: .shortened))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Text("Estimates use your last export. Import again after changing upgrades, potions, or helpers in the game.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                let active = village.effects.filter { $0.endsAt > now }
                if !active.isEmpty {
                    Section("Active boosts") {
                        ForEach(active) { effect in
                            HStack {
                                Label(effect.name, systemImage: "bolt.fill")
                                Spacer()
                                CountdownView(start: village.exportedAt, finish: effect.endsAt, now: now)
                                    .foregroundStyle(.orange)
                            }.font(.subheadline)
                        }
                    }
                }
                ForEach(UpgradeQueue.allCases, id: \.self) { queue in
                    let jobs = village.upgrades.filter { $0.queue == queue }
                    if !jobs.isEmpty {
                        Section(queue.label) {
                            ForEach(jobs) { job in
                                VStack(alignment: .leading, spacing: 7) {
                                    HStack(alignment: .firstTextBaseline) {
                                        Label(job.name, systemImage: queue.symbol).font(.headline)
                                        Spacer()
                                        Text("→ \(job.targetLevel)").font(.headline).foregroundStyle(.secondary)
                                    }
                                    if job.extraWorker {
                                        Label("Hired worker", systemImage: "person.badge.plus").font(.caption).foregroundStyle(.orange)
                                    }
                                    CountdownView(start: job.snapshotAt, finish: job.finishAt, now: now)
                                        .font(.title3.weight(.semibold))
                                    if job.finishAt > now {
                                        Text("Finishes \(job.finishAt.formatted(date: .abbreviated, time: .shortened))")
                                            .font(.caption).foregroundStyle(.secondary)
                                        let elapsed = job.elapsed(at: now)
                                        let speed = job.timing.speed(after: elapsed)
                                        if !job.timing.boosts.isEmpty || job.timing.helper != nil {
                                            Text("\(speed)× speed · \(DurationLabel.seconds(job.timing.remainingWork(after: elapsed))) work left")
                                                .font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .padding(.vertical, 5)
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                }
                if village.upgrades.isEmpty {
                    Section { Text("No active upgrades in this export.").foregroundStyle(.secondary) }
                }
                Section {
                    Label("Add a widget from your Home Screen, then touch and hold it and choose Edit Widget to select an account.", systemImage: "square.grid.2x2")
                        .font(.footnote).foregroundStyle(.secondary)
                    Text("A finished timer is an estimate; it does not change your saved in-game level.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }
}

enum DurationLabel {
    static func seconds(_ value: Int64) -> String {
        let seconds = max(0, value)
        let days = seconds / 86400
        let hours = seconds % 86400 / 3600
        let minutes = seconds % 3600 / 60
        return (days > 0 ? "\(days)d " : "") + "\(hours)h \(minutes)m \(seconds % 60)s"
    }
}
