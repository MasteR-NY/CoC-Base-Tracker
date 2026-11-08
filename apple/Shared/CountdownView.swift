import SwiftUI

/// A native interval stops at zero instead of counting upwards after completion.
struct CountdownView: View {
    let start: Date
    let finish: Date
    let now: Date

    var body: some View {
        if finish <= now {
            Label("Finished", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
        } else {
            Text(timerInterval: min(start, now)...finish, countsDown: true)
                .monospacedDigit()
                .contentTransition(.numericText(countsDown: true))
                .accessibilityLabel("Time remaining")
        }
    }
}
