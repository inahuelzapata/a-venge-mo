import CoreGraphics
import Foundation

/// One piece of text the on-device recognizer found on the receipt photo.
/// `box` is in Vision's normalized coordinates (0...1, origin bottom-left).
struct RecognizedText: Equatable {
    let text: String
    let box: CGRect
}

/// Finds the receipt's total line among recognized text, ignoring subtotal,
/// tax and tip lines (PoC-10). Pure logic, so it is unit-tested without a camera.
///
/// Receipts usually print the label and the amount far apart ("TOTAL ...... $48.50"),
/// and the recognizer often returns them as two pieces, so pieces are first
/// grouped into printed lines by their vertical position.
enum ReceiptTotalDetector {
    /// A line whose label matches one of these is a total candidate, the earlier
    /// patterns winning over later ones ("amount due" is more final than "total").
    private static let totalLabelsByPriority: [[String]] = [
        ["grand total", "amount due", "balance due", "total due", "amount paid"],
        ["total"],
    ]
    /// Lines that mention "total" but are not the bill's total.
    private static let excludedWords = [
        "subtotal", "sub total", "sub-total", "tax", "tip", "gratuity", "suggested", "%",
        "items", "item count", "qty", "savings", "discount", "service charge",
    ]
    /// Pieces whose vertical centres differ by less than this fraction of their
    /// height sit on the same printed line.
    private static let sameLineTolerance: CGFloat = 0.6
    /// An amount on the receipt: digits with exactly two decimals, optionally a
    /// "$" and thousands separators. "12.5" or "1234" are never totals.
    private static let amountPattern = try! NSRegularExpression(
        pattern: #"(?<![\d.,])\$?\s?(\d{1,3}(?:,\d{3})+|\d+)[.,](\d{2})(?![\d])"#)

    /// The total in cents, or nil when no total line with a plausible amount was found.
    static func totalCents(in pieces: [RecognizedText]) -> Int? {
        let lines = printedLines(from: pieces)
        for labels in totalLabelsByPriority {
            let candidates = lines.indices.compactMap { index -> Int? in
                let lowered = lines[index].lowercased()
                guard labels.contains(where: { containsWord($0, in: lowered) }),
                      !excludedWords.contains(where: { containsWord($0, in: lowered) }) else { return nil }
                // The amount is usually on the same line; when it was printed (or
                // recognized) on its own line, take the one right below.
                if let amount = amounts(in: lines[index]).last { return amount }
                if index + 1 < lines.count, !isLabelledLine(lines[index + 1]) {
                    return amounts(in: lines[index + 1]).last
                }
                return nil
            }
            // A receipt can repeat its total (e.g. on the card slip); the largest is
            // never the subtotal, which was excluded anyway.
            if let best = candidates.max() { return best }
        }
        return nil
    }

    /// Joins pieces into printed lines, top of the receipt first, each line's
    /// pieces left to right.
    static func printedLines(from pieces: [RecognizedText]) -> [String] {
        let sorted = pieces.sorted { $0.box.midY > $1.box.midY }
        var lines: [[RecognizedText]] = []
        for piece in sorted {
            if let last = lines.last?.last,
               abs(last.box.midY - piece.box.midY) < sameLineTolerance * max(last.box.height, piece.box.height) {
                lines[lines.count - 1].append(piece)
            } else {
                lines.append([piece])
            }
        }
        return lines.map { line in line.sorted { $0.box.minX < $1.box.minX }.map(\.text).joined(separator: " ") }
    }

    /// Every amount on a line, in cents, left to right. Out-of-range amounts are
    /// dropped (the bill accepts $0.01 to $9,999.99).
    static func amounts(in line: String) -> [Int] {
        let range = NSRange(line.startIndex..., in: line)
        return amountPattern.matches(in: line, range: range).compactMap { match -> Int? in
            guard let wholeRange = Range(match.range(at: 1), in: line),
                  let centsRange = Range(match.range(at: 2), in: line) else { return nil }
            let whole = line[wholeRange].replacingOccurrences(of: ",", with: "")
            return try? CentsInput.bill.parse("\(whole).\(line[centsRange])").get()
        }
    }

    /// Whole-word match, so "tip" does not exclude "multiple" and "total" does
    /// not match "subtotal" (which is excluded on its own).
    private static func containsWord(_ word: String, in line: String) -> Bool {
        guard word.first?.isLetter == true else { return line.contains(word) }
        let pattern = "\\b" + NSRegularExpression.escapedPattern(for: word) + "\\b"
        return line.range(of: pattern, options: .regularExpression) != nil
    }

    private static func isLabelledLine(_ line: String) -> Bool {
        line.contains { $0.isLetter }
    }
}
