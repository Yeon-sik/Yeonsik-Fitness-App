import SwiftUI
import YeonsikShared

/// M7 functional-parity screen. Persistence is supplied by the shared iOS in-memory adapter.
struct BodyView: View {
    private let scope: AccountScope
    private let service: BodyMetricsApplicationService

    @State private var date = "2026-09-20"
    @State private var recordId: String?
    @State private var weightText = ""
    @State private var memo = ""
    @State private var heightText = ""
    @State private var profileLabel = "미설정"
    @State private var status = ""

    init() {
        let scope = AccountScope(ownerId: "ios-proof-owner")
        self.scope = scope
        self.service = BodyMetricsApplicationService(
            repository: IosBodyMetricsRepository(),
            ownerId: scope.ownerId
        )
    }

    var body: some View {
        Form {
            Section("체중") {
                TextField("날짜 (YYYY-MM-DD)", text: $date)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("체중 (kg)", text: $weightText)
                    .keyboardType(.decimalPad)
                TextField("메모", text: $memo)
                HStack {
                    Button("조회") { loadWeight() }
                    Button(recordId == nil ? "추가" : "수정") { saveWeight() }
                    if recordId != nil {
                        Button("삭제", role: .destructive) { deleteWeight() }
                    }
                }
            }

            Section("BodyProfile") {
                Text("현재: \(profileLabel)")
                TextField("키 (cm)", text: $heightText)
                    .keyboardType(.numberPad)
                Button("프로필 저장") { saveProfile() }
            }

            if !status.isEmpty {
                Section("shared 결과") {
                    Text(status)
                }
            }
        }
        .navigationTitle("Body")
        .onAppear {
            loadWeight()
            loadProfile()
        }
    }

    private func loadWeight() {
        let editor = service.load(scope: scope, date: date, recordId: nil)
        recordId = editor.recordId
        weightText = editor.exists() ? String(editor.weightKg) : ""
        memo = editor.memo
        status = editor.exists() ? "shared에서 체중을 조회했습니다." : "해당 날짜 기록이 없습니다."
    }

    private func saveWeight() {
        guard let weight = Double(weightText) else {
            status = "체중을 숫자로 입력하세요."
            return
        }
        recordId = service.save(
            scope: scope,
            recordId: recordId,
            date: date,
            weightKg: weight,
            memo: memo
        )
        status = "shared service로 체중을 저장했습니다."
    }

    private func deleteWeight() {
        guard let recordId else { return }
        service.delete(scope: scope, recordId: recordId)
        self.recordId = nil
        weightText = ""
        memo = ""
        status = "shared service로 체중을 삭제했습니다."
    }

    private func loadProfile() {
        let profile = service.loadProfile(scope: scope)
        if let height = profile.heightCm {
            heightText = "\(height)"
        } else {
            heightText = ""
        }
        profileLabel = profile.heightLabelKo()
    }

    private func saveProfile() {
        let height = heightText.trimmingCharacters(in: .whitespacesAndNewlines)
        let profile: BodyProfile
        if height.isEmpty {
            profile = BodyProfile(heightCm: nil, createdAt: "", updatedAt: "")
        } else if let value = Int32(height) {
            profile = BodyProfile(heightCm: value, createdAt: "", updatedAt: "")
        } else {
            status = "키를 숫자로 입력하세요."
            return
        }
        service.saveProfile(scope: scope, profile: profile)
        profileLabel = service.loadProfile(scope: scope).heightLabelKo()
        status = "shared service로 BodyProfile을 저장했습니다."
    }
}
