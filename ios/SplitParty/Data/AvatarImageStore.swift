import CryptoKit
import Foundation
import Observation
import SwiftUI
import UIKit

/// Downloads the bytes of an avatar photo. Throws on any failure.
typealias AvatarPhotoFetcher = @Sendable (URL) async throws -> Data

/// Avatar photos, fetched once and kept on the phone (docs/design/visual-conventions.md).
///
/// Each photo is cropped to its square and scaled to `pixelSize` once, at download
/// time, then written to `directory` (Application Support, which iOS never evicts,
/// unlike Caches). After that it is read from disk, and from memory within a run,
/// with no network call, so the demo works in Airplane Mode once each photo has
/// loaded once. Failures are not remembered; the next `loadPhoto` tries again.
///
/// Observable so a bubble drawn before its photo arrived redraws when it does.
@MainActor
@Observable
final class AvatarImageStore {
    /// The largest bubble is 80 pt (Me header); 4× leaves headroom over 3× screens
    /// and matches Android's 320 px files.
    nonisolated static let pixelSize = 320
    nonisolated private static let jpegQuality: CGFloat = 0.9
    nonisolated private static let requestTimeout: TimeInterval = 10
    /// Guards against a wrong URL serving something huge instead of a photo.
    nonisolated private static let maximumDownloadBytes = 5_000_000
    nonisolated private static let cacheKeyLength = 32

    /// The app's store. Previews and snapshot tests don't get it, so they draw initials.
    static let shared = AvatarImageStore(directory: defaultDirectory)

    private static var defaultDirectory: URL {
        URL.applicationSupportDirectory.appending(path: "AvatarPhotos", directoryHint: .isDirectory)
    }

    private let directory: URL
    private let fetcher: AvatarPhotoFetcher
    private var photosByCacheKey: [String: UIImage] = [:]
    @ObservationIgnored private var inFlightLoads: [String: Task<UIImage?, Never>] = [:]

    init(directory: URL, fetcher: @escaping AvatarPhotoFetcher = { try await AvatarImageStore.downloadPhoto(from: $0) }) {
        self.directory = directory
        self.fetcher = fetcher
    }

    /// The photo if it is in memory or on disk; never touches the network. Safe to
    /// call while a view draws: a disk hit is returned without changing state.
    func cachedPhoto(url: URL, crop: AvatarCrop) -> UIImage? {
        let cacheKey = Self.cacheKey(url: url, crop: crop)
        return photosByCacheKey[cacheKey] ?? Self.readFromDisk(fileURL(for: cacheKey))
    }

    /// The photo from memory, disk or the network, in that order; nil when it can't be shown.
    func loadPhoto(url: URL, crop: AvatarCrop) async -> UIImage? {
        let cacheKey = Self.cacheKey(url: url, crop: crop)
        if let photo = photosByCacheKey[cacheKey] { return photo }
        if let pendingLoad = inFlightLoads[cacheKey] { return await pendingLoad.value }

        let fileURL = fileURL(for: cacheKey)
        let fetcher = fetcher
        let directory = directory
        // Download, decode and disk work happen off the main thread.
        let load = Task.detached(priority: .utility) { () -> UIImage? in
            if let stored = Self.readFromDisk(fileURL) { return stored }
            guard let data = try? await fetcher(url), let square = Self.squarePhoto(from: data, crop: crop) else {
                return nil // Any failure: the bubble keeps its initials.
            }
            Self.writeToDisk(square, at: fileURL, in: directory)
            return square
        }
        inFlightLoads[cacheKey] = load
        let photo = await load.value
        inFlightLoads[cacheKey] = nil
        if let photo { photosByCacheKey[cacheKey] = photo }
        return photo
    }

    private func fileURL(for cacheKey: String) -> URL {
        directory.appending(path: "\(cacheKey).jpg", directoryHint: .notDirectory)
    }

    // MARK: - Work that runs off the main thread

    /// Crop and size are part of the key, so a changed crop gets a new file.
    nonisolated static func cacheKey(url: URL, crop: AvatarCrop) -> String {
        let identity = "\(url.absoluteString)|\(crop.centerX)|\(crop.centerY)|\(crop.side)|\(pixelSize)"
        let digest = SHA256.hash(data: Data(identity.utf8))
        return String(digest.map { String(format: "%02x", $0) }.joined().prefix(cacheKeyLength))
    }

    nonisolated static func downloadPhoto(from url: URL) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: requestTimeout)
        // Our own disk copy is the offline cache; don't let URLCache answer for the network.
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        guard !data.isEmpty, data.count <= maximumDownloadBytes else { throw URLError(.cannotDecodeContentData) }
        return data
    }

    /// Decodes `data`, cuts out `crop` and scales it to `pixelSize` square. Drawing
    /// through UIImage applies the photo's EXIF orientation before cropping.
    nonisolated static func squarePhoto(from data: Data, crop: AvatarCrop) -> UIImage? {
        guard let photo = UIImage(data: data), photo.size.width >= 1, photo.size.height >= 1 else { return nil }
        let cropRect = cropRect(imageSize: photo.size, crop: crop)
        let outputSide = CGFloat(pixelSize)
        let scale = outputSide / cropRect.width
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: outputSide, height: outputSide), format: format)
        return renderer.image { _ in
            photo.draw(in: CGRect(x: -cropRect.minX * scale, y: -cropRect.minY * scale,
                                  width: photo.size.width * scale, height: photo.size.height * scale))
        }
    }

    /// The crop's square in the image's coordinates, moved back inside the image
    /// if it would stick out. Same rule as Android's `AvatarImageStore.cropRect`.
    nonisolated static func cropRect(imageSize: CGSize, crop: AvatarCrop) -> CGRect {
        let minimumSideFraction = 0.01
        let sideFraction = min(max(crop.side, minimumSideFraction), 1)
        let side = max((min(imageSize.width, imageSize.height) * sideFraction).rounded(.down), 1)
        let left = min(max((imageSize.width * crop.centerX - side / 2).rounded(.towardZero), 0), imageSize.width - side)
        let top = min(max((imageSize.height * crop.centerY - side / 2).rounded(.towardZero), 0), imageSize.height - side)
        return CGRect(x: left, y: top, width: side, height: side)
    }

    nonisolated private static func readFromDisk(_ fileURL: URL) -> UIImage? {
        guard let data = try? Data(contentsOf: fileURL) else { return nil }
        guard let photo = UIImage(data: data) else {
            try? FileManager.default.removeItem(at: fileURL) // Corrupt file: fetch again next time.
            return nil
        }
        return photo
    }

    /// A failed write still shows the photo this run; the next run fetches again.
    nonisolated private static func writeToDisk(_ photo: UIImage, at fileURL: URL, in directory: URL) {
        guard let data = photo.jpegData(compressionQuality: jpegQuality) else { return }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var excludedFromBackup = URLResourceValues()
            excludedFromBackup.isExcludedFromBackup = true // Re-downloadable, so keep it out of iCloud backups.
            var backupDirectory = directory
            try? backupDirectory.setResourceValues(excludedFromBackup)
            try data.write(to: fileURL, options: .atomic)
        } catch {
            return
        }
    }
}

extension AvatarImageStore {
    nonisolated private static let firstRetryDelay: Duration = .seconds(5)
    nonisolated private static let maximumRetryDelay: Duration = .seconds(60)

    /// Loads the photo, retrying while it can't be fetched (offline, server down)
    /// after 5 s, doubling up to 60 s, as Android does. Meant for a view's `.task`,
    /// so it stops when the bubble leaves the screen.
    func keepLoadingPhoto(url: URL, crop: AvatarCrop) async {
        var retryDelay = Self.firstRetryDelay
        while !Task.isCancelled {
            if await loadPhoto(url: url, crop: crop) != nil { return }
            try? await Task.sleep(for: retryDelay)
            retryDelay = min(retryDelay * 2, Self.maximumRetryDelay)
        }
    }
}

extension EnvironmentValues {
    /// Where avatar photos come from. Nil (previews, snapshot tests) draws
    /// initials only; the app root provides `AvatarImageStore.shared`.
    @Entry var avatarImageStore: AvatarImageStore? = nil
}
