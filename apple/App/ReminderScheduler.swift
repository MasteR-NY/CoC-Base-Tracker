import Foundation
import UserNotifications
import TrackerCore

@MainActor
final class ReminderScheduler {
    private var generation = UUID()
    private let center = UNUserNotificationCenter.current()
    private let prefix = "tracker.finish."

    /// Replace only this app's finish reminders. Generation-specific IDs prevent
    /// a suspended old import from restoring alerts after removal or disabling.
    func replace(accounts: [Account], enabled: Bool) async {
        let token = UUID()
        generation = token
        let pending = await center.pendingNotificationRequests()
        guard generation == token else { return }
        center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(prefix) })
        guard enabled else { return }
        let settings = await center.notificationSettings()
        guard generation == token,
              settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        let now = Date()
        let jobs = accounts.flatMap { account in account.village.upgrades.map { (account, $0) } }
            .filter { $0.1.finishAt.timeIntervalSince(now) > 1 }
            .sorted { $0.1.finishAt < $1.1.finishAt }
        // Leave room below the system's pending local-notification limit.
        for (index, pair) in jobs.prefix(60).enumerated() {
            guard generation == token else { return }
            let (account, job) = pair
            let remaining = job.finishAt.timeIntervalSinceNow
            guard remaining > 1 else { continue }
            let content = UNMutableNotificationContent()
            content.title = "\(job.name) → \(job.targetLevel)"
            content.body = "Estimated upgrade complete · \(account.displayName)"
            content.sound = .default
            content.userInfo = ["accountTag": account.id]
            let id = "\(prefix)\(token.uuidString).\(index)"
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: remaining, repeats: false)
            do {
                try await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            } catch { /* Permission and delivery status remain visible in Settings. */ }
            if generation != token { center.removePendingNotificationRequests(withIdentifiers: [id]); return }
        }
    }
}
