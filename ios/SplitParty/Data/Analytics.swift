import Foundation
import os

/// One product analytics event. The PoC has no analytics backend: events go to
/// the unified log (subsystem "SplitParty", category "analytics"), where the
/// demo can read them, and to `Analytics.observer` for tests.
struct AnalyticsEvent: Equatable, Sendable {
    let name: String
    let parameters: [String: String]

    /// The host's cancel committed (PRD "cancelled vs settled vs timed out").
    static func partyCancelled(_ cancellation: PartyCancellation) -> AnalyticsEvent {
        AnalyticsEvent(name: "party_cancelled", parameters: [
            "party_id": cancellation.partyId,
            "member_count": String(cancellation.memberCount),
            "seconds_since_created": String(cancellation.secondsSinceCreated),
        ])
    }
}

@MainActor
enum Analytics {
    private static let logger = Logger(subsystem: "SplitParty", category: "analytics")
    /// Tests capture events here.
    static var observer: ((AnalyticsEvent) -> Void)?

    static func record(_ event: AnalyticsEvent) {
        let parameters = event.parameters.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
            .joined(separator: " ")
        logger.info("\(event.name, privacy: .public) \(parameters, privacy: .public)")
        observer?(event)
    }
}
