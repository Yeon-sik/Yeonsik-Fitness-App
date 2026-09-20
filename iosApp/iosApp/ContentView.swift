import Foundation
import SwiftUI
import YeonsikShared

/// M6/M7 integration proof only. Production persistence and platform SDK adapters intentionally do not exist here.
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

                Section("Body vertical slice") {
                    NavigationLink("Open Body") {
                        BodyView()
                    }
                }

                Section("Adapter boundary") {
                    Text("SwiftUI → shared Body API → in-memory iOS adapter")
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
