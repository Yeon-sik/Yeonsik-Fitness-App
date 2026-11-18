import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: WorkoutStore

    var body: some View {
        Group {
            if store.data != nil {
                WorkoutTabs()
            } else if let error = store.loadError {
                VStack(spacing: 16) {
                    Text(error)
                    Button("다시 불러오기") { Task { await store.load() } }
                }
                .padding()
            } else {
                ProgressView("운동 기록 불러오는 중")
            }
        }
        .task { await store.load() }
    }
}

private struct WorkoutTabs: View {
    private enum Tab { case workout, history, body }
    private enum Route: Hashable { case routine(UUID), active }

    @EnvironmentObject private var store: WorkoutStore
    @State private var tab = Tab.workout
    @State private var path: [Route] = []
    @State private var creatingRoutine = false

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $path) {
                List {
                    if let active = store.data?.inProgress {
                        Section("진행 중") {
                            NavigationLink(value: Route.active) {
                                LabeledContent(active.title, value: "\(active.setCount)세트")
                            }
                            .accessibilityIdentifier("resume-workout")
                        }
                    }

                    Section("내 루틴") {
                        let routines = store.data?.routines ?? []
                        if routines.isEmpty {
                            Text("더하기 버튼으로 첫 루틴을 만들어 보세요.")
                                .foregroundStyle(.secondary)
                        }
                        ForEach(routines) { routine in
                            NavigationLink(value: Route.routine(routine.id)) {
                                LabeledContent(routine.name, value: "\(routine.exercises.count)개 종목")
                            }
                            .accessibilityIdentifier("routine-\(routine.name)")
                        }
                    }

                }
                .navigationTitle("운동")
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button { creatingRoutine = true } label: {
                            Label("루틴 만들기", systemImage: "plus")
                        }
                        .accessibilityIdentifier("create-routine")
                    }
                }
                .sheet(isPresented: $creatingRoutine) { RoutineEditor(routine: nil) }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .routine(let id):
                        RoutineView(routineID: id) { path = [.active] }
                    case .active:
                        ActiveWorkoutView { completed in
                            path = []
                            if completed { tab = .history }
                        }
                    }
                }
            }
            .tabItem { Label("운동", systemImage: "dumbbell") }
            .tag(Tab.workout)

            NavigationStack { WorkoutHistoryView() }
                .tabItem { Label("기록", systemImage: "clock") }
                .tag(Tab.history)

            BodyView()
                .tabItem { Label("체중", systemImage: "scalemass") }
                .tag(Tab.body)
        }
    }
}

private struct RoutineView: View {
    @EnvironmentObject private var store: WorkoutStore
    @State private var editing = false
    @State private var error: String?
    let routineID: UUID
    let openWorkout: () -> Void

    var body: some View {
        if let routine = store.data?.routines.first(where: { $0.id == routineID }) {
            routineList(routine)
        }
    }

    private func routineList(_ routine: WorkoutRoutine) -> some View {
        List {
            Section("종목") {
                ForEach(routine.exercises) { Text($0.name) }
            }
            Section {
                if store.data?.inProgress != nil {
                    Button("진행 중 운동 계속", action: openWorkout)
                } else {
                    Button("운동 시작", action: start)
                        .accessibilityIdentifier("start-workout")
                }
            }
        }
        .navigationTitle(routine.name)
        .toolbar {
            ToolbarItem(placement: .primaryAction) { Button("편집") { editing = true } }
        }
        .sheet(isPresented: $editing) { RoutineEditor(routine: routine) }
        .workoutError($error)
    }

    private func start() {
        Task {
            do {
                try await store.startWorkout(routineID: routineID)
                openWorkout()
            } catch { self.error = error.localizedDescription }
        }
    }
}

private struct RoutineEditor: View {
    @EnvironmentObject private var store: WorkoutStore
    @Environment(\.dismiss) private var dismiss
    @State private var draft: WorkoutRoutine
    @State private var error: String?
    let routine: WorkoutRoutine?

    init(routine: WorkoutRoutine?) {
        self.routine = routine
        _draft = State(initialValue: routine ?? WorkoutRoutine(name: "", exercises: [RoutineExercise(name: "")]))
    }

    var body: some View {
        NavigationStack {
            Form {
                if let error { Text(error).foregroundStyle(.red) }
                Section("루틴") {
                    TextField("루틴 이름", text: $draft.name)
                        .accessibilityIdentifier("routine-name")
                }
                Section("종목") {
                    ForEach($draft.exercises) { $exercise in
                        TextField("종목 이름", text: $exercise.name)
                            .accessibilityIdentifier("exercise-name")
                    }
                    .onDelete { draft.exercises.remove(atOffsets: $0) }
                    Button {
                        draft.exercises.append(RoutineExercise(name: ""))
                    } label: {
                        Label("종목 추가", systemImage: "plus")
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(routine == nil ? "루틴 만들기" : "루틴 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }.disabled(store.isWorking)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장", action: save)
                        .disabled(store.isWorking)
                        .accessibilityIdentifier("save-routine")
                }
            }
        }
        .interactiveDismissDisabled(store.isWorking)
    }

    private func save() {
        Task {
            do {
                try await store.saveRoutine(draft)
                dismiss()
            } catch { self.error = error.localizedDescription }
        }
    }
}

private struct ActiveWorkoutView: View {
    private struct SetSelection: Identifiable {
        let exercise: LoggedExercise
        let existingSet: LoggedSet?
        var id: UUID { existingSet?.id ?? exercise.id }
    }

    @EnvironmentObject private var store: WorkoutStore
    @State private var selection: SetSelection?
    @State private var confirmingDiscard = false
    @State private var error: String?
    let leaveWorkout: (Bool) -> Void

    var body: some View {
        if let workout = store.data?.inProgress {
            exerciseList(workout)
        }
    }

    private func exerciseList(_ workout: LoggedWorkout) -> some View {
        List {
            ForEach(workout.exercises) { exercise in
                Section(exercise.name) {
                    ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { index, set in
                        Button {
                            selection = SetSelection(exercise: exercise, existingSet: set)
                        } label: {
                            SetRow(number: index + 1, set: set)
                        }
                        .foregroundStyle(.primary)
                        .swipeActions(allowsFullSwipe: false) {
                            Button("삭제") { remove(set, from: exercise, workoutID: workout.id) }.tint(.red)
                        }
                    }
                    Button {
                        selection = SetSelection(exercise: exercise, existingSet: nil)
                    } label: {
                        Label("세트 추가", systemImage: "plus")
                    }
                    .accessibilityIdentifier("add-set")
                }
            }
            Section {
                Button("진행 중 운동 삭제", role: .destructive) { confirmingDiscard = true }
                    .disabled(store.isWorking)
            }
        }
        .navigationTitle(workout.title)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("운동 완료") { endWorkout(id: workout.id, completed: true) }
                    .disabled(store.isWorking || workout.setCount == 0)
                    .accessibilityIdentifier("finish-workout")
            }
        }
        .sheet(item: $selection) { item in
            SetEditor(workoutID: workout.id, exercise: item.exercise, set: item.existingSet)
        }
        .confirmationDialog("진행 중 운동을 삭제할까요?", isPresented: $confirmingDiscard, titleVisibility: .visible) {
            Button("운동 삭제", role: .destructive) { endWorkout(id: workout.id, completed: false) }
        } message: {
            Text("이 운동에서 저장한 세트가 삭제됩니다.")
        }
        .workoutError($error)
    }

    private func remove(_ set: LoggedSet, from exercise: LoggedExercise, workoutID: UUID) {
        Task {
            do {
                try await store.removeSet(id: set.id, workoutID: workoutID, exerciseID: exercise.id)
            } catch { self.error = error.localizedDescription }
        }
    }

    private func endWorkout(id: UUID, completed: Bool) {
        Task {
            do {
                if completed {
                    try await store.finishWorkout(id: id)
                } else {
                    try await store.discardWorkout(id: id)
                }
                leaveWorkout(completed)
            } catch { self.error = error.localizedDescription }
        }
    }
}

private struct SetEditor: View {
    @EnvironmentObject private var store: WorkoutStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    @State private var weight: String
    @State private var reps: String
    @State private var unit: WorkoutWeightUnit
    @State private var rir: Int?
    @State private var error: String?
    let workoutID: UUID
    let exercise: LoggedExercise
    let set: LoggedSet?

    init(workoutID: UUID, exercise: LoggedExercise, set: LoggedSet?) {
        self.workoutID = workoutID
        self.exercise = exercise
        self.set = set
        _weight = State(initialValue: set.map {
            String($0.inputWeight).replacingOccurrences(of: ".", with: Locale.current.decimalSeparator ?? ".")
        } ?? "")
        _reps = State(initialValue: set.map { String($0.reps) } ?? "")
        _unit = State(initialValue: set?.unit ?? .kg)
        _rir = State(initialValue: set?.rir)
    }

    var body: some View {
        NavigationStack {
            Form {
                if let error { Text(error).foregroundStyle(.red) }
                Section(exercise.name) {
                    Picker("중량 단위", selection: $unit) {
                        ForEach(WorkoutWeightUnit.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    TextField("중량", text: $weight, prompt: Text("중량 (\(unit.rawValue))"))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("set-weight")
                    TextField("횟수", text: $reps)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("set-reps")
                    Picker("RIR", selection: $rir) {
                        Text("미입력").tag(nil as Int?)
                        ForEach(0...5, id: \.self) { Text("\($0)").tag(Optional($0)) }
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("set-rir")
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(set == nil ? "세트 추가" : "세트 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }.disabled(store.isWorking)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장", action: save)
                        .disabled(store.isWorking)
                        .accessibilityIdentifier("save-set")
                }
            }
        }
        .interactiveDismissDisabled(store.isWorking)
    }

    private func save() {
        Task {
            do {
                let rawWeight = weight.trimmingCharacters(in: .whitespacesAndNewlines)
                    .replacingOccurrences(of: locale.decimalSeparator ?? ".", with: ".")
                guard let inputWeight = Double(rawWeight),
                      let count = Int32(reps.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                    throw WorkoutError.invalidSet
                }
                let logged = try LoggedSet(id: set?.id ?? UUID(), inputWeight: inputWeight, unit: unit, reps: count, rir: rir)
                try await store.saveSet(logged, workoutID: workoutID, exerciseID: exercise.id)
                dismiss()
            } catch { self.error = error.localizedDescription }
        }
    }
}

private struct WorkoutHistoryView: View {
    @EnvironmentObject private var store: WorkoutStore

    var body: some View {
        List {
            let history = store.data?.history ?? []
            if history.isEmpty {
                Text("완료한 운동이 여기에 표시됩니다.").foregroundStyle(.secondary)
            }
            ForEach(history) { workout in
                NavigationLink {
                    WorkoutHistoryDetail(workout: workout)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(workout.title)
                        Text("\((workout.completedAt ?? workout.startedAt).formatted(date: .abbreviated, time: .shortened)) · \(workout.setCount)세트")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityIdentifier("history-\(workout.title)")
            }
        }
        .navigationTitle("기록")
    }
}

private struct WorkoutHistoryDetail: View {
    let workout: LoggedWorkout

    var body: some View {
        List {
            LabeledContent("완료 시간", value: (workout.completedAt ?? workout.startedAt).formatted())
            ForEach(workout.exercises.filter { !$0.sets.isEmpty }) { exercise in
                Section(exercise.name) {
                    ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { index, set in
                        SetRow(number: index + 1, set: set)
                    }
                }
            }
        }
        .navigationTitle(workout.title)
    }
}

private struct SetRow: View {
    let number: Int
    let set: LoggedSet

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("세트 \(number)").font(.subheadline).foregroundStyle(.secondary)
            Text("\(set.inputWeight.formatted()) \(set.unit.rawValue) · \(set.reps)회")
                .monospacedDigit()
            if let rir = set.rir { Text("RIR \(rir)").font(.subheadline) }
            if let estimate = set.estimatedOneRepMaxKg {
                Text("추정 1RM \(estimate.formatted(.number.precision(.fractionLength(1)))) kg")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private extension View {
    func workoutError(_ error: Binding<String?>) -> some View {
        alert("운동 기록", isPresented: Binding(
            get: { error.wrappedValue != nil },
            set: { if !$0 { error.wrappedValue = nil } }
        )) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(error.wrappedValue ?? "")
        }
    }
}
