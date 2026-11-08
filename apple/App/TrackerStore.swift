import SwiftUI
import TrackerCore
import WidgetKit

@MainActor
final class TrackerStore: ObservableObject {
    @Published private(set) var archive = AccountArchive()
    @Published private(set) var storageError: String?
    @Published var message: String?
    private let reminders = ReminderScheduler()

    init() { reload() }

    func reload() {
        do {
            archive = try SharedArchive.file().load()
            storageError = nil
            refreshReminders()
        } catch { storageError = error.localizedDescription }
    }

    /// Return an import problem to the sheet so invalid input stays editable.
    func importText(_ text: String) -> String? {
        do {
            let village = try ExportParser.parse(text)
            var draft = archive
            let outcome = draft.importVillage(village)
            guard outcome != .unchanged else {
                return "This export is already saved or is older than the saved snapshot. Copy a fresh export to update it."
            }
            try commit(draft)
            return nil
        } catch { return error.localizedDescription }
    }

    func select(_ tag: String) {
        guard archive.selectedTag != tag else { return }
        update { $0.select(tag) }
    }

    func rename(_ tag: String, to name: String) { update { $0.rename(tag, to: name) } }
    func remove(_ tag: String) { update { $0.remove(tag) } }

    private func update(_ change: (inout AccountArchive) -> Void) {
        do {
            var draft = archive
            change(&draft)
            try commit(draft)
        } catch { message = error.localizedDescription }
    }

    private func commit(_ draft: AccountArchive) throws {
        // Never overwrite an unreadable or unsupported archive with an empty one.
        guard storageError == nil else { throw ExportError.invalid(storageError!) }
        try SharedArchive.file().save(draft)
        archive = draft
        WidgetCenter.shared.reloadAllTimelines()
        refreshReminders()
    }

    func refreshReminders() {
        let accounts = archive.accounts
        let enabled = UserDefaults.standard.bool(forKey: "finishReminders")
        Task { await reminders.replace(accounts: accounts, enabled: enabled) }
    }
}
