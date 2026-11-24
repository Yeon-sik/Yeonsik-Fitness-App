import Foundation
import Security
import SwiftUI

private struct CloudError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
    init(_ message: String) { self.message = message }
}

struct WorkoutCloudCredentials: Codable {
    let host: String
    let key: String
    let owner: String
    let email: String
    let access: String
    let refresh: String
}

private final class CloudRedirectPolicy: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

actor WorkoutCloudSession {
    static let shared = WorkoutCloudSession()
    private let http = URLSession(configuration: .ephemeral, delegate: CloudRedirectPolicy(), delegateQueue: nil)
    private var generation = 0
    private static let keychain: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "fitness.completed-workout-sync.v1",
        kSecAttrAccount as String: "fitness"
    ]

    static func current() -> WorkoutCloudCredentials? {
        var query = keychain
        query[kSecReturnData as String] = true
        var value: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &value) == errSecSuccess, let data = value as? Data else { return nil }
        return try? JSONDecoder().decode(WorkoutCloudCredentials.self, from: data)
    }

    private func persist(_ session: WorkoutCloudCredentials) throws {
        let attributes: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(session),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        var status = SecItemUpdate(Self.keychain as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(Self.keychain.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw CloudError("동기화 계정을 기기에 저장하지 못했습니다.") }
    }

    func signOut() throws {
        generation += 1
        let result = SecItemDelete(Self.keychain as CFDictionary)
        guard result == errSecSuccess || result == errSecItemNotFound else { throw CloudError("로그아웃하지 못했습니다.") }
    }

    private func isClientKey(_ key: String) -> Bool {
        if key.hasPrefix("sb_publishable_") { return key.count > "sb_publishable_".count }
        let parts = key.split(separator: ".")
        guard parts.count == 3 else { return false }
        var payload = parts[1].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        return json["role"] as? String == "anon"
    }

    func signIn(project: String, key: String, email: String, password: String) async throws {
        let url = URLComponents(string: project.trimmingCharacters(in: .whitespacesAndNewlines))
        guard url?.scheme == "https", let host = url?.host?.lowercased(), host.count <= 100,
              host.range(of: "^[a-z0-9][a-z0-9.-]*[a-z0-9]$", options: .regularExpression) != nil,
              url?.user == nil, url?.password == nil, url?.port == nil, url?.query == nil, url?.fragment == nil,
              url?.path == "" || url?.path == "/" else { throw CloudError("Fitness 프로젝트의 HTTPS 주소를 입력하세요.") }
        let publicKey = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isClientKey(publicKey) else { throw CloudError("Fitness 프로젝트의 publishable 키 또는 anon 키를 입력하세요.") }
        generation += 1
        let attempt = generation
        let session = try await token(host: host, key: publicKey, grant: "password", body: ["email": email, "password": password])
        guard generation == attempt else { throw CloudError("계정이 변경되었습니다.") }
        try persist(session)
    }

    private func token(host: String, key: String, grant: String, body: [String: String]) async throws -> WorkoutCloudCredentials {
        struct AuthReply: Decodable {
            struct User: Decodable { let id: UUID; let email: String? }
            let access_token: String
            let refresh_token: String
            let user: User
        }
        let data = try JSONEncoder().encode(body)
        let (response, code) = try await post(host: host, path: "/auth/v1/token?grant_type=" + grant, key: key, jwt: nil, data: data)
        guard (200..<300).contains(code) else { throw CloudError("로그인 정보를 확인하고 다시 로그인하세요.") }
        let session = try JSONDecoder().decode(AuthReply.self, from: response)
        return WorkoutCloudCredentials(host: host, key: key, owner: session.user.id.uuidString.lowercased(),
            email: session.user.email ?? session.user.id.uuidString, access: session.access_token, refresh: session.refresh_token)
    }

    func rpc(owner: String, host: String, function: String, body: String) async throws -> String {
        guard ["fitness_completed_workouts_v1", "fitness_save_completed_workout_v1", "fitness_completed_workout_summary_v1"].contains(function),
              let saved = Self.current(), saved.owner == owner, saved.host == host else { throw CloudError("동기화 계정이 변경되었습니다.") }
        let attempt = generation
        let path = "/rest/v1/rpc/" + function
        let data = Data(body.utf8)
        var (response, code) = try await post(host: host, path: path, key: saved.key, jwt: saved.access, data: data)
        if code == 401 {
            guard generation == attempt else { throw CloudError("동기화 계정이 변경되었습니다.") }
            let session = try await token(host: host, key: saved.key, grant: "refresh_token", body: ["refresh_token": saved.refresh])
            guard generation == attempt, session.owner == owner else { throw CloudError("다시 로그인하세요.") }
            try persist(session)
            (response, code) = try await post(host: host, path: path, key: session.key, jwt: session.access, data: data)
        }
        guard generation == attempt else { throw CloudError("동기화 계정이 변경되었습니다.") }
        if code == 404 { throw CloudError("이 Fitness 서버에 완료 운동 동기화가 배포되지 않았습니다.") }
        if code == 409 { throw CloudError("다른 기기의 변경이 있습니다. 다시 동기화해 확인하세요.") }
        guard (200..<300).contains(code), let text = String(data: response, encoding: .utf8) else { throw CloudError("Fitness 서버에서 운동을 확인하지 못했습니다.") }
        return text
    }

    private func post(host: String, path: String, key: String, jwt: String?, data: Data) async throws -> (Data, Int) {
        guard let url = URL(string: "https://" + host + path) else { throw CloudError("프로젝트 주소를 확인하세요.") }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = "POST"
        request.httpBody = data
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "apikey")
        if let jwt { request.setValue("Bearer " + jwt, forHTTPHeaderField: "Authorization") }
        let (data, response) = try await http.data(for: request)
        return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
    }
}

struct WorkoutCloudAccountView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var current = WorkoutCloudSession.current()
    @State private var project = ""
    @State private var key = ""
    @State private var email = ""
    @State private var password = ""
    @State private var error = ""
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                if let current {
                    LabeledContent("프로젝트", value: current.host)
                    LabeledContent("계정", value: current.email)
                    Button("로그아웃") { run { try await WorkoutCloudSession.shared.signOut() } }
                } else {
                    Section("Android 영양 DB와 같은 Fitness 프로젝트") {
                        TextField("프로젝트 HTTPS 주소", text: $project).keyboardType(.URL)
                        SecureField("Publishable 또는 anon 키", text: $key)
                        TextField("이메일", text: $email).keyboardType(.emailAddress).textContentType(.username)
                        SecureField("비밀번호", text: $password).textContentType(.password)
                    }.textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("로그인") { run {
                        try await WorkoutCloudSession.shared.signIn(project: project, key: key, email: email, password: password)
                        password = ""
                    } }.disabled(project.isEmpty || key.isEmpty || email.isEmpty || password.isEmpty)
                }
                if !error.isEmpty { Text(error) }
            }.disabled(busy).navigationTitle("동기화 계정")
                .toolbar { Button("닫기") { dismiss() } }
        }
    }

    private func run(_ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        Task { @MainActor in
            defer { busy = false }
            do { try await action(); current = WorkoutCloudSession.current(); error = "" }
            catch { self.error = error.localizedDescription }
        }
    }
}
