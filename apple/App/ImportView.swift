import SwiftUI
import UniformTypeIdentifiers
import TrackerCore

struct ImportView: View {
    let onImport: (String) -> String?
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var selectingFile = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("In Clash of Clans, open Settings → More Settings → Export village data, then copy the export.")
                    Text("Tap Paste below or paste into the text field. Each player tag is saved as a separate account.")
                        .foregroundStyle(.secondary)
                }
                Section("Village export") {
                    PasteButton(payloadType: String.self) { values in
                        if let value = values.first { accept(value) }
                    }
                    TextEditor(text: $text)
                        .font(.system(.caption, design: .monospaced))
                        .frame(minHeight: 180)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .accessibilityLabel("Village export JSON")
                        .onChange(of: text) { _, value in
                            if value.utf8.count > ExportParser.maximumBytes {
                                text = ""
                                error = "Export is too large (maximum 1 MB)."
                            }
                        }
                    Button("Choose JSON or text file", systemImage: "doc") { selectingFile = true }
                }
                Section {
                    Text("Exports stay on this device. Paste happens only when you request it. Older snapshots cannot replace newer saved data.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Import village")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import") {
                        if let problem = onImport(text) { error = problem } else { dismiss() }
                    }
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .fileImporter(isPresented: $selectingFile, allowedContentTypes: [.json, .plainText]) { result in
                do {
                    let url = try result.get()
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let handle = try FileHandle(forReadingFrom: url)
                    defer { try? handle.close() }
                    let data = try handle.read(upToCount: ExportParser.maximumBytes + 1) ?? Data()
                    guard data.count <= ExportParser.maximumBytes,
                          let value = String(data: data, encoding: .utf8) else {
                        throw ExportError.invalid("Choose a UTF-8 village export no larger than 1 MB.")
                    }
                    accept(value)
                } catch { self.error = error.localizedDescription }
            }
            .alert("Import problem", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }

    private func accept(_ value: String) {
        if value.utf8.count > ExportParser.maximumBytes { error = "Export is too large (maximum 1 MB)." }
        else { text = value }
    }
}
