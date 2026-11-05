import AVFoundation
import SwiftUI
import UIKit
import Vision

/// "Scan receipt" (PoC-10): take a photo, read it on the phone with Apple Vision
/// while a laser line sweeps it, and hand the total line's amount to the host's
/// confirmation step. Nothing is written here: the host confirms first.
///
/// Privacy: the photo lives only in this view's memory. UIImagePickerController
/// never saves to the photo library, and the image is dropped as soon as the
/// text is read; it is never written to the party or to storage.
/// Measured values: docs/design/visual-conventions.md ("Receipt scan").
struct ReceiptScanView: View {
    let onDetected: (Int) -> Void
    /// Carries the message to show above the keypad.
    let onNoTotal: (String) -> Void
    let onEnterInstead: () -> Void
    let onCancel: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.openURL) private var openURL
    @State private var stage: Stage = .checkingCamera

    static let noTotalMessage = "We couldn't find a total on that receipt. Type it instead."
    static let unreadableMessage = "We couldn't read that photo. Type the total instead."
    /// Long enough to see the laser pass once, short enough to stay well inside
    /// the work order's 3 s from capture to confirmation.
    private static let minimumScanDuration: Duration = .milliseconds(700)

    enum Stage: Equatable {
        case checkingCamera
        case cameraUnavailable(CameraProblem)
        case capturing
        case reading(UIImage)
    }

    enum CameraProblem: Equatable {
        case noCamera
        case denied

        var title: String {
            switch self {
            case .noCamera: "No camera available"
            case .denied: "Camera access is off"
            }
        }

        var message: String {
            switch self {
            case .noCamera: "This device can't take a photo of the receipt. You can type the total instead."
            case .denied: "Split Party needs the camera to read the receipt. Turn it on in Settings, or type the total instead."
            }
        }
    }

    var body: some View {
        NavigationStack {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(stageBackground.ignoresSafeArea())
                .navigationTitle("Scan receipt")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", action: onCancel)
                    }
                }
        }
        .task { await prepareCamera() }
        .accessibilityIdentifier("receiptScanScreen")
    }

    private var stageBackground: Color {
        if case .reading = stage { return .black }
        return Theme.background
    }

    @ViewBuilder
    private var content: some View {
        switch stage {
        case .checkingCamera:
            ProgressView()
        case let .cameraUnavailable(problem):
            CameraUnavailableView(problem: problem, onEnterTotal: onEnterInstead, onOpenSettings: {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            })
        case .capturing:
            CameraCapture(onCaptured: { image in startReading(image) }, onCancel: onCancel)
                .ignoresSafeArea()
        case let .reading(image):
            ScanningView(image: image, showsLaser: !reduceMotion)
        }
    }

    // MARK: Camera

    private func prepareCamera() async {
        guard stage == .checkingCamera else { return }
        #if DEBUG
        if let launch = ReceiptScanTestLaunch.fromProcessArguments() {
            switch launch {
            case .cameraDenied: stage = .cameraUnavailable(.denied)
            case let .receipt(lines): startReading(SyntheticReceipt.render(lines: lines))
            }
            return
        }
        #endif
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            stage = .cameraUnavailable(.noCamera)
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            stage = .capturing
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            stage = granted ? .capturing : .cameraUnavailable(.denied)
        default:
            stage = .cameraUnavailable(.denied)
        }
    }

    // MARK: Reading

    private func startReading(_ image: UIImage) {
        stage = .reading(image)
        UIAccessibility.post(notification: .announcement, argument: "Reading the receipt")
        Task {
            let started = ContinuousClock.now
            let pieces = await ReceiptTextRecognizer.recognize(image)
            var minimum = Self.minimumScanDuration
            #if DEBUG
            // Captures hold the laser on screen long enough to photograph it.
            if let hold = UserDefaults.standard.object(forKey: "UITestScanHold") as? String, let seconds = Double(hold) {
                minimum = .seconds(seconds)
            }
            #endif
            let remaining = minimum - (ContinuousClock.now - started)
            if remaining > .zero { try? await Task.sleep(for: remaining) }
            // Drop the photo before anything else happens with the result.
            stage = .checkingCamera
            if pieces.isEmpty {
                onNoTotal(Self.unreadableMessage)
            } else if let cents = ReceiptTotalDetector.totalCents(in: pieces) {
                onDetected(cents)
            } else {
                onNoTotal(Self.noTotalMessage)
            }
        }
    }
}

// MARK: Recognition

/// Apple Vision text recognition, on the device only.
enum ReceiptTextRecognizer {
    static func recognize(_ image: UIImage) async -> [RecognizedText] {
        guard let cgImage = image.cgImage else { return [] }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        return await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["en-US"]
            // Language correction "fixes" prices into words; receipts are mostly numbers.
            request.usesLanguageCorrection = false
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation)
            do {
                try handler.perform([request])
            } catch {
                return []
            }
            return (request.results ?? []).compactMap { observation in
                guard let candidate = observation.topCandidates(1).first else { return nil }
                return RecognizedText(text: candidate.string, box: observation.boundingBox)
            }
        }.value
    }
}

private extension CGImagePropertyOrientation {
    init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        case .upMirrored: self = .upMirrored
        case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

// MARK: Views

/// The photo with a brand-blue laser line sweeping down and up while Vision
/// reads it. With Reduce Motion there is no sweep, just the status.
private struct ScanningView: View {
    let image: UIImage
    let showsLaser: Bool

    @State private var isAtBottom = false

    var body: some View {
        VStack(spacing: 18) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay {
                    if showsLaser {
                        GeometryReader { proxy in
                            LaserLine()
                                .frame(height: Theme.laserGlowHeight)
                                .position(x: proxy.size.width / 2,
                                          y: isAtBottom ? proxy.size.height : 0)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .accessibilityIdentifier("laserLine")
                    }
                }
                .accessibilityHidden(true)
            HStack(spacing: 10) {
                ProgressView().tint(.white)
                Text("Reading the receipt…")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("scanStatus")
        }
        .padding(20)
        .onAppear {
            guard showsLaser else { return }
            withAnimation(.easeInOut(duration: Theme.laserSweepDuration).repeatForever(autoreverses: true)) {
                isAtBottom = true
            }
        }
    }
}

/// A 3 pt bright line with a soft glow above and below it.
private struct LaserLine: View {
    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.brandBlue.opacity(0), Theme.brandBlue.opacity(0.35), Theme.brandBlue.opacity(0)],
                           startPoint: .top, endPoint: .bottom)
            Rectangle()
                .fill(Color(hex: 0x6CC4FF))
                .frame(height: Theme.laserLineHeight)
                .shadow(color: Theme.brandBlue, radius: 6)
        }
    }
}

private struct CameraUnavailableView: View {
    let problem: ReceiptScanView.CameraProblem
    let onEnterTotal: () -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "camera.slash")
                .font(.system(size: 52))
                .foregroundStyle(Theme.secondaryText)
                .accessibilityHidden(true)
            Text(problem.title)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.primaryText)
                .multilineTextAlignment(.center)
            Text(problem.message)
                .font(.system(size: 17))
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            Button("Enter total", action: onEnterTotal)
                .buttonStyle(PillButtonStyle())
                .padding(.top, 12)
                .accessibilityIdentifier("cameraUnavailableEnterTotal")
            if problem == .denied {
                Button("Open Settings", action: onOpenSettings)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.brandBlue)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 32)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("cameraUnavailable")
    }
}

/// The system camera. It hands back the photo in memory and never saves it.
private struct CameraCapture: UIViewControllerRepresentable {
    let onCaptured: (UIImage) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ picker: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCaptured: onCaptured, onCancel: onCancel)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCaptured: (UIImage) -> Void
        let onCancel: () -> Void

        init(onCaptured: @escaping (UIImage) -> Void, onCancel: @escaping () -> Void) {
            self.onCaptured = onCaptured
            self.onCancel = onCancel
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage {
                onCaptured(image)
            } else {
                onCancel()
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCancel()
        }
    }
}

#if DEBUG
/// UI tests and captures run on simulators without a camera: the launch
/// environment variable `UITestReceipt="TOTAL\t$51.58|..."` renders those lines (one per "|", label and
/// amount split by a tab) as a receipt image in code and reads it with the real
/// Vision pipeline; `-UITestReceipt blank` is a blank page; `-UITestCameraDenied YES`
/// behaves as if camera permission were off; `-UITestScanHold <seconds>` keeps
/// the laser sweeping that long (captures). Debug builds only; no receipt
/// photo is ever bundled.
enum ReceiptScanTestLaunch {
    case receipt(lines: [String])
    case cameraDenied

    static func fromProcessArguments(_ defaults: UserDefaults = .standard) -> ReceiptScanTestLaunch? {
        if defaults.bool(forKey: "UITestCameraDenied") { return .cameraDenied }
        // An environment variable, not a "-key value" argument: argument values are
        // parsed as property lists, which mangles spaces and apostrophes.
        guard let raw = ProcessInfo.processInfo.environment["UITestReceipt"] else { return nil }
        return .receipt(lines: raw == "blank" ? [] : raw.components(separatedBy: "|"))
    }
}

/// Draws receipt-like text (black monospaced on white) for the Debug scan path.
enum SyntheticReceipt {
    static func render(lines: [String]) -> UIImage {
        let size = CGSize(width: 600, height: 900)
        let font = UIFont.monospacedSystemFont(ofSize: 30, weight: .semibold)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.black]
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            for (index, line) in lines.enumerated() {
                let y = 60 + CGFloat(index) * 56
                let parts = line.components(separatedBy: "\t")
                (parts[0] as NSString).draw(at: CGPoint(x: 40, y: y), withAttributes: attributes)
                if parts.count > 1 {
                    let amount = parts[1] as NSString
                    let width = amount.size(withAttributes: attributes).width
                    amount.draw(at: CGPoint(x: size.width - 40 - width, y: y), withAttributes: attributes)
                }
            }
        }
    }
}
#endif
