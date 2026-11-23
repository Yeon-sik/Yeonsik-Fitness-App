import HealthKit
import SwiftUI
import UniformTypeIdentifiers
import YeonsikShared

struct DailyWorkoutView: UIViewControllerRepresentable {
    let history: [LoggedWorkout]
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(close: { dismiss() }) }

    func makeUIViewController(context: Context) -> UIViewController {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        let workouts = history.compactMap { workout -> DailyWorkoutSource? in
            guard let end = workout.completedAt else { return nil }
            let exercises = workout.exercises.filter { !$0.sets.isEmpty }.map { exercise in
                DailyWorkoutExercise(name: exercise.name, sets: exercise.sets.map { set in
                    IosDailyWorkoutHostKt.makeIosWorkoutSet(weightKg: set.weightKg, reps: set.reps, rir: Int32(set.rir ?? -1))
                })
            }
            return IosDailyWorkoutHostKt.makeIosFitnessWorkout(
                id: workout.id.uuidString, title: workout.title,
                startMillis: workout.startedAt.milliseconds, endMillis: end.milliseconds, exercises: exercises
            )
        }
        let controller = IosDailyWorkoutHostKt.makeDailyWorkoutViewController(
            initialDate: formatter.string(from: Date()), workouts: workouts, platform: context.coordinator
        )
        context.coordinator.host = controller
        return controller
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}

    @MainActor
    final class Coordinator: NSObject, IosDailyWorkoutPlatform, UIDocumentPickerDelegate {
        weak var host: UIViewController?
        private let closeScreen: () -> Void
        private let healthStore = HKHealthStore()
        private var fileAction: FileAction?

        private enum FileAction {
            case open(IosDailyWorkoutFileReply)
            case save(IosDailyWorkoutFileReply, directory: URL)
        }

        init(close: @escaping () -> Void) { closeScreen = close }

        nonisolated func close() { Task { @MainActor in closeScreen() } }

        nonisolated func readHealth(startMillis: Int64, endMillis: Int64, reply: IosDailyWorkoutHealthReply) {
            Task { @MainActor in
                guard HKHealthStore.isHealthDataAvailable(),
                      let heartRate = HKQuantityType.quantityType(forIdentifier: .heartRate) else {
                    reply.fail(message: "이 기기에서 Apple 건강을 사용할 수 없습니다.")
                    return
                }
                let workoutType = HKObjectType.workoutType()
                do {
                    try await healthStore.requestAuthorization(toShare: [], read: [workoutType, heartRate])
                    let from = Date(timeIntervalSince1970: Double(startMillis) / 1_000)
                    let to = Date(timeIntervalSince1970: Double(endMillis) / 1_000)
                    let workouts = try await samples(of: workoutType, from: from, to: to).compactMap { $0 as? HKWorkout }
                    let pulseEnd = max(to, workouts.map(\.endDate).max() ?? to)
                    let rateSamples = try await samples(of: heartRate, from: from, to: pulseEnd)
                    let sources = workouts.sorted {
                        Self.syncVersion($0) < Self.syncVersion($1)
                    }.map(Self.workoutSource)
                    let bpm = HKUnit.count().unitDivided(by: .minute())
                    let heartSamples = rateSamples.compactMap { $0 as? HKQuantitySample }.map {
                        DailyHeartRateSample(writer: $0.sourceRevision.source.bundleIdentifier,
                                             timeMillis: $0.startDate.milliseconds, bpm: $0.quantity.doubleValue(for: bpm))
                    }
                    // HealthKit can return an empty read when the user did not grant access.
                    reply.complete(records: sources.isEmpty && heartSamples.isEmpty ? nil :
                        DailyWearableRecords(workouts: sources, heartRateSamples: heartSamples))
                } catch {
                    reply.fail(message: "Apple 건강 기록을 읽지 못했습니다. 이 앱의 건강 읽기 권한을 확인해 주세요.")
                }
            }
        }

        nonisolated func pickFleekCsv(reply: IosDailyWorkoutFileReply) {
            Task { @MainActor in
                guard let host, fileAction == nil else { reply.complete(text: nil); return }
                let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.commaSeparatedText, .plainText])
                fileAction = .open(reply)
                picker.delegate = self
                host.present(picker, animated: true)
            }
        }

        nonisolated func saveJson(name: String, json: String, reply: IosDailyWorkoutFileReply) {
            Task { @MainActor in
                guard let host, fileAction == nil else { reply.complete(text: nil); return }
                let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
                let file = directory.appendingPathComponent(name)
                do {
                    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                    try Data(json.utf8).write(to: file, options: .atomic)
                    let picker = UIDocumentPickerViewController(forExporting: [file], asCopy: true)
                    fileAction = .save(reply, directory: directory)
                    picker.delegate = self
                    host.present(picker, animated: true)
                } catch {
                    try? FileManager.default.removeItem(at: directory)
                    reply.fail(message: "JSON 파일을 준비하지 못했습니다.")
                }
            }
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            finishPicker(url: urls.first)
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { finishPicker(url: nil) }

        private func finishPicker(url: URL?) {
            let action = fileAction
            fileAction = nil
            switch action {
            case .open(let reply):
                guard let url else { reply.complete(text: nil); return }
                Task {
                    do {
                        let text = try await Task.detached {
                            let scoped = url.startAccessingSecurityScopedResource()
                            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                            return try String(contentsOf: url, encoding: .utf8)
                        }.value
                        reply.complete(text: text)
                    } catch {
                        reply.fail(message: "FLEEK CSV를 읽지 못했습니다.")
                    }
                }
            case .save(let reply, let directory):
                try? FileManager.default.removeItem(at: directory)
                reply.complete(text: url == nil ? nil : "")
            case nil:
                break
            }
        }

        private func samples(of type: HKSampleType, from: Date, to: Date) async throws -> [HKSample] {
            let predicate = HKQuery.predicateForSamples(withStart: from, end: to, options: .strictStartDate)
            return try await withCheckedThrowingContinuation { continuation in
                let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit,
                                          sortDescriptors: nil) { _, samples, error in
                    if let error { continuation.resume(throwing: error) }
                    else { continuation.resume(returning: samples ?? []) }
                }
                healthStore.execute(query)
            }
        }

        private static func workoutSource(_ record: HKWorkout) -> DailyWorkoutSource {
            let activeEnergy = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)
                .flatMap { record.statistics(for: $0)?.sumQuantity() }
            let externalID = record.metadata?[HKMetadataKeyExternalUUID] as? String
            let id = externalID.flatMap { $0.isEmpty ? nil : "external:\($0)" } ?? record.uuid.uuidString
            return IosDailyWorkoutHostKt.makeAppleHealthWorkout(
                id: id, writer: record.sourceRevision.source.bundleIdentifier, kindName: kind(record.workoutActivityType),
                startMillis: record.startDate.milliseconds, endMillis: record.endDate.milliseconds,
                durationSeconds: Int32(clamping: Int64(record.duration)),
                distanceMeters: record.totalDistance?.doubleValue(for: .meter()) ?? -1,
                energyKcal: activeEnergy?.doubleValue(for: .kilocalorie()) ?? -1
            )
        }

        private static func syncVersion(_ workout: HKWorkout) -> Int {
            (workout.metadata?[HKMetadataKeySyncVersion] as? NSNumber)?.intValue ?? 0
        }

        private static func kind(_ activity: HKWorkoutActivityType) -> String {
            switch activity {
            case .traditionalStrengthTraining, .functionalStrengthTraining: return "STRENGTH"
            case .running: return "RUNNING"
            case .walking, .hiking: return "WALKING"
            case .cycling: return "CYCLING"
            case .swimming: return "SWIMMING"
            case .elliptical, .rowing, .stairClimbing, .mixedCardio, .highIntensityIntervalTraining: return "CARDIO"
            default: return "OTHER"
            }
        }
    }
}

private extension Date {
    var milliseconds: Int64 { Int64((timeIntervalSince1970 * 1_000).rounded()) }
}
