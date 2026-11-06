import XCTest
@testable import SplitParty

/// Covers the rules the work order names: money is integer cents formatted for
/// display only, and username login is case-insensitive with empty input refused.
final class MoneyTests: XCTestCase {
    func testSeededBalancesFormatAsDollars() {
        XCTAssertEqual(Money.format(cents: 25000), "$250.00")
        XCTAssertEqual(Money.format(cents: 18050), "$180.50")
        XCTAssertEqual(Money.format(cents: 32075), "$320.75")
    }

    func testSmallNegativeAndZeroAmounts() {
        XCTAssertEqual(Money.format(cents: 1), "$0.01")
        XCTAssertEqual(Money.format(cents: 0), "$0.00")
        XCTAssertEqual(Money.format(cents: -1234), "-$12.34")
    }

    /// Classic binary floating-point trap: 0.1 + 0.2 in dollars. In cents it is exact.
    func testNoFloatingPointDrift() {
        XCTAssertEqual(Money.format(cents: 10 + 20), "$0.30")
        XCTAssertEqual(Money.format(cents: 3115), "$31.15")
    }
}

final class UsernameTests: XCTestCase {
    func testSeededUsernamesAreCaseInsensitiveAndTrimmed() {
        XCTAssertEqual(Username.normalized("maxi"), "maxi")
        XCTAssertEqual(Username.normalized("  Maxi "), "maxi")
        XCTAssertEqual(Username.normalized("ANNIE"), "annie")
        XCTAssertEqual(Username.normalized("Christophe"), "christophe")
    }

    func testEmptyOrImpossibleUsernamesAreRefusedBeforeAnyLookup() {
        XCTAssertNil(Username.normalized(""))
        XCTAssertNil(Username.normalized("   "))
        XCTAssertNil(Username.normalized("ma"))
        XCTAssertNil(Username.normalized("a/b"))
    }

    /// "thor" is a valid-looking username; it is refused because no document exists.
    func testWellFormedUnknownUsernameStillReachesTheLookup() {
        XCTAssertEqual(Username.normalized("thor"), "thor")
    }
}

final class DocumentMappingTests: XCTestCase {
    private func integer(_ value: Int64) -> NSNumber { NSNumber(value: value) }

    func testTransactionReadsSchemaFields() throws {
        let transaction = try XCTUnwrap(PaymentTransaction(id: "seed_tx_04", data: [
            "payerId": "christophe", "payeeId": "maxi", "amountCents": integer(2575),
            "memo": "Tony's Split Party", "merchantName": "Tony's",
            "likesCount": integer(2), "commentsCount": integer(2),
        ], createdAt: nil))
        XCTAssertEqual(transaction.amountCents, 2575)
        XCTAssertEqual(transaction.memo, "Tony's Split Party")
        XCTAssertEqual(transaction.merchantName, "Tony's")
        XCTAssertEqual(transaction.likesCount, 2)
        XCTAssertEqual(transaction.commentsCount, 2)
    }

    func testFloatingPointAmountIsRejectedNotRounded() {
        XCTAssertNil(PaymentTransaction(id: "bad", data: [
            "payerId": "annie", "payeeId": "maxi", "amountCents": NSNumber(value: 31.15),
        ], createdAt: nil))
        XCTAssertNil(UserAccount(id: "maxi", data: ["displayName": "Maxi", "balanceCents": NSNumber(value: 250.0)]))
    }

    func testUserReadsBalanceInCents() throws {
        let user = try XCTUnwrap(UserAccount(id: "annie", data: [
            "username": "annie", "displayName": "Annie", "avatar": "avatar_annie", "balanceCents": integer(18050),
        ]))
        XCTAssertEqual(user.balanceCents, 18050)
        XCTAssertEqual(user.avatarKey, "avatar_annie")
    }
}

/// The Me tab's "Custom shares" switch: off on a fresh install, remembered after.
final class CustomSharesStoreTests: XCTestCase {
    func testOffByDefaultAndRemembered() throws {
        let suiteName = "CustomSharesStoreTests"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        defer { defaults.removePersistentDomain(forName: suiteName) }

        XCTAssertFalse(CustomSharesStore(defaults: defaults).isEnabled)
        CustomSharesStore(defaults: defaults).save(isEnabled: true)
        XCTAssertTrue(CustomSharesStore(defaults: defaults).isEnabled, "A new store, as after a restart")
        CustomSharesStore(defaults: defaults).save(isEnabled: false)
        XCTAssertFalse(CustomSharesStore(defaults: defaults).isEnabled)
    }
}
