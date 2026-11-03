import Foundation

/// Display formatting for money. Every amount in the app is an integer number of
/// cents (docs/firestore-schema.md: "Money is always an integer number of cents").
/// Conversion to dollars happens only here, through `Decimal`, never `Double`, so
/// `1850` is always exactly "$18.50".
enum Money {
    private static let centsPerDollar = Decimal(100)

    /// US dollars, because the seeded merchant and balances are US amounts. Fixed
    /// rather than the phone's locale so all three demo phones show the same text.
    private static let formatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: "en_US")
        formatter.currencyCode = "USD"
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter
    }()

    /// `25000` → `"$250.00"`, `-1234` → `"-$12.34"`.
    static func format(cents: Int) -> String {
        let dollars = Decimal(cents) / centsPerDollar
        return formatter.string(from: dollars as NSDecimalNumber) ?? "$\(dollars)"
    }
}
