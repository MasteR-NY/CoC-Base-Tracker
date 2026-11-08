import SwiftUI
import UserNotifications
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var store: TrackerStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("finishReminders") private var finishReminders = false
    @State private var notificationStatus = "Checking permission…"
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Notifications") {
                    Toggle("Upgrade finish reminders", isOn: $finishReminders)
                        .onChange(of: finishReminders) { _, enabled in
                            Task { await changeReminders(enabled) }
                        }
                    Text(notificationStatus).font(.caption).foregroundStyle(.secondary)
                    Button("Open notification settings") {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    Text("Schedules the next 60 upcoming upgrade finishes across your accounts. Opening the app replenishes the schedule. Alerts are estimates and delivery depends on your device settings.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Home Screen widgets") {
                    Text("Touch and hold your Home Screen, choose Add Widget, and search for CoC Base Tracker. Add a size, then touch and hold the widget → Edit Widget → Account.")
                    Text("Each widget can show a different account. Tap a widget to open that village. Apple controls widget refresh timing; countdown text uses the system timer.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Import & privacy") {
                    Text("Copy a fresh village export after starting, cancelling, or finishing upgrades early, or changing potions and helper assignments. Imports require Paste or a file; there is no background clipboard monitoring.")
                    Text("Account names, tags and timers are stored in this app’s shared container on this device. This version does not sync accounts with Android or upload exports.")
                    Text("Removing an account removes its saved timers and pending reminders. Widgets bound to it will ask you to select an account.")
                }
                Section("About") {
                    Text("CoC Base Tracker · Apple preview 0.1")
                    Text("For iPhone and iPad running iOS / iPadOS 17 or later.")
                    Text("This first Apple version includes accounts, imports, upgrade timers, boost/helper estimates, reminders and widgets. Android’s planner, collection views and updater are not included yet.")
                        .font(.footnote).foregroundStyle(.secondary)
                    Text("Unofficial fan companion. Not affiliated with or endorsed by Supercell. Clash of Clans and game names belong to Supercell.")
                        .font(.caption).foregroundStyle(.secondary)
                    if let url = Bundle.main.url(forResource: "THIRD-PARTY-NOTICES", withExtension: "txt"),
                       let notice = try? String(contentsOf: url, encoding: .utf8) {
                        NavigationLink("Data credits & licenses") { ScrollView { Text(notice).font(.caption).padding() }.navigationTitle("Credits") }
                    }
                }
            }
            .navigationTitle("Settings & help")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task { await updateStatus() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await updateStatus() } }
            }
            .alert("Notifications", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }

    @MainActor private func changeReminders(_ enabled: Bool) async {
        if enabled {
            do {
                let allowed = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
                // The user may have switched the toggle off while the prompt was open.
                if !allowed { finishReminders = false }
            } catch { self.error = error.localizedDescription; finishReminders = false }
        }
        store.refreshReminders()
        await updateStatus()
    }

    @MainActor private func updateStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral: notificationStatus = "Notifications allowed"
        case .denied: notificationStatus = "Notifications blocked. Enable them in Settings to receive reminders."
        case .notDetermined: notificationStatus = "Permission is requested when you enable reminders."
        @unknown default: notificationStatus = "Check notification permissions in Settings."
        }
    }
}
