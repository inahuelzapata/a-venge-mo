import SwiftUI
import XCTest
@testable import SplitParty

/// Avatar bubbles drawn from `users.avatar` (docs/design/visual-conventions.md):
/// which values mean a photo, that photos load, crop and survive going offline,
/// and that anything else falls back to initials.
final class AvatarSpecTests: XCTestCase {
    private let maxiPhotoURL = "https://taller.ai/_next/image?url=%2Fleadership%2Fmaximiliano-armesto.jpg&w=1080&q=75"
    private let anniePhotoURL = "https://taller.ai/_next/image?url=%2Fleadership%2Fannie-rodriguez.jpg&w=1080&q=75"
    private let christophePhotoURL = "https://taller.ai/_next/image?url=%2Fleadership%2Fchristophe-kolb.jpg&w=1080&q=75"

    /// What the seed stores now: the person's URL, read straight from the user document.
    func testSeededPhotoURLFromUserDocumentResolvesToThatPersonsPhotoCropAndColour() throws {
        let user = try XCTUnwrap(UserAccount(id: "maxi", data: [
            "username": "maxi", "displayName": "Maxi", "avatar": maxiPhotoURL,
            "balanceCents": NSNumber(value: 25000), "friendIds": ["annie", "christophe"],
        ]))
        let spec = AvatarPhotos.spec(for: user.avatarKey)
        XCTAssertEqual(spec.url?.absoluteString, maxiPhotoURL)
        XCTAssertEqual(spec.crop, AvatarCrop(centerX: 0.50, centerY: 0.40, side: 0.70))
        XCTAssertEqual(spec.initialsColor, Color(hex: 0x0087E0))

        XCTAssertEqual(AvatarPhotos.spec(for: anniePhotoURL).crop, AvatarCrop(centerX: 0.55, centerY: 0.40, side: 0.70))
        XCTAssertEqual(AvatarPhotos.spec(for: christophePhotoURL).crop, AvatarCrop(centerX: 0.50, centerY: 0.35, side: 1.00))
    }

    /// Fixtures and older data carry the key; it means the same photo.
    func testLegacyKeyMeansTheSamePhoto() {
        XCTAssertEqual(AvatarPhotos.spec(for: "avatar_annie"), AvatarPhotos.spec(for: anniePhotoURL))
        XCTAssertEqual(AvatarPhotos.spec(for: "avatar_christophe"), AvatarPhotos.spec(for: christophePhotoURL))
    }

    func testOtherHttpsURLLoadsWithCentredCropOverGrey() {
        let spec = AvatarPhotos.spec(for: "https://example.com/julie.jpg")
        XCTAssertEqual(spec.url?.absoluteString, "https://example.com/julie.jpg")
        XCTAssertEqual(spec.crop, .centered)
        XCTAssertEqual(spec.initialsColor, AvatarPhotos.fallbackColor)
    }

    func testMissingEmptyOrNonURLValuesAreGreyInitialsWithNothingToFetch() {
        for value: String? in [nil, "", "   ", "avatar_julie", "not a url", "http://taller.ai/photo.jpg", "https://", "ftp://x/y.jpg"] {
            let spec = AvatarPhotos.spec(for: value)
            XCTAssertNil(spec.url, "\(String(describing: value)) must not fetch anything")
            XCTAssertEqual(spec.initialsColor, AvatarPhotos.fallbackColor, "\(String(describing: value)) must be grey")
        }
    }

    /// The pixel squares for the real photo sizes. 720 × 0.70 truncates to 503 in
    /// Double (the design doc and Android's Float math give 504): one pixel of the
    /// source, invisible once scaled to the bubble.
    func testCropRectsForTheRealPhotoSizes() {
        func pixels(_ width: CGFloat, _ height: CGFloat, _ avatarValue: String) -> CGRect {
            AvatarImageStore.cropRect(imageSize: CGSize(width: width, height: height),
                                      crop: AvatarPhotos.spec(for: avatarValue).crop)
        }
        XCTAssertEqual(pixels(1080, 720, maxiPhotoURL), CGRect(x: 288, y: 36, width: 503, height: 503))
        XCTAssertEqual(pixels(1080, 720, anniePhotoURL), CGRect(x: 342, y: 36, width: 503, height: 503))
        XCTAssertEqual(pixels(667, 1000, christophePhotoURL), CGRect(x: 0, y: 16, width: 667, height: 667))
    }
}

@MainActor
final class AvatarImageStoreTests: XCTestCase {
    private let photoURL = URL(string: "https://taller.ai/_next/image?url=%2Fleadership%2Fannie-rodriguez.jpg&w=1080&q=75")!
    private var directory: URL!

    override func setUp() async throws {
        directory = FileManager.default.temporaryDirectory.appending(path: "AvatarPhotoTests-\(UUID().uuidString)")
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: directory)
    }

    func testLoadsPhotoCroppedToSquareAndKeepsItInMemory() async throws {
        let fetches = FetchCounter()
        let store = AvatarImageStore(directory: directory) { _ in
            await fetches.increment()
            return Self.solidJPEG(width: 1080, height: 720, color: .red)
        }

        let loadedPhoto = await store.loadPhoto(url: photoURL, crop: .centered)
        let photo = try XCTUnwrap(loadedPhoto)
        XCTAssertEqual(photo.size, CGSize(width: AvatarImageStore.pixelSize, height: AvatarImageStore.pixelSize))
        XCTAssertNotNil(store.cachedPhoto(url: photoURL, crop: .centered))
        _ = await store.loadPhoto(url: photoURL, crop: .centered)
        let fetchCount = await fetches.count
        XCTAssertEqual(fetchCount, 1)
    }

    /// Airplane Mode after one online load: a fresh store (next launch) whose
    /// network always fails still serves the photo from disk.
    func testPhotoLoadedOnceStillShowsWhenOffline() async throws {
        let onlineStore = AvatarImageStore(directory: directory) { _ in Self.solidJPEG(width: 667, height: 1000, color: .blue) }
        let onlinePhoto = await onlineStore.loadPhoto(url: photoURL, crop: .centered)
        XCTAssertNotNil(onlinePhoto)

        let offlineStore = AvatarImageStore(directory: directory) { _ in throw URLError(.notConnectedToInternet) }
        XCTAssertNotNil(offlineStore.cachedPhoto(url: photoURL, crop: .centered))
        let photo = await offlineStore.loadPhoto(url: photoURL, crop: .centered)
        XCTAssertNotNil(photo)
    }

    func testFailedOrUndecodableFetchGivesNoPhotoAndIsRetried() async throws {
        let failingStore = AvatarImageStore(directory: directory) { _ in throw URLError(.timedOut) }
        let failedPhoto = await failingStore.loadPhoto(url: photoURL, crop: .centered)
        XCTAssertNil(failedPhoto)

        let garbageStore = AvatarImageStore(directory: directory) { _ in Data("<html>not a photo</html>".utf8) }
        let garbagePhoto = await garbageStore.loadPhoto(url: photoURL, crop: .centered)
        XCTAssertNil(garbagePhoto)

        // Failures are not remembered: once the network answers, the photo loads.
        let recoveredStore = AvatarImageStore(directory: directory) { _ in Self.solidJPEG(width: 100, height: 100, color: .green) }
        let recoveredPhoto = await recoveredStore.loadPhoto(url: photoURL, crop: .centered)
        XCTAssertNotNil(recoveredPhoto)
    }

    // MARK: - The bubble itself

    func testBubbleShowsThePhotoFromUsersAvatar() async throws {
        let store = AvatarImageStore(directory: directory) { _ in Self.solidJPEG(width: 1080, height: 720, color: .red) }
        let loadedPhoto = await store.loadPhoto(url: photoURL, crop: AvatarPhotos.spec(for: photoURL.absoluteString).crop)
        XCTAssertNotNil(loadedPhoto)

        let center = try centerPixel(of: AvatarView(avatarKey: photoURL.absoluteString, displayName: "Annie", size: 80)
            .environment(\.avatarImageStore, store))
        XCTAssertGreaterThan(center.red, 0.9)
        XCTAssertLessThan(center.green, 0.1)
    }

    /// Missing, empty or non-URL values (and no photo store at all) draw initials
    /// on the grey circle; a seeded photo that hasn't loaded shows the person's colour.
    func testBubbleFallsBackToInitials() throws {
        let store = AvatarImageStore(directory: directory) { _ in throw URLError(.notConnectedToInternet) }
        for value in ["", "not a url", "avatar_julie"] {
            let corner = try pixel(of: AvatarView(avatarKey: value, displayName: "Julie Wang", size: 80)
                .environment(\.avatarImageStore, store), x: 0.5, y: 0.15)
            XCTAssertEqual(corner.red, 0x8E / 255, accuracy: 0.03, "\(value) should be grey")
            XCTAssertEqual(corner.blue, 0x93 / 255, accuracy: 0.03, "\(value) should be grey")
        }
        let notLoadedYet = try pixel(of: AvatarView(avatarKey: photoURL.absoluteString, displayName: "Annie", size: 80)
            .environment(\.avatarImageStore, store), x: 0.5, y: 0.15)
        XCTAssertEqual(notLoadedYet.red, 0xE5 / 255, accuracy: 0.03, "Annie's initials colour while there is no photo")
    }

    // MARK: - Helpers

    private actor FetchCounter {
        private(set) var count = 0
        func increment() { count += 1 }
    }

    private nonisolated static func solidJPEG(width: CGFloat, height: CGFloat, color: UIColor) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).jpegData(withCompressionQuality: 1) { context in
            color.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    private struct RGB { let red: CGFloat; let green: CGFloat; let blue: CGFloat }

    private func centerPixel(of view: some View) throws -> RGB { try pixel(of: view, x: 0.5, y: 0.5) }

    /// One pixel of the rendered view, at fractions of its width and height.
    private func pixel(of view: some View, x: CGFloat, y: CGFloat) throws -> RGB {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.cgImage)
        var rgba = [UInt8](repeating: 0, count: 4)
        let context = try XCTUnwrap(CGContext(data: &rgba, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                               space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                               bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        let pixelX = CGFloat(image.width) * x
        let pixelY = CGFloat(image.height) * y
        // Draw the image shifted so the wanted pixel lands on the 1×1 context (origin bottom-left).
        context.draw(image, in: CGRect(x: -pixelX, y: -(CGFloat(image.height) - pixelY), width: CGFloat(image.width), height: CGFloat(image.height)))
        return RGB(red: CGFloat(rgba[0]) / 255, green: CGFloat(rgba[1]) / 255, blue: CGFloat(rgba[2]) / 255)
    }
}
