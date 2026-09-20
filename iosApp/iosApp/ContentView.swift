import Foundation
import SwiftUI
import YeonsikShared

/// M6 integration proof only. Persistence and platform adapters intentionally do not exist here.
struct ContentView: View {
    private let scope = AccountScope(ownerId: "ios-proof-owner")
    private let estimatedOneRepMaxKg = WorkoutPerformanceCalculator.shared.epleyE1rm(
        loadKg: 80.0,
        reps: 5
    )
    private let activity = CardioActivityType.running

    var body: some View {
        NavigationStack {
            List {
                Section("KMP shared integration") {
                    LabeledContent("Account scope", value: scope.ownerId)
                    LabeledContent(
                        "Epley 1RM",
                        value: String(format: "%.1f kg", estimatedOneRepMaxKg)
                    )
                    LabeledContent("Cardio activity", value: String(describing: activity))
                }

                Section("Adapter boundary") {
                    Text("SwiftUI → shared Domain/API → future iOS adapter")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Yeonsik Fitness")
        }
    }
}

#Preview {
    ContentView()
}
