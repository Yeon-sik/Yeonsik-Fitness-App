import SwiftUI
import YeonsikShared

struct WorkoutCompletionSummaryView: View {
    @EnvironmentObject private var store: WorkoutStore
    @State private var message: String?
    let workout: LoggedWorkout
    let onExercise: (UUID) -> Void
    let onRecords: () -> Void

    var body: some View {
        if let finished = workout.completedAt {
            SummaryController(workout: workout, finished: finished, history: store.data?.history ?? [],
                              onExercise: onExercise, onRecords: onRecords, onSaveRoutine: saveRoutine)
                .id(workout.id)
                .navigationTitle("운동 요약")
                .navigationBarTitleDisplayMode(.inline)
                .alert("루틴으로 저장", isPresented: Binding(
                    get: { message != nil }, set: { if !$0 { message = nil } }
                )) { Button("확인") {} } message: { Text(message ?? "") }
        }
    }

    private func saveRoutine(_ name: String) {
        guard !store.isWorking else { return }
        Task {
            do {
                try await store.saveRoutine(WorkoutRoutine(name: name, exercises: workout.exercises.map {
                    RoutineExercise(name: $0.name)
                }))
                message = "루틴을 저장했습니다."
            } catch { message = error.localizedDescription }
        }
    }
}

private struct SummaryController: UIViewControllerRepresentable {
    let workout: LoggedWorkout
    let finished: Date
    let history: [LoggedWorkout]
    let onExercise: (UUID) -> Void
    let onRecords: () -> Void
    let onSaveRoutine: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let iso = ISO8601DateFormatter()
        let day = DateFormatter()
        day.locale = Locale(identifier: "en_US_POSIX")
        day.calendar = Calendar(identifier: .gregorian)
        day.dateFormat = "yyyy-MM-dd"
        let earlier = history.drop(while: { $0.id != workout.id }).dropFirst()
        let recent = earlier.prefix(5).reversed().map { item in
            WorkoutVolumePoint(date: day.string(from: item.startedAt), label: item.title, volumeKg: item.volumeKg)
        }
        let previous = earlier.first(where: { $0.routineID == workout.routineID }).map { item in
            IosWorkoutSummaryKt.iosPreviousRoutine(date: day.string(from: item.startedAt), recordId: item.id.uuidString,
                                                  exercises: item.summaryExercises)
        }
        let snapshot = IosWorkoutSummaryKt.iosCompletedSnapshot(
            id: workout.id.uuidString, routineId: workout.routineID.uuidString, title: workout.title,
            startedAt: iso.string(from: workout.startedAt), completedAt: iso.string(from: finished),
            durationSeconds: Int32(clamping: max(0, Int(finished.timeIntervalSince(workout.startedAt)))),
            exercises: workout.summaryExercises, recent: recent, previous: previous)
        return IosWorkoutSummaryKt.makeWorkoutSummaryViewController(session: snapshot,
            onExercise: { if let id = UUID(uuidString: $0) { onExercise(id) } },
            onRecords: onRecords, onSaveRoutine: onSaveRoutine)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

private extension LoggedWorkout {
    var summaryExercises: [WorkoutSessionExercise] {
        exercises.enumerated().map { order, exercise in
            let rows = exercise.sets.enumerated().map { index, set in
                IosWorkoutSummaryKt.iosSummarySet(id: set.id.uuidString, index: Int32(index + 1),
                    weightKg: set.weightKg.map { KotlinDouble(value: $0) },
                    inputWeight: set.inputWeight.map { KotlinDouble(value: $0) }, unit: set.unit.sharedUnit,
                    reps: set.reps.map { KotlinInt(value: $0) }, rir: set.rir.map { KotlinInt(value: Int32(clamping: $0)) },
                    completed: set.isCompleted)
            }
            return IosWorkoutSummaryKt.iosSummaryExercise(id: exercise.id.uuidString,
                exerciseId: exercise.catalog?.id ?? (exercise.routineExerciseID ?? exercise.id).uuidString,
                order: Int32(order + 1), name: exercise.name,
                bodyPart: exercise.catalog?.bodyPart, primaryPart: exercise.catalog?.primaryPart,
                equipment: exercise.catalog?.equipment, sets: rows)
        }
    }
}
