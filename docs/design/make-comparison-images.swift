// Builds the iOS | Android side-by-side images in docs/design/comparison/ from
// the committed screenshot references, so the comparison always matches them.
//
//   swift docs/design/make-comparison-images.swift     (run from the repository root)
//
// Re-run after re-recording either platform's references (iOS: ScreenSnapshotTests,
// Android: ./gradlew recordPaparazziDebug). A pair whose Android golden is missing
// is skipped with a message rather than failing.
//
// Glossary
// **Golden**: a committed reference screenshot that screenshot tests compare against.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct Pair {
    let name: String
    let ios: String
    let android: String
}

let iosReferences = "ios/SplitPartyTests/__Snapshots__/ScreenSnapshotTests"
let androidGoldens = "android/app/src/test/snapshots/images/com.splitparty.android.screenshots"

let pairs = [
    Pair(name: "login-empty", ios: "\(iosReferences)/testLoginEmpty.1.png",
         android: "\(androidGoldens)_LoginScreenshotTest_empty.png"),
    Pair(name: "home-seeded", ios: "\(iosReferences)/testHomeFeed.1.png",
         android: "\(androidGoldens)_HomeScreenshotTest_loaded.png"),
    Pair(name: "pay-request", ios: "\(iosReferences)/testPayRequestPlaceholder.1.png",
         android: "\(androidGoldens)_OtherScreensScreenshotTest_payRequest.png"),
    // Android screen goldens render without the shell, so its bar has its own golden.
    Pair(name: "bottom-bar", ios: "\(iosReferences)/testHomeFeed.1.png",
         android: "\(androidGoldens)_BottomBarScreenshotTest_homeSelected.png"),
]

/// Both screenshots are scaled to this height; the gap separates them.
let outputHeight = 1600.0
let gap = 40.0

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)

func load(_ path: String) -> CGImage? {
    guard let source = CGImageSourceCreateWithURL(root.appendingPathComponent(path) as CFURL, nil) else { return nil }
    return CGImageSourceCreateImageAtIndex(source, 0, nil)
}

func sideBySide(_ left: CGImage, _ right: CGImage) -> CGImage {
    let leftWidth = outputHeight * Double(left.width) / Double(left.height)
    let rightWidth = outputHeight * Double(right.width) / Double(right.height)
    let width = Int(leftWidth + gap + rightWidth), height = Int(outputHeight)
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.setFillColor(CGColor(gray: 1, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    context.interpolationQuality = .high
    context.draw(left, in: CGRect(x: 0, y: 0, width: leftWidth, height: outputHeight))
    context.draw(right, in: CGRect(x: leftWidth + gap, y: 0, width: rightWidth, height: outputHeight))
    return context.makeImage()!
}

let outputFolder = root.appendingPathComponent("docs/design/comparison")
try FileManager.default.createDirectory(at: outputFolder, withIntermediateDirectories: true)

for pair in pairs {
    guard let ios = load(pair.ios) else { print("skip \(pair.name): missing \(pair.ios)"); continue }
    guard let android = load(pair.android) else { print("skip \(pair.name): missing \(pair.android)"); continue }
    let destinationURL = outputFolder.appendingPathComponent("\(pair.name).png")
    let destination = CGImageDestinationCreateWithURL(destinationURL as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, sideBySide(ios, android), nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("cannot write \(destinationURL.path)") }
    print("wrote docs/design/comparison/\(pair.name).png (iOS left, Android right)")
}
