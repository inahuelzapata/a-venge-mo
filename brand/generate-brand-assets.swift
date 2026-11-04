// Generates every Aven-mo brand asset the iOS and Android apps use from the two
// source images in brand/source/, so both platforms trace back to one place.
//
//   swift brand/generate-brand-assets.swift          (run from the repository root)
//
// Outputs (overwritten on every run):
//   brand/aven-mo-mark.svg                    vector mark, the master for in-app use
//   brand/aven-mo-icon-1024.png               opaque 1024×1024 icon master
//   ios/.../AppIcon.appiconset/AppIcon-1024.png
//   ios/.../AvenMoMark.imageset/aven-mo-mark.svg
//   android/.../drawable/ic_aven_mo_mark.xml           in-app mark (tinted in code)
//   android/.../drawable/ic_launcher_foreground.xml    adaptive icon foreground layer
//   android/.../drawable/ic_launcher_background.xml    adaptive icon background layer
//
// The mark is traced by hand from brand/source/aven-mo-mark-source.png
// (1254×1254): the coordinates below are measured on that image. Running the
// script also re-draws the trace over the source and prints how closely they
// overlap, so a bad edit to the geometry shows up at once.
//
// The icon master is the user-supplied glossy icon (500×500, transparent
// corners) upscaled to 1024 on a navy fill: iOS rejects icons with transparency
// and masks the corners itself. The user chose this over rebuilding the glossy
// look from the vector mark.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// MARK: - Geometry, in source-image pixels (y grows downwards)

struct Point { var x: Double; var y: Double }

/// The ring: one circle band with a gap where the lower-left stroke passes through it.
let ringCentre = Point(x: 623.5, y: 685)
let ringOuterRadius = 432.0
let ringInnerRadius = 358.5

/// Top of the "A": both legs, joined at the top bar, with the notch between them.
let letterAPolygon: [Point] = [
    .init(x: 795, y: 141), .init(x: 945, y: 141), .init(x: 838, y: 483), .init(x: 758, y: 483),
    .init(x: 783, y: 368), .init(x: 715, y: 483), .init(x: 600, y: 483),
]
/// The long lower-left stroke that crosses the ring (pointed at its top).
let strokePolygon: [Point] = [
    .init(x: 447, y: 693), .init(x: 485, y: 868), .init(x: 345, y: 1112), .init(x: 194, y: 1112),
]
let letterVPolygon: [Point] = [
    .init(x: 426, y: 500), .init(x: 578, y: 500), .init(x: 610, y: 740), .init(x: 710, y: 500),
    .init(x: 860, y: 500), .init(x: 672, y: 903), .init(x: 517, y: 903),
]
/// The white gaps between the stroke and the ring ends, as horizontal offsets from
/// the stroke's left and right edges.
let ringGapLeftOfStroke = 26.0
let ringGapRightOfStroke = 32.5

/// The mark's bounding box is 865×975 source pixels; this square viewBox
/// centres it with a little room above and below.
let viewBoxSize = 1000.0
let viewBoxOrigin = Point(x: 123.5, y: 128.5)

let markNavyHex = "#021F40"

// MARK: - Ring path

/// A line through two points, solved for x at a given y.
func xOnLine(_ a: Point, _ b: Point, atY y: Double) -> Double {
    a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
}

/// Where the line x = edge(y) + offset meets the circle, on its lower-left side.
func cutPoint(edgeFrom a: Point, to b: Point, offset: Double, radius: Double) -> Point {
    // Bisection on y over the lower half of the circle, left of centre.
    func signedDistance(_ y: Double) -> Double {
        let x = xOnLine(a, b, atY: y) + offset
        return hypot(x - ringCentre.x, y - ringCentre.y) - radius
    }
    var low = ringCentre.y, high = ringCentre.y + radius
    let lowSign = signedDistance(low) > 0
    for _ in 0..<60 {
        let middle = (low + high) / 2
        if (signedDistance(middle) > 0) == lowSign { low = middle } else { high = middle }
    }
    let y = (low + high) / 2
    return Point(x: xOnLine(a, b, atY: y) + offset, y: y)
}

let strokeLeftEdge = (strokePolygon[0], strokePolygon[3])
let strokeRightEdge = (strokePolygon[1], strokePolygon[2])
let leftCutOuter = cutPoint(edgeFrom: strokeLeftEdge.0, to: strokeLeftEdge.1, offset: -ringGapLeftOfStroke, radius: ringOuterRadius)
let leftCutInner = cutPoint(edgeFrom: strokeLeftEdge.0, to: strokeLeftEdge.1, offset: -ringGapLeftOfStroke, radius: ringInnerRadius)
let bottomCutOuter = cutPoint(edgeFrom: strokeRightEdge.0, to: strokeRightEdge.1, offset: ringGapRightOfStroke, radius: ringOuterRadius)
let bottomCutInner = cutPoint(edgeFrom: strokeRightEdge.0, to: strokeRightEdge.1, offset: ringGapRightOfStroke, radius: ringInnerRadius)

// MARK: - Path data shared by SVG and Android VectorDrawable

func number(_ value: Double) -> String {
    let rounded = (value * 10).rounded() / 10
    return rounded == rounded.rounded() ? String(Int(rounded)) : String(rounded)
}

/// Converts a source point into the mark's viewBox, then into a target box of
/// `size` units offset by `inset` (used to place the mark inside the launcher icon).
struct Placement { var scale: Double; var offsetX: Double; var offsetY: Double }
let viewBoxPlacement = Placement(scale: 1, offsetX: 0, offsetY: 0)

func place(_ p: Point, _ placement: Placement) -> String {
    let x = (p.x - viewBoxOrigin.x) * placement.scale + placement.offsetX
    let y = (p.y - viewBoxOrigin.y) * placement.scale + placement.offsetY
    return "\(number(x)),\(number(y))"
}

func polygonPath(_ points: [Point], _ placement: Placement) -> String {
    "M" + points.map { place($0, placement) }.joined(separator: " L") + " Z"
}

/// Outer arc the long way round (clockwise on screen) from the left cut to the
/// bottom cut, across the cut, then the inner arc back.
func ringPath(_ placement: Placement) -> String {
    let outer = number(ringOuterRadius * placement.scale)
    let inner = number(ringInnerRadius * placement.scale)
    return "M\(place(leftCutOuter, placement))"
        + " A\(outer),\(outer) 0 1,1 \(place(bottomCutOuter, placement))"
        + " L\(place(bottomCutInner, placement))"
        + " A\(inner),\(inner) 0 1,0 \(place(leftCutInner, placement)) Z"
}

/// One path per shape, so overlapping shapes never cancel out under a fill rule.
func markPaths(_ placement: Placement) -> [String] {
    [ringPath(placement), polygonPath(letterAPolygon, placement),
     polygonPath(strokePolygon, placement), polygonPath(letterVPolygon, placement)]
}

// MARK: - File writers

let repositoryRoot = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
func url(_ relativePath: String) -> URL { repositoryRoot.appendingPathComponent(relativePath) }

func write(_ text: String, to relativePath: String) throws {
    let destination = url(relativePath)
    try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
    try text.write(to: destination, atomically: true, encoding: .utf8)
    print("wrote \(relativePath)")
}

let generatedNote = "Generated by brand/generate-brand-assets.swift from brand/source/; do not edit by hand."

func markSVG() -> String {
    let paths = markPaths(viewBoxPlacement).map { "  <path d=\"\($0)\"/>" }.joined(separator: "\n")
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <!-- Aven-mo mark. \(generatedNote) -->
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 \(number(viewBoxSize)) \(number(viewBoxSize))" width="\(number(viewBoxSize))" height="\(number(viewBoxSize))">
    <g fill="\(markNavyHex)">
    \(paths)
    </g>
    </svg>

    """
}

func vectorDrawable(size: String, viewport: Double, comment: String, pathsXML: String, extraNamespace: String = "") -> String {
    """
    <?xml version="1.0" encoding="utf-8"?>
    <!-- \(comment) \(generatedNote) -->
    <vector xmlns:android="http://schemas.android.com/apk/res/android"\(extraNamespace)
        android:width="\(size)"
        android:height="\(size)"
        android:viewportWidth="\(number(viewport))"
        android:viewportHeight="\(number(viewport))">
    \(pathsXML)
    </vector>

    """
}

func pathXML(_ data: String, fill: String) -> String {
    """
        <path
            android:fillColor="\(fill)"
            android:pathData="\(data)" />
    """
}

/// Adaptive icons are 108 dp; launchers show the middle 72 dp and only promise
/// the middle 66 dp circle survives every mask. The mark is scaled so its
/// farthest point from the icon centre stays inside that circle with a margin.
let adaptiveIconSize = 108.0
let launcherSafeRadius = 33.0
let launcherMarginInSafeZone = 2.0

func launcherPlacement() -> Placement {
    let corners = letterAPolygon + strokePolygon + letterVPolygon
        + [Point(x: ringCentre.x - ringOuterRadius, y: ringCentre.y), Point(x: ringCentre.x + ringOuterRadius, y: ringCentre.y),
           Point(x: ringCentre.x, y: ringCentre.y - ringOuterRadius), Point(x: ringCentre.x, y: ringCentre.y + ringOuterRadius)]
    // Centre the mark's bounding box in the icon.
    let minX = corners.map(\.x).min()!, maxX = corners.map(\.x).max()!
    let minY = corners.map(\.y).min()!, maxY = corners.map(\.y).max()!
    let centre = Point(x: (minX + maxX) / 2, y: (minY + maxY) / 2)
    let farthest = corners.map { hypot($0.x - centre.x, $0.y - centre.y) }.max()!
    let scale = (launcherSafeRadius - launcherMarginInSafeZone) / farthest
    return Placement(scale: scale,
                     offsetX: adaptiveIconSize / 2 - (centre.x - viewBoxOrigin.x) * scale,
                     offsetY: adaptiveIconSize / 2 - (centre.y - viewBoxOrigin.y) * scale)
}

/// Light, slightly blue mark like the glossy icon's, white at the top.
func launcherForeground() -> String {
    let placement = launcherPlacement()
    let gradient = """
            <aapt:attr name="android:fillColor">
                <gradient
                    android:type="linear"
                    android:startX="54" android:startY="18"
                    android:endX="54" android:endY="90"
                    android:startColor="#FFFFFFFF"
                    android:endColor="#FFB9D6F7" />
            </aapt:attr>
    """
    let paths = markPaths(placement).map { data in
        """
            <path android:pathData="\(data)">
        \(gradient)
            </path>
        """
    }.joined(separator: "\n")
    return vectorDrawable(size: "108dp", viewport: adaptiveIconSize,
                          comment: "Adaptive launcher icon foreground: the Aven-mo mark inside the 66 dp safe zone.",
                          pathsXML: paths, extraNamespace: "\n    xmlns:aapt=\"http://schemas.android.com/aapt\"")
}

/// Navy with a brighter blue centre, sampled from the glossy icon
/// (edges #002458, glow area #00469C).
func launcherBackground() -> String {
    let path = """
        <path android:pathData="M0,0 H108 V108 H0 Z">
            <aapt:attr name="android:fillColor">
                <gradient
                    android:type="radial"
                    android:centerX="54" android:centerY="54"
                    android:gradientRadius="76"
                    android:startColor="#FF0A4FB0"
                    android:endColor="#FF00214F" />
            </aapt:attr>
        </path>
    """
    return vectorDrawable(size: "108dp", viewport: adaptiveIconSize,
                          comment: "Adaptive launcher icon background, matching the glossy icon's navy.",
                          pathsXML: path, extraNamespace: "\n    xmlns:aapt=\"http://schemas.android.com/aapt\"")
}

// MARK: - Raster helpers

func loadImage(_ relativePath: String) -> CGImage {
    guard let source = CGImageSourceCreateWithURL(url(relativePath) as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fatalError("cannot read \(relativePath)")
    }
    return image
}

func makeContext(width: Int, height: Int, hasAlpha: Bool) -> CGContext {
    CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
              space: CGColorSpace(name: CGColorSpace.sRGB)!,
              bitmapInfo: hasAlpha ? CGImageAlphaInfo.premultipliedLast.rawValue : CGImageAlphaInfo.noneSkipLast.rawValue)!
}

func writePNG(_ image: CGImage, to relativePath: String) throws {
    let destinationURL = url(relativePath)
    try FileManager.default.createDirectory(at: destinationURL.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let destination = CGImageDestinationCreateWithURL(destinationURL as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fatalError("cannot write \(relativePath)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("cannot write \(relativePath)") }
    print("wrote \(relativePath)")
}

/// Icon master: the glossy icon's opaque rounded square, upscaled to fill
/// 1024×1024 over the navy it fades to at its edges.
let iconMasterSize = 1024
let glossyIconOpaqueBounds = CGRect(x: 22, y: 20, width: 456, height: 456) // measured on the 500×500 source, y up
let glossyIconEdgeNavy = CGColor(srgbRed: 0x00 / 255, green: 0x24 / 255, blue: 0x58 / 255, alpha: 1)

/// Alpha below this is the light halo the background removal left around the
/// rounded corners; those pixels become navy so no halo shows on the icon.
let glossyIconHaloAlphaThreshold: UInt8 = 250

func glossyIconWithoutHalo() -> CGImage {
    let glossy = loadImage("brand/source/aven-mo-icon-glossy-source.png")
    let context = makeContext(width: glossy.width, height: glossy.height, hasAlpha: true)
    context.draw(glossy, in: CGRect(x: 0, y: 0, width: glossy.width, height: glossy.height))
    let pixels = context.data!.bindMemory(to: UInt8.self, capacity: glossy.width * glossy.height * 4)
    let navy: [UInt8] = [0x00, 0x24, 0x58, 0xFF]
    for index in stride(from: 0, to: glossy.width * glossy.height * 4, by: 4) where pixels[index + 3] < glossyIconHaloAlphaThreshold {
        for channel in 0..<4 { pixels[index + channel] = navy[channel] }
    }
    return context.makeImage()!
}

func iconMaster() -> CGImage {
    let glossy = glossyIconWithoutHalo()
    let context = makeContext(width: iconMasterSize, height: iconMasterSize, hasAlpha: false)
    context.setFillColor(glossyIconEdgeNavy)
    context.fill(CGRect(x: 0, y: 0, width: iconMasterSize, height: iconMasterSize))
    context.interpolationQuality = .high
    // Scale the glossy image so its opaque square covers the whole canvas.
    let scale = Double(iconMasterSize) / glossyIconOpaqueBounds.width
    context.draw(glossy, in: CGRect(x: -glossyIconOpaqueBounds.minX * scale, y: -glossyIconOpaqueBounds.minY * scale,
                                    width: Double(glossy.width) * scale, height: Double(glossy.height) * scale))
    return context.makeImage()!
}

/// Fills the traced shapes over the source's size and compares with the
/// source's dark pixels: intersection over union, 1.0 being a perfect trace.
func traceAgreement() -> Double {
    let source = loadImage("brand/source/aven-mo-mark-source.png")
    let width = source.width, height = source.height
    func darkMask(_ draw: (CGContext) -> Void) -> [Bool] {
        let context = makeContext(width: width, height: height, hasAlpha: true)
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        // Flip so source coordinates (y down) draw the right way up.
        context.translateBy(x: 0, y: Double(height))
        context.scaleBy(x: 1, y: -1)
        draw(context)
        let pixels = context.data!.bindMemory(to: UInt8.self, capacity: width * height * 4)
        return (0..<(width * height)).map { Int(pixels[$0 * 4]) + Int(pixels[$0 * 4 + 1]) + Int(pixels[$0 * 4 + 2]) < 384 }
    }
    let sourceMask = darkMask { context in
        context.translateBy(x: 0, y: Double(height)); context.scaleBy(x: 1, y: -1)
        context.draw(source, in: CGRect(x: 0, y: 0, width: width, height: height))
    }
    let traceMask = darkMask { context in
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        for shape in [letterAPolygon, strokePolygon, letterVPolygon] {
            context.addLines(between: shape.map { CGPoint(x: $0.x, y: $0.y) }); context.closePath(); context.fillPath()
        }
        let angle = { (p: Point) in atan2(p.y - ringCentre.y, p.x - ringCentre.x) }
        let center = CGPoint(x: ringCentre.x, y: ringCentre.y)
        context.move(to: CGPoint(x: leftCutOuter.x, y: leftCutOuter.y))
        context.addArc(center: center, radius: ringOuterRadius, startAngle: angle(leftCutOuter), endAngle: angle(bottomCutOuter), clockwise: false)
        context.addLine(to: CGPoint(x: bottomCutInner.x, y: bottomCutInner.y))
        context.addArc(center: center, radius: ringInnerRadius, startAngle: angle(bottomCutInner), endAngle: angle(leftCutInner), clockwise: true)
        context.closePath(); context.fillPath()
    }
    var both = 0, either = 0
    for index in 0..<sourceMask.count where sourceMask[index] || traceMask[index] {
        either += 1
        if sourceMask[index] && traceMask[index] { both += 1 }
    }
    return Double(both) / Double(either)
}

// MARK: - Run

let svg = markSVG()
try write(svg, to: "brand/aven-mo-mark.svg")
try write(svg, to: "ios/SplitParty/Assets.xcassets/AvenMoMark.imageset/aven-mo-mark.svg")
try write(vectorDrawable(size: "48dp", viewport: viewBoxSize,
                         comment: "Aven-mo mark (same viewBox as brand/aven-mo-mark.svg); tinted where it is drawn.",
                         pathsXML: markPaths(viewBoxPlacement).map { pathXML($0, fill: "#FF000000") }.joined(separator: "\n")),
          to: "android/app/src/main/res/drawable/ic_aven_mo_mark.xml")
try write(launcherForeground(), to: "android/app/src/main/res/drawable/ic_launcher_foreground.xml")
try write(launcherBackground(), to: "android/app/src/main/res/drawable/ic_launcher_background.xml")

let master = iconMaster()
try writePNG(master, to: "brand/aven-mo-icon-1024.png")
try writePNG(master, to: "ios/SplitParty/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png")

print(String(format: "trace agreement with the source mark (intersection over union): %.3f", traceAgreement()))
