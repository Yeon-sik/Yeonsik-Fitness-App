import CryptoKit
import Foundation
import SwiftUI
import YeonsikShared

struct RoutineExercise: Codable, Identifiable, Sendable {
    var id = UUID()
    var name: String
}

struct WorkoutRoutine: Codable, Identifiable, Sendable {
    var id = UUID()
    var name: String
    var exercises: [RoutineExercise]
}

enum WorkoutWeightUnit: String, CaseIterable, Codable, Sendable {
    case kg, lb

    var sharedUnit: MassUnit { self == .kg ? .kg : .lb }
}

struct LoggedSet: Codable, Identifiable, Sendable {
    let id: UUID
    let inputWeight: Double?
    let unit: WorkoutWeightUnit
    let weightKg: Double?
    let reps: Int32?
    let rir: Int?
    let isCompleted: Bool

    init(id: UUID = UUID(), unit: WorkoutWeightUnit, entry: OrdinarySetEntry? = nil, completed: Bool = false) {
        self.id = id
        self.unit = unit
        self.inputWeight = entry?.inputLoad?.doubleValue
        self.weightKg = entry?.weightKg?.doubleValue
        self.reps = entry?.reps?.int32Value
        self.rir = entry?.rir?.intValue
        self.isCompleted = completed
    }

    init(from decoder: Decoder) throws {
        let fields = try decoder.container(keyedBy: CodingKeys.self)
        id = try fields.decode(UUID.self, forKey: .id)
        unit = try fields.decode(WorkoutWeightUnit.self, forKey: .unit)
        rir = try fields.decodeIfPresent(Int.self, forKey: .rir)
        if (decoder.userInfo[WorkoutData.versionKey] as? Int ?? WorkoutData.currentVersion) < 3 {
            // Both previous formats only stored saved, completed sets with numeric weight and reps.
            inputWeight = try fields.decode(Double.self, forKey: .inputWeight)
            weightKg = try fields.decode(Double.self, forKey: .weightKg)
            reps = try fields.decode(Int32.self, forKey: .reps)
            isCompleted = true
        } else {
            inputWeight = try fields.decodeIfPresent(Double.self, forKey: .inputWeight)
            weightKg = try fields.decodeIfPresent(Double.self, forKey: .weightKg)
            reps = try fields.decodeIfPresent(Int32.self, forKey: .reps)
            isCompleted = try fields.decode(Bool.self, forKey: .isCompleted)
        }
    }

    var estimatedOneRepMaxKg: Double? {
        guard let weightKg, let reps else { return nil }
        let estimate = WorkoutPerformanceCalculator.shared.epleyE1rm(loadKg: weightKg, reps: reps)
        return estimate.isFinite && estimate > 0 ? estimate : nil
    }
}

struct LoggedExercise: Codable, Identifiable, Sendable {
    let id: UUID
    let routineExerciseID: UUID?
    let name: String
    var sets: [LoggedSet] = []

    // Version 1 reused the routine exercise UUID every time its routine was performed.
    // UUIDv5 gives copies of the same historical session the same occurrence ID.
    func migratingRoutineID(in sessionID: UUID) -> LoggedExercise {
        var namespace = sessionID.uuid
        var bytes = withUnsafeBytes(of: &namespace) { Data($0) }
        bytes.append(contentsOf: id.uuidString.lowercased().utf8)
        var digest = Array(Insecure.SHA1.hash(data: bytes).prefix(16))
        digest[6] = (digest[6] & 0x0f) | 0x50
        digest[8] = (digest[8] & 0x3f) | 0x80
        let occurrenceID = NSUUID(uuidBytes: digest) as UUID
        return LoggedExercise(id: occurrenceID, routineExerciseID: id, name: name, sets: sets)
    }
}

struct LoggedWorkout: Codable, Identifiable, Sendable {
    let id: UUID
    let routineID: UUID
    let title: String
    let startedAt: Date
    var completedAt: Date?
    var exercises: [LoggedExercise]

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets.filter(\.isCompleted).count } }
    var volumeKg: Double {
        exercises.flatMap(\.sets).filter(\.isCompleted).reduce(0) { $0 + ($1.weightKg ?? 0) * Double($1.reps ?? 0) }
    }
}

struct WorkoutData: Codable, Sendable {
    static let currentVersion = 3
    static let versionKey = CodingUserInfoKey(rawValue: "fitness.workout.schemaVersion")!
    var schemaVersion = currentVersion
    var routines: [WorkoutRoutine] = []
    var inProgress: LoggedWorkout?
    var history: [LoggedWorkout] = []

    mutating func migrateVersionOne() {
        func migrated(_ original: LoggedWorkout) -> LoggedWorkout {
            var workout = original
            workout.exercises = original.exercises.map { $0.migratingRoutineID(in: original.id) }
            return workout
        }
        inProgress = inProgress.map(migrated)
        history = history.map(migrated)
        schemaVersion = Self.currentVersion
    }
}

enum WorkoutError: LocalizedError {
    case incompatibleData, unreadableData, invalidRoutine, invalidRemoval
    case dataUnavailable, routineUnavailable, workoutUnavailable, workoutInProgress, missingSets

    var errorDescription: String? {
        switch self {
        case .incompatibleData: return "이 버전에서 열 수 없는 운동 기록입니다."
        case .unreadableData: return "저장된 운동 기록을 읽을 수 없습니다."
        case .invalidRoutine: return "루틴 이름과 종목 이름을 입력해 주세요. 종목은 하나 이상 있어야 합니다."
        case .invalidRemoval: return "첫 세트는 삭제할 수 없습니다."
        case .dataUnavailable: return "운동 데이터를 불러오거나 저장하는 중입니다."
        case .routineUnavailable: return "루틴을 찾을 수 없습니다."
        case .workoutUnavailable: return "진행 중인 운동을 찾을 수 없습니다."
        case .workoutInProgress: return "이미 진행 중인 운동이 있습니다."
        case .missingSets: return "운동을 완료하려면 세트를 한 개 이상 저장하세요."
        }
    }
}

actor WorkoutFileRepository {
    private struct Header: Decodable {
        let schemaVersion: Int
    }

    func load() throws -> WorkoutData {
        let url = try fileURL()
        let raw: Data
        do {
            raw = try Data(contentsOf: url)
        } catch let error as CocoaError where error.code == .fileReadNoSuchFile {
            return WorkoutData()
        }

        let decoder = JSONDecoder()
        guard let header = try? decoder.decode(Header.self, from: raw) else { throw WorkoutError.unreadableData }
        guard (1...WorkoutData.currentVersion).contains(header.schemaVersion) else { throw WorkoutError.incompatibleData }
        decoder.userInfo[WorkoutData.versionKey] = header.schemaVersion
        guard var data = try? decoder.decode(WorkoutData.self, from: raw) else { throw WorkoutError.unreadableData }
        if header.schemaVersion < WorkoutData.currentVersion {
            if header.schemaVersion == 1 { data.migrateVersionOne() }
            data.schemaVersion = WorkoutData.currentVersion
            try save(data)
        }
        return data
    }

    func save(_ data: WorkoutData) throws {
        let url = try fileURL()
        let raw = try JSONEncoder().encode(data)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try raw.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private func fileURL() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: false
        )
        return support.appendingPathComponent("FitnessWorkout", isDirectory: true)
            .appendingPathComponent("workouts.json")
    }
}

@MainActor
final class WorkoutStore: ObservableObject {
    @Published private(set) var data: WorkoutData?
    @Published private(set) var loadError: String?
    @Published private(set) var isWorking = false
    private let repository = WorkoutFileRepository()

    func load() async {
        guard data == nil, !isWorking else { return }
        isWorking = true
        loadError = nil
        defer { isWorking = false }
        do {
            data = try await repository.load()
        } catch {
            loadError = error.localizedDescription
        }
    }

    func saveRoutine(_ routine: WorkoutRoutine) async throws {
        var cleaned = routine
        cleaned.name = routine.name.trimmingCharacters(in: .whitespacesAndNewlines)
        for index in cleaned.exercises.indices {
            cleaned.exercises[index].name = cleaned.exercises[index].name.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard !cleaned.name.isEmpty, !cleaned.exercises.isEmpty,
              cleaned.exercises.allSatisfy({ !$0.name.isEmpty }) else { throw WorkoutError.invalidRoutine }
        try await change { data in
            if let index = data.routines.firstIndex(where: { $0.id == cleaned.id }) {
                data.routines[index] = cleaned
            } else {
                data.routines.append(cleaned)
            }
        }
    }

    func startWorkout(routineID: UUID) async throws {
        try await change { data in
            guard data.inProgress == nil else { throw WorkoutError.workoutInProgress }
            guard let routine = data.routines.first(where: { $0.id == routineID }) else {
                throw WorkoutError.routineUnavailable
            }
            data.inProgress = LoggedWorkout(
                id: UUID(), routineID: routine.id, title: routine.name, startedAt: Date(), completedAt: nil,
                exercises: routine.exercises.map { LoggedExercise(id: UUID(), routineExerciseID: $0.id, name: $0.name,
                    sets: [LoggedSet(unit: IosDisplaySettings.shared.unit == .lb ? .lb : .kg)]) }
            )
        }
    }

    func saveSet(_ set: LoggedSet, workoutID: UUID, exerciseID: UUID) async throws {
        try await changeSets(workoutID: workoutID, exerciseID: exerciseID) { sets in
            guard let index = sets.firstIndex(where: { $0.id == set.id }) else {
                throw WorkoutError.workoutUnavailable
            }
            sets[index] = set
        }
    }

    func addSet(workoutID: UUID, exerciseID: UUID, onlyIfEmpty: Bool = false) async throws {
        try await changeSets(workoutID: workoutID, exerciseID: exerciseID) { sets in
            if !onlyIfEmpty || sets.isEmpty {
                sets.append(LoggedSet(unit: IosDisplaySettings.shared.unit == .lb ? .lb : .kg))
            }
        }
    }

    func removeSet(id: UUID, workoutID: UUID, exerciseID: UUID) async throws {
        try await changeSets(workoutID: workoutID, exerciseID: exerciseID) { sets in
            guard let index = sets.firstIndex(where: { $0.id == id }),
                  OrdinarySetRules.shared.canRemove(count: Int32(sets.count), setIndex: Int64(index + 1), isFirst: index == 0)
            else { throw WorkoutError.invalidRemoval }
            sets.remove(at: index)
        }
    }

    func finishWorkout(id: UUID) async throws {
        try await change { data in
            guard var workout = data.inProgress, workout.id == id else { throw WorkoutError.workoutUnavailable }
            guard workout.setCount > 0 else { throw WorkoutError.missingSets }
            workout.completedAt = Date()
            data.history.insert(workout, at: 0)
            data.inProgress = nil
        }
    }

    func discardWorkout(id: UUID) async throws {
        try await change { data in
            guard data.inProgress?.id == id else { throw WorkoutError.workoutUnavailable }
            data.inProgress = nil
        }
    }

    private func changeSets(
        workoutID: UUID, exerciseID: UUID, _ edit: (inout [LoggedSet]) throws -> Void
    ) async throws {
        try await change { data in
            guard var workout = data.inProgress, workout.id == workoutID,
                  let index = workout.exercises.firstIndex(where: { $0.id == exerciseID }) else {
                throw WorkoutError.workoutUnavailable
            }
            try edit(&workout.exercises[index].sets)
            data.inProgress = workout
        }
    }

    private func change(_ edit: (inout WorkoutData) throws -> Void) async throws {
        guard var candidate = data, !isWorking else { throw WorkoutError.dataUnavailable }
        isWorking = true
        defer { isWorking = false }
        try edit(&candidate)
        try await repository.save(candidate)
        data = candidate
    }
}
