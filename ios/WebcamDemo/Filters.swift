import CoreImage

enum PhotoFilter: String, CaseIterable, Identifiable {
    case none, gray, sepia, invert, vivid, hue
    var id: String { rawValue }
    var title: String {
        switch self {
        case .none: return "Без фильтра"
        case .gray: return "Ч/Б"
        case .sepia: return "Сепия"
        case .invert: return "Инверсия"
        case .vivid: return "Яркий"
        case .hue: return "Сдвиг оттенка"
        }
    }
    func apply(_ img: CIImage) -> CIImage {
        func f(_ name: String, _ params: [String: Any]) -> CIImage {
            CIFilter(name: name, parameters: [kCIInputImageKey: img].merging(params) { $1 })?.outputImage ?? img
        }
        switch self {
        case .none: return img
        case .gray: return f("CIColorControls", [kCIInputSaturationKey: 0.0])
        case .sepia: return f("CISepiaTone", [kCIInputIntensityKey: 1.0])
        case .invert: return f("CIColorInvert", [:])
        case .vivid: return f("CIColorControls", [kCIInputSaturationKey: 1.8, kCIInputContrastKey: 1.2])
        case .hue: return f("CIHueAdjust", [kCIInputAngleKey: Double.pi])
        }
    }
}
