import SwiftUI
import YeonsikShared

struct ContentView: View {
    @EnvironmentObject private var store: WorkoutStore
    @AppStorage(IosDisplaySettings.shared.themeKey) private var themeID = DisplayTheme.dark.id

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
        .preferredColorScheme(colorScheme)
    }

    private var colorScheme: ColorScheme? {
        let theme = DisplayPreferences.shared.theme(stored: themeID)
        if theme == .system { return nil }
        return theme == .dark ? .dark : .light
    }
}

private struct WorkoutTabs: View {
    private enum Route: Hashable { case strength, routine(UUID), active }

    @EnvironmentObject private var store: WorkoutStore
    @State private var tab = ProductTab.home
    @State private var path: [Route] = []
    @State private var recordsPath: [UUID] = []
    @State private var rootVisits: [String: Int] = [:]
    @State private var creatingRoutine = false
    @State private var homeWeight = false
    @State private var fitnessWeight = false

    var body: some View {
        TabView(selection: Binding(get: { tab.id }, set: { id in
            if let destination = ProductTab.companion.fromId(id: id) { select(destination) }
        })) {
            ForEach(ProductTab.companion.ordered, id: \.id) { destination in
                root(destination)
                    .id(rootVisits[destination.id, default: 0])
                    .tabItem { Label(destination.label, systemImage: icon(destination)) }
                    .tag(destination.id)
            }
        }
    }

    private func select(_ destination: ProductTab) {
        let inChild = (tab == .fitness && !path.isEmpty) || (tab == .records && !recordsPath.isEmpty)
        if destination.needsRootChange(currentRoot: inChild ? nil : tab) {
            if destination == .fitness { path = [] }
            if destination == .records { recordsPath = [] }
        }
        rootVisits[destination.id, default: 0] += 1
        tab = destination
    }

    private func icon(_ destination: ProductTab) -> String {
        if destination == .home { return "house" }
        if destination == .fitness { return "dumbbell" }
        if destination == .records { return "calendar" }
        return "gearshape"
    }

    @ViewBuilder private func root(_ destination: ProductTab) -> some View {
        if destination == .home {
            NavigationStack {
                List {
                    if let active = store.data?.inProgress {
                        Button {
                            select(.fitness)
                            path = [.active]
                        } label: { LabeledContent("진행 중 운동 계속", value: active.title) }
                    }
                    Button("무산소") {
                        select(.fitness)
                        path = [.strength]
                    }
                    Button("체중") { homeWeight = true }
                }
                .navigationTitle(destination.label)
                .sheet(isPresented: $homeWeight) { BodyView(openToday: true) { homeWeight = false } }
            }
        } else if destination == .fitness {
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

                    Section("운동 기록") {
                        NavigationLink("무산소 운동", value: Route.strength)
                    }
                    Section("생활 기록") {
                        Button("체중 기록") { fitnessWeight = true }
                    }
                }
                .navigationTitle(destination.label)
                .sheet(isPresented: $fitnessWeight) { BodyView(openToday: true) { fitnessWeight = false } }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .strength:
                        strengthRoutines
                    case .routine(let id):
                        RoutineView(routineID: id) { path = [.strength, .active] }
                    case .active:
                        ActiveWorkoutView { completed in
                            path = []
                            if completed { select(.records) }
                        }
                    }
                }
            }
        } else if destination == .records {
            NavigationStack(path: $recordsPath) {
                WorkoutHistoryView()
                    .navigationDestination(for: UUID.self) { id in
                        if let workout = store.data?.history.first(where: { $0.id == id }) {
                            WorkoutHistoryDetail(workout: workout)
                        }
                    }
            }
        } else {
            NavigationStack { DisplaySettingsView().navigationTitle(destination.label) }
        }
    }

    private var strengthRoutines: some View {
        List {
            Section("내 루틴") {
                let routines = store.data?.routines ?? []
                if routines.isEmpty {
                    Text("더하기 버튼으로 첫 루틴을 만들어 보세요.").foregroundStyle(.secondary)
                }
                ForEach(routines) { routine in
                    NavigationLink(value: Route.routine(routine.id)) {
                        LabeledContent(routine.name, value: "\(routine.exercises.count)개 종목")
                    }
                    .accessibilityIdentifier("routine-\(routine.name)")
                }
            }
        }
        .navigationTitle("무산소 운동")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { creatingRoutine = true } label: { Label("루틴 만들기", systemImage: "plus") }
                    .accessibilityIdentifier("create-routine")
            }
        }
        .sheet(isPresented: $creatingRoutine) { RoutineEditor(routine: nil) }
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
    @EnvironmentObject private var store: WorkoutStore
    @StateObject private var editors = ExerciseEditorSessions()
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
                NavigationLink {
                    ExerciseSetEditor(workoutID: workout.id, exerciseID: exercise.id, host: editors.host(for: exercise.id))
                        .navigationTitle(exercise.name)
                        .navigationBarTitleDisplayMode(.inline)
                } label: {
                    LabeledContent(exercise.name) {
                        Text("\(exercise.sets.filter(\.isCompleted).count)/\(exercise.sets.count)세트")
                    }
                }
                .accessibilityIdentifier("exercise-\(exercise.name)")
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
        .confirmationDialog("진행 중 운동을 삭제할까요?", isPresented: $confirmingDiscard, titleVisibility: .visible) {
            Button("운동 삭제", role: .destructive) { endWorkout(id: workout.id, completed: false) }
        } message: {
            Text("이 운동에서 저장한 세트가 삭제됩니다.")
        }
        .workoutError($error)
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

private final class ExerciseEditorSessions: ObservableObject {
    private var editors: [UUID: IosSetEditorHost] = [:]

    func host(for id: UUID) -> IosSetEditorHost {
        if let existing = editors[id] { return existing }
        let editor = IosSetEditorHost(decimalSeparator: Locale.current.decimalSeparator ?? ".")
        editors[id] = editor
        return editor
    }
}

private struct ExerciseSetEditor: View {
    @EnvironmentObject private var store: WorkoutStore
    @State private var error: String?
    let workoutID: UUID
    let exerciseID: UUID
    let host: IosSetEditorHost

    var body: some View {
        if let exercise = store.data?.inProgress?.exercises.first(where: { $0.id == exerciseID }) {
            SetTableController(store: store, workoutID: workoutID, exercise: exercise, host: host)
                .task {
                    // A session saved by an earlier build may have no first row yet.
                    if exercise.sets.isEmpty {
                        do { try await store.addSet(workoutID: workoutID, exerciseID: exerciseID, onlyIfEmpty: true) }
                        catch { self.error = error.localizedDescription }
                    }
                }
                .workoutError($error)
        }
    }
}

private struct SetTableController: UIViewControllerRepresentable {
    let store: WorkoutStore
    let workoutID: UUID
    let exercise: LoggedExercise
    let host: IosSetEditorHost

    func makeCoordinator() -> Coordinator {
        Coordinator(store: store, workoutID: workoutID, exerciseID: exercise.id)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        host.controller(storage: context.coordinator)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        let rows = exercise.sets.map { set in
            let weight = set.inputWeight.map {
                $0.formatted(.number.grouping(.never).precision(.fractionLength(0...1)))
            } ?? ""
            return IosSetRow(id: set.id.uuidString, unit: set.unit.sharedUnit, weight: weight,
                reps: set.reps.map { String($0) } ?? "", rir: set.rir.map { String($0) } ?? "",
                completed: set.isCompleted, storedLoad: set.inputWeight.map { KotlinDouble(value: $0) },
                storedKg: set.weightKg.map { KotlinDouble(value: $0) })
        }
        host.update(rows: rows)
    }

    @MainActor final class Coordinator: NSObject, IosSetEditorStorage {
        let store: WorkoutStore
        let workoutID: UUID
        let exerciseID: UUID

        init(store: WorkoutStore, workoutID: UUID, exerciseID: UUID) {
            self.store = store
            self.workoutID = workoutID
            self.exerciseID = exerciseID
        }

        nonisolated func add(reply: IosSetWriteReply) {
            Task { @MainActor in
                do {
                    try await store.addSet(workoutID: workoutID, exerciseID: exerciseID)
                    reply.finish(error: nil)
                } catch { reply.finish(error: error.localizedDescription) }
            }
        }

        nonisolated func remove(id: String, reply: IosSetWriteReply) {
            Task { @MainActor in
                do {
                    guard let uuid = UUID(uuidString: id) else { throw WorkoutError.workoutUnavailable }
                    try await store.removeSet(id: uuid, workoutID: workoutID, exerciseID: exerciseID)
                    reply.finish(error: nil)
                } catch { reply.finish(error: error.localizedDescription) }
            }
        }

        nonisolated func save(id: String, entry: OrdinarySetEntry, completed: Bool, reply: IosSetWriteReply) {
            Task { @MainActor in
                do {
                    guard let uuid = UUID(uuidString: id) else { throw WorkoutError.workoutUnavailable }
                    let set = LoggedSet(id: uuid, unit: entry.unit == .lb ? .lb : .kg, entry: entry, completed: completed)
                    try await store.saveSet(set, workoutID: workoutID, exerciseID: exerciseID)
                    reply.finish(error: nil)
                } catch { reply.finish(error: error.localizedDescription) }
            }
        }
    }
}

private struct WorkoutHistoryView: View {
    private enum BodyEntry: String, Identifiable {
        case history, today
        var id: String { rawValue }
    }
    @EnvironmentObject private var store: WorkoutStore
    @State private var showDailyWorkout = false
    @State private var showSyncAccount = false
    @State private var choosingRecordKind = false
    @State private var bodyEntry: BodyEntry?

    var body: some View {
        List {
            Button { choosingRecordKind.toggle() } label: {
                HStack {
                    Text("기록 추가")
                    Spacer()
                    Image(systemName: choosingRecordKind ? "chevron.up" : "chevron.down")
                        .imageScale(.small).accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
            }
            .accessibilityIdentifier("records-add")
            .accessibilityValue(choosingRecordKind ? "펼쳐짐" : "접힘")
            if choosingRecordKind {
                Button("체중") {
                    choosingRecordKind = false
                    bodyEntry = .today
                }
                .accessibilityIdentifier("records-add-weight")
            }
            Button("체중 기록") { bodyEntry = .history }
            let history = store.data?.history ?? []
            if history.isEmpty {
                Text("완료한 운동이 여기에 표시됩니다.").foregroundStyle(.secondary)
            }
            ForEach(history) { workout in
                NavigationLink(value: workout.id) {
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
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("동기화 계정") { showSyncAccount = true }
            }
            ToolbarItem(placement: .primaryAction) {
                Button("하루 운동 기록") { showDailyWorkout = true }
            }
        }
        .sheet(isPresented: $showDailyWorkout) {
            DailyWorkoutView(history: store.data?.history ?? [])
        }
        .sheet(isPresented: $showSyncAccount) { WorkoutCloudAccountView() }
        .sheet(item: $bodyEntry) { entry in
            NavigationStack {
                BodyView(openToday: entry == .today) { bodyEntry = nil }
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("닫기") { bodyEntry = nil }
                        }
                    }
            }
        }
    }
}

private struct WorkoutHistoryDetail: View {
    let workout: LoggedWorkout

    var body: some View {
        List {
            LabeledContent("완료 시간", value: (workout.completedAt ?? workout.startedAt).formatted())
            ForEach(workout.exercises.filter { $0.sets.contains(where: \.isCompleted) }) { exercise in
                Section(exercise.name) {
                    ForEach(Array(exercise.sets.enumerated()).filter { $0.element.isCompleted }, id: \.element.id) { index, set in
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
            Text([set.inputWeight.map { "\($0.formatted()) \(set.unit.rawValue)" },
                  set.reps.map { "\($0)회" }].compactMap { $0 }.joined(separator: " · "))
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
