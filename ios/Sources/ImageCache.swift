import UIKit

/// LRU Image cache for thermal printer images.
/// Caches up to maxSize images, evicting least recently used when full.
/// Uses URL as key for caching.
internal class ImageCache {
    private let maxSize: Int
    private var cache: [String: UIImage] = [:]
    private var accessOrder: [String] = []
    private let lock = NSLock()

    public init(maxSize: Int = 10) {
        self.maxSize = maxSize
    }

    /// Cache an image from URL
    public func cacheFromUrl(_ url: String, key: String) async throws {
        guard let imageUrl = URL(string: url) else {
            throw ImageCacheError.invalidUrl
        }

        let (data, _) = try await URLSession.shared.data(from: imageUrl)

        guard let image = UIImage(data: data) else {
            throw ImageCacheError.invalidImageData
        }

        lock.lock()
        defer { lock.unlock() }

        // Evict oldest if at capacity
        if cache.count >= maxSize, let eldest = accessOrder.first {
            cache.removeValue(forKey: eldest)
            accessOrder.removeFirst()
        }

        cache[key] = image
        accessOrder.append(key)
    }

    /// Cache an image directly
    public func cacheImage(_ image: UIImage, key: String) {
        lock.lock()
        defer { lock.unlock() }

        // Evict oldest if at capacity
        if cache.count >= maxSize && !cache.keys.contains(key), let eldest = accessOrder.first {
            cache.removeValue(forKey: eldest)
            accessOrder.removeFirst()
        }

        // Update access order if key exists
        if let index = accessOrder.firstIndex(of: key) {
            accessOrder.remove(at: index)
        }

        cache[key] = image
        accessOrder.append(key)
    }

    /// Get a cached image by key
    public func get(key: String) -> UIImage? {
        lock.lock()
        defer { lock.unlock() }

        guard let image = cache[key] else { return nil }

        // Update access order (LRU)
        if let index = accessOrder.firstIndex(of: key) {
            accessOrder.remove(at: index)
            accessOrder.append(key)
        }

        return image
    }

    /// Check if an image is cached
    public func contains(key: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return cache.keys.contains(key)
    }

    /// Remove a cached image
    public func remove(key: String) {
        lock.lock()
        defer { lock.unlock() }

        cache.removeValue(forKey: key)
        if let index = accessOrder.firstIndex(of: key) {
            accessOrder.remove(at: index)
        }
    }

    /// Clear all cached images
    public func clear() {
        lock.lock()
        defer { lock.unlock() }

        cache.removeAll()
        accessOrder.removeAll()
    }

    /// Get current cache size
    public func size() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return cache.count
    }

    /// Download an image from URL
    public func downloadImage(from url: String) async throws -> UIImage {
        guard let imageUrl = URL(string: url) else {
            throw ImageCacheError.invalidUrl
        }

        let (data, _) = try await URLSession.shared.data(from: imageUrl)

        guard let image = UIImage(data: data) else {
            throw ImageCacheError.invalidImageData
        }

        return image
    }
}

/// Errors for ImageCache
internal enum ImageCacheError: Error, LocalizedError {
    case invalidUrl
    case invalidImageData
    case downloadFailed

    public var errorDescription: String? {
        switch self {
        case .invalidUrl:
            return "Invalid image URL"
        case .invalidImageData:
            return "Invalid image data"
        case .downloadFailed:
            return "Failed to download image"
        }
    }
}
