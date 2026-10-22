import AVFoundation
import CoreImage
import Photos
import SwiftUI
import UIKit

final class CameraModel: NSObject, ObservableObject {
    enum Auth { case unknown, authorized, denied, noCamera }

    @Published var auth: Auth = .unknown
    @Published var frame: CGImage?
    @Published var filter: PhotoFilter = .none
    @Published var shots: [UIImage] = []
    @Published var message: String?
    @Published var busy = false
    @Published var position: AVCaptureDevice.Position = .back

    private let session = AVCaptureSession()
    private let videoOut = AVCaptureVideoDataOutput()
    private let photoOut = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "camera")
    private let ctx = CIContext()
    private var currentFilter: PhotoFilter = .none
    private var configured = false

    var canSwitch: Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) != nil &&
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    func setFilter(_ f: PhotoFilter) { filter = f; queue.async { self.currentFilter = f } }

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: auth = .authorized; configureAndRun()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    self.auth = ok ? .authorized : .denied
                    if ok { self.configureAndRun() }
                }
            }
        default: auth = .denied
        }
    }

    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }

    private func configureAndRun() {
        queue.async {
            if !self.configured {
                self.session.beginConfiguration()
                self.session.sessionPreset = .photo
                self.videoOut.alwaysDiscardsLateVideoFrames = true
                self.videoOut.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                self.videoOut.setSampleBufferDelegate(self, queue: self.queue)
                if self.session.canAddOutput(self.videoOut) { self.session.addOutput(self.videoOut) }
                if self.session.canAddOutput(self.photoOut) { self.session.addOutput(self.photoOut) }
                self.session.commitConfiguration()
                self.configured = true
                if !self.setInput(self.position) {
                    let other: AVCaptureDevice.Position = self.position == .back ? .front : .back
                    if self.setInput(other) { DispatchQueue.main.async { self.position = other } }
                    else { DispatchQueue.main.async { self.auth = .noCamera }; return }
                }
            }
            if !self.session.isRunning { self.session.startRunning() }
        }
    }

    @discardableResult
    private func setInput(_ pos: AVCaptureDevice.Position) -> Bool {
        guard let dev = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: pos),
              let input = try? AVCaptureDeviceInput(device: dev) else { return false }
        session.beginConfiguration()
        session.inputs.forEach { session.removeInput($0) }
        guard session.canAddInput(input) else { session.commitConfiguration(); return false }
        session.addInput(input)
        for out in [videoOut, photoOut] as [AVCaptureOutput] {
            if let c = out.connection(with: .video) {
                if c.isVideoOrientationSupported { c.videoOrientation = .portrait }
                if c.isVideoMirroringSupported { c.automaticallyAdjustsVideoMirroring = false; c.isVideoMirrored = (pos == .front) }
            }
        }
        session.commitConfiguration()
        return true
    }

    func switchCamera() {
        let next: AVCaptureDevice.Position = position == .back ? .front : .back
        queue.async {
            if self.setInput(next) { DispatchQueue.main.async { self.position = next } }
        }
    }

    func capture() {
        guard !busy else { return }
        busy = true
        queue.async {
            self.photoOut.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    private func finish(_ image: UIImage?) {
        DispatchQueue.main.async {
            guard let image = image else { self.busy = false; self.message = "Не удалось сделать снимок"; return }
            self.shots.insert(image, at: 0)
            if self.shots.count > 30 { self.shots.removeLast() }
            self.save(image)
        }
    }

    private func save(_ image: UIImage) {
        let doSave = {
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { ok, _ in
                DispatchQueue.main.async {
                    self.busy = false
                    self.message = ok ? "Снимок сохранён в Фото" : "Не удалось сохранить снимок"
                }
            }
        }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { st in
            if st == .authorized || st == .limited { doSave() }
            else { DispatchQueue.main.async {
                self.busy = false
                self.message = "Нет доступа к Фото. Разрешите его в Настройках, чтобы сохранять снимки."
            } }
        }
    }
}

extension CameraModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sb: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pb = CMSampleBufferGetImageBuffer(sb) else { return }
        let img = currentFilter.apply(CIImage(cvPixelBuffer: pb))
        guard let cg = ctx.createCGImage(img, from: img.extent) else { return }
        DispatchQueue.main.async { self.frame = cg }
    }
}

extension CameraModel: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let cg = photo.cgImageRepresentation() else { finish(nil); return }
        var ci = CIImage(cgImage: cg)
        if let raw = photo.metadata[kCGImagePropertyOrientation as String] as? UInt32,
           let o = CGImagePropertyOrientation(rawValue: raw) { ci = ci.oriented(o) }
        ci = currentFilter.apply(ci)
        guard let out = ctx.createCGImage(ci, from: ci.extent) else { finish(nil); return }
        finish(UIImage(cgImage: out))
    }
}
