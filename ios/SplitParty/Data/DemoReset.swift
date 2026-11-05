import Foundation

/// Why Demo reset failed, worded for the Me tab.
enum DemoResetError: Error, Equatable {
    case offline
    /// The function could not be reached (not deployed, timed out, no target).
    case unreachable
    /// The function ran and reported an error; `message` is its text, if any.
    case failed(message: String?)

    var message: String {
        switch self {
        case .offline:
            "You're offline. Nothing was reset; connect and try again."
        case .unreachable:
            "Couldn't reach the reset function. Nothing was reset; try again."
        case let .failed(message):
            message.map { "Reset failed: \($0)" } ?? "Reset failed. Try again."
        }
    }
}

/// Calls the backend's `resetDemo` callable Cloud Function (backend/functions/index.js)
/// with no arguments, over plain HTTPS using the Firebase callable protocol
/// (POST `{"data": null}`, answer `{"result": …}` or `{"error": {…}}`), so the
/// app needs no extra Firebase SDK. The function is open: no auth header.
/// https://firebase.google.com/docs/functions/callable-reference
struct DemoResetClient {
    static let functionName = "resetDemo"
    /// The function's region (RESET_DEMO_REGION in backend/functions/index.js).
    static let region = "us-central1"
    /// The functions emulator port (backend/firebase.json).
    static let emulatorPort = 5001
    /// A cold start on the hosted project can take several seconds.
    private static let requestTimeout: TimeInterval = 30

    let endpoint: URL

    /// Hosted: `https://us-central1-<project>.cloudfunctions.net/resetDemo`.
    /// Emulator: `http://<emulator host>:5001/<project>/us-central1/resetDemo`.
    init?(target: FirestoreTarget) {
        let url: URL?
        switch target {
        case let .hosted(projectId):
            url = URL(string: "https://\(Self.region)-\(projectId).cloudfunctions.net/\(Self.functionName)")
        case let .emulator(projectId, host):
            let hostName = host.split(separator: ":").first.map(String.init) ?? host
            url = URL(string: "http://\(hostName):\(Self.emulatorPort)/\(projectId)/\(Self.region)/\(Self.functionName)")
        case .notConfigured:
            url = nil
        }
        guard let url else { return nil }
        endpoint = url
    }

    func call() async throws {
        var request = URLRequest(url: endpoint, timeoutInterval: Self.requestTimeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(#"{"data":null}"#.utf8)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch let error as URLError where Self.offlineCodes.contains(error.code) {
            throw DemoResetError.offline
        } catch {
            throw DemoResetError.unreachable
        }
        try Self.checkResult(data: data, statusCode: (response as? HTTPURLResponse)?.statusCode ?? 0)
    }

    private static let offlineCodes: Set<URLError.Code> = [.notConnectedToInternet, .networkConnectionLost,
                                                          .dataNotAllowed]

    /// Success is a 200 whose `result.ok` is true. A 404 means not deployed.
    static func checkResult(data: Data, statusCode: Int) throws {
        let body = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        if let error = body?["error"] as? [String: Any] {
            throw DemoResetError.failed(message: error["message"] as? String)
        }
        guard statusCode == 200, let result = body?["result"] as? [String: Any] else {
            throw statusCode == 404 || body == nil ? DemoResetError.unreachable : DemoResetError.failed(message: nil)
        }
        guard result["ok"] as? Bool == true else { throw DemoResetError.failed(message: nil) }
    }
}
