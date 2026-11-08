import SwiftUI

@main
struct CoCBaseTrackerApp: App {
    @StateObject private var store = TrackerStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            TrackerView()
                .environmentObject(store)
                .tint(.orange)
                .onOpenURL { url in
                    guard url.scheme == "cocbasetracker", url.host == "account",
                          let tag = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                            .queryItems?.first(where: { $0.name == "tag" })?.value else { return }
                    store.select(tag)
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.refreshReminders() }
        }
    }
}
