import Foundation

/// The party's `tip` map (docs/firestore-schema.md, `parties.tip`): either a
/// percent preset or a custom amount in cents.
///
/// The 0% chip has no percent preset in the schema (presets are 15/18/20/25), so
/// it is stored as a custom tip of 0 cents (`TipChoice.zero`); readers show a
/// custom 0 as the 0% chip.
enum TipChoice: Equatable {
    case percent(Int)
    case custom(amountCents: Int)

    static let zero = TipChoice.custom(amountCents: 0)
    /// The schema's percent presets, same as TIP_PERCENT_PRESETS in backend/lib/schema.js.
    static let schemaPercentPresets = [15, 18, 20, 25]

    enum Field {
        static let type = "type"
        static let percent = "percent"
        static let amountCents = "amountCents"
        static let percentType = "percent"
        static let customType = "custom"
    }

    /// Nil for `null`, a missing field or a shape the rules would reject.
    init?(firestoreValue: Any?) {
        guard let map = firestoreValue as? [String: Any], let type = map[Field.type] as? String else { return nil }
        switch type {
        case Field.percentType:
            guard let percent = FirestoreValue.integer(map[Field.percent]),
                  Self.schemaPercentPresets.contains(percent) else { return nil }
            self = .percent(percent)
        case Field.customType:
            guard let amount = FirestoreValue.integer(map[Field.amountCents]), amount >= 0 else { return nil }
            self = .custom(amountCents: amount)
        default:
            return nil
        }
    }

    /// The map written to Firestore; the rules accept exactly these keys.
    var firestoreValue: [String: Any] {
        switch self {
        case let .percent(percent): [Field.type: Field.percentType, Field.percent: percent]
        case let .custom(amountCents): [Field.type: Field.customType, Field.amountCents: amountCents]
        }
    }

    /// Percent tips round half up to whole cents (backend/lib/split.js `tipCents`).
    func tipCents(onBillCents billCents: Int) -> Int {
        switch self {
        case let .custom(amountCents): amountCents
        case let .percent(percent): (billCents * percent + 50) / 100
        }
    }

    var isZero: Bool {
        self == .zero
    }

    /// "18%", "0%" or "$7.00": how the chosen tip is described on screen.
    var label: String {
        switch self {
        case let .percent(percent): "\(percent)%"
        case let .custom(amountCents): amountCents == 0 ? "0%" : Money.format(cents: amountCents)
        }
    }
}

/// Every member's share of a party, from the equal-split rule in
/// docs/firestore-schema.md ("Split rule") and backend/lib/split.js. Integer
/// cents only; the host (first member) carries the leftover cents, so the shares
/// sum exactly to the total.
struct PartySplit: Equatable {
    let billCents: Int
    let tipCents: Int
    let totalCents: Int
    /// Member ids in `memberIds` order, host first.
    let memberIds: [String]
    let sharesByMember: [String: Int]

    var headcount: Int { memberIds.count }

    /// Nil when there is no bill yet, or no members. A missing tip counts as 0%
    /// ("the tip is 0% until the host picks one").
    init?(billCents: Int?, tip: TipChoice?, memberIds: [String]) {
        guard let billCents, billCents >= 0, !memberIds.isEmpty else { return nil }
        let tipCents = (tip ?? .zero).tipCents(onBillCents: billCents)
        let totalCents = billCents + tipCents
        let base = totalCents / memberIds.count
        let leftover = totalCents - base * memberIds.count
        var shares: [String: Int] = [:]
        for (index, memberId) in memberIds.enumerated() {
            shares[memberId] = index == 0 ? base + leftover : base
        }
        self.billCents = billCents
        self.tipCents = tipCents
        self.totalCents = totalCents
        self.memberIds = memberIds
        self.sharesByMember = shares
    }

    func share(of memberId: String) -> Int? {
        sharesByMember[memberId]
    }
}

/// Parses what the host types into whole cents, without ever going through
/// floating point: "48.50" → 4850. Accepts "." or "," as the decimal mark and
/// ignores a leading "$" and thousands separators typed as spaces.
struct CentsInput {
    enum Problem: Error, Equatable {
        case empty
        case notANumber
        case tooManyDecimals
        case belowMinimum(Int)
        case aboveMaximum(Int)

        var message: String {
            switch self {
            case .empty: "Enter an amount."
            case .notANumber: "Enter an amount like 48.50."
            case .tooManyDecimals: "Use at most two decimals, like 48.50."
            case let .belowMinimum(cents): "The amount must be at least \(Money.format(cents: cents))."
            case let .aboveMaximum(cents): "The amount can be at most \(Money.format(cents: cents))."
            }
        }
    }

    /// The bill accepts $0.01 to $9,999.99.
    static let bill = CentsInput(minimumCents: 1, maximumCents: 999_999)
    /// A custom tip may be $0.00 (no tip) up to $9,999.99.
    static let customTip = CentsInput(minimumCents: 0, maximumCents: 999_999)

    let minimumCents: Int
    let maximumCents: Int

    private static let maxFractionDigits = 2
    /// More whole digits than this cannot be in range and could overflow `Int`.
    private static let maxWholeDigits = 7

    func parse(_ text: String) -> Result<Int, Problem> {
        var cleaned = text.trimmingCharacters(in: .whitespaces)
        if cleaned.hasPrefix("$") { cleaned.removeFirst() }
        cleaned = cleaned.replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty else { return .failure(.empty) }

        let parts = cleaned.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count <= 2 else { return .failure(.notANumber) }
        let whole = String(parts[0])
        let fraction = parts.count == 2 ? String(parts[1]) : ""
        guard !(whole.isEmpty && fraction.isEmpty),
              (whole + fraction).allSatisfy({ $0.isASCII && $0.isNumber }) else { return .failure(.notANumber) }
        guard fraction.count <= Self.maxFractionDigits else { return .failure(.tooManyDecimals) }
        let significantWhole = whole.drop { $0 == "0" }
        guard significantWhole.count <= Self.maxWholeDigits else { return .failure(.aboveMaximum(maximumCents)) }

        let dollars = Int(whole.isEmpty ? "0" : whole) ?? 0
        let paddedFraction = fraction.padding(toLength: Self.maxFractionDigits, withPad: "0", startingAt: 0)
        let cents = dollars * 100 + (Int(paddedFraction) ?? 0)
        if cents < minimumCents { return .failure(.belowMinimum(minimumCents)) }
        if cents > maximumCents { return .failure(.aboveMaximum(maximumCents)) }
        return .success(cents)
    }

    /// 4850 → "48.50": what an edit field starts with.
    static func editableText(cents: Int) -> String {
        String(format: "%d.%02d", cents / 100, cents % 100)
    }
}

extension Money {
    /// "39 dollars and 34 cents" for VoiceOver, so amounts are read as money
    /// rather than as "39 point 34".
    static func spoken(cents: Int) -> String {
        if cents < 0 { return "minus " + spoken(cents: -cents) }
        let dollars = cents / 100
        let remainder = cents % 100
        let dollarPart = dollars == 1 ? "1 dollar" : "\(dollars) dollars"
        switch remainder {
        case 0: return dollarPart
        case 1: return "\(dollarPart) and 1 cent"
        default: return "\(dollarPart) and \(remainder) cents"
        }
    }
}
