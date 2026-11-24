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

    func toKg(_ value: Double) -> Double {
        let unit: MassUnit = self == .kg ? .kg : .lb
        return unit.toKg(value: value)
    }
}

struct LoggedSet: Codable, Identifiable, Sendable {
    let id: UUID
    let inputWeight: Double
    let unit: WorkoutWeightUnit
    let weightKg: Double
    let reps: Int32
    let rir: Int?

    init(id: UUID, inputWeight: Double, unit: WorkoutWeightUnit, reps: Int32, rir: Int?) throws {
        guard inputWeight.isFinite, inputWeight >= 0, reps > 0,
              rir.map({ (0...5).contains($0) }) ?? true else {
            throw WorkoutError.invalidSet
        }
        self.id = id
        self.inputWeight = inputWeight
        self.unit = unit
        self.weightKg = unit.toKg(inputWeight)
        self.reps = reps
        self.rir = rir
    }

    var estimatedOneRepMaxKg: Double? {
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

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets.count } }
}

struct WorkoutData: Codable, Sendable {
    static let currentVersion = 2
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
    case incompatibleData, unreadableData, invalidRoutine, invalidSet
    case dataUnavailable, routineUnavailable, workoutUnavailable, workoutInProgress, missingSets

    var errorDescription: String? {
        switch self {
        case .incompatibleData: return "이 버전에서 열 수 없는 운동 기록입니다."
        case .unreadableData: return "저장된 운동 기록을 읽을 수 없습니다."
        case .invalidRoutine: return "루틴 이름과 종목 이름을 입력해 주세요. 종목은 하나 이상 있어야 합니다."
        case .invalidSet: return "중량은 0 이상, 횟수는 1 이상으로 입력해 주세요."
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
        guard [1, WorkoutData.currentVersion].contains(header.schemaVersion) else { throw WorkoutError.incompatibleData }
        guard var data = try? decoder.decode(WorkoutData.self, from: raw) else { throw WorkoutError.unreadableData }
        if header.schemaVersion == 1 {
            data.migrateVersionOne()
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
                exercises: routine.exercises.map { LoggedExercise(id: UUID(), routineExerciseID: $0.id, name: $0.name) }
            )
        }
    }

    func saveSet(_ set: LoggedSet, workoutID: UUID, exerciseID: UUID) async throws {
        try await changeSets(workoutID: workoutID, exerciseID: exerciseID) { sets in
            if let index = sets.firstIndex(where: { $0.id == set.id }) {
                sets[index] = set
            } else {
                sets.append(set)
            }
        }
    }

    func removeSet(id: UUID, workoutID: UUID, exerciseID: UUID) async throws {
        try await changeSets(workoutID: workoutID, exerciseID: exerciseID) { sets in
            sets.removeAll { $0.id == id }
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
        workoutID: UUID, exerciseID: UUID, _ edit: (inout [LoggedSet]) -> Void
    ) async throws {
        try await change { data in
            guard var workout = data.inProgress, workout.id == workoutID,
                  let index = workout.exercises.firstIndex(where: { $0.id == exerciseID }) else {
                throw WorkoutError.workoutUnavailable
            }
            edit(&workout.exercises[index].sets)
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
