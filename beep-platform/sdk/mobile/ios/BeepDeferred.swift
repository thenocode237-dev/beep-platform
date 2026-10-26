import Foundation

/// Explicit user-provided token. No clipboard access or device fingerprinting.
public struct BeepDeferred {
    public let origin: URL
    public let appId: UUID
    public let scheme: String
    public init(origin: URL, appId: UUID, scheme: String) throws {
        guard origin.scheme == "https", origin.host != nil, origin.user == nil, origin.password == nil,
              origin.query == nil, origin.fragment == nil, ["", "/"].contains(origin.path),
              scheme.range(of: "^[a-z][a-z0-9+.-]{1,39}$", options: .regularExpression) != nil,
              !["http", "https", "javascript", "file", "data", "intent"].contains(scheme) else { throw BeepError.invalidConfiguration }
        self.origin = origin; self.appId = appId; self.scheme = scheme
    }
    public enum BeepError: Error { case invalidConfiguration, invalidToken, invalidDestination }
    public func resolve(token: String) async throws -> URL {
        guard token.range(of: "^[A-Za-z0-9_-]{32,128}$", options: .regularExpression) != nil else { throw BeepError.invalidToken }
        var request = URLRequest(url: origin.appendingPathComponent("v1/mobile/resolve"))
        request.httpMethod = "POST"; request.timeoutInterval = 10
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["appId": appId.uuidString, "token": token, "platform": "ios"])
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil
        let session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200, data.count <= 8192 else { throw BeepError.invalidToken }
        struct Resolution: Decodable { let destination: String }
        let result = try JSONDecoder().decode(Resolution.self, from: data)
        guard let url = URL(string: result.destination), url.scheme == scheme else { throw BeepError.invalidDestination }
        return url
    }
}
private final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
