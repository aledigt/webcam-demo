import SwiftUI

struct ContentView: View {
    @StateObject private var cam = CameraModel()
    @Environment(\.scenePhase) private var phase
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Color.black
                switch cam.auth {
                case .authorized:
                    if let f = cam.frame {
                        Image(decorative: f, scale: 1).resizable().scaledToFit()
                    } else { ProgressView().tint(.white) }
                case .denied:
                    permissionView("Доступ к камере запрещён. Разрешите его в Настройках, чтобы видеть изображение и делать снимки.")
                case .noCamera:
                    Text("Камера не найдена").foregroundColor(.white)
                case .unknown:
                    ProgressView().tint(.white)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(PhotoFilter.allCases) { f in
                        Button(f.title) { cam.setFilter(f) }
                            .buttonStyle(.bordered)
                            .tint(cam.filter == f ? .accentColor : .gray)
                    }
                }.padding(.horizontal)
            }

            HStack(spacing: 16) {
                Button { cam.switchCamera() } label: { Label("Сменить камеру", systemImage: "arrow.triangle.2.circlepath.camera") }
                    .buttonStyle(.bordered)
                    .disabled(cam.auth != .authorized || !cam.canSwitch)
                Button { cam.capture() } label: { Label("Снимок", systemImage: "camera.fill") }
                    .buttonStyle(.borderedProminent)
                    .disabled(cam.auth != .authorized || cam.busy)
            }

            Text(cam.shots.isEmpty ? "Снимков пока нет" : "Последние снимки")
                .font(.footnote).foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(Array(cam.shots.enumerated()), id: \.offset) { _, img in
                        Image(uiImage: img).resizable().scaledToFill()
                            .frame(width: 72, height: 72).clipped().cornerRadius(8)
                    }
                }.padding(.horizontal)
            }.frame(height: 76)
        }
        .padding(.vertical, 8)
        .preferredColorScheme(.dark)
        .onAppear { cam.start() }
        .onChange(of: phase) { p in
            if p == .active { cam.start() } else if p == .background { cam.stop() }
        }
        .alert(cam.message ?? "", isPresented: Binding(get: { cam.message != nil }, set: { if !$0 { cam.message = nil } })) {
            if cam.message?.contains("Настройках") == true {
                Button("Открыть настройки") { openSettings() }
            }
            Button("OK", role: .cancel) {}
        }
    }

    private func permissionView(_ text: String) -> some View {
        VStack(spacing: 12) {
            Text("Нужен доступ к камере").font(.title3.bold())
            Text(text).multilineTextAlignment(.center)
            Button("Открыть настройки") { openSettings() }.buttonStyle(.borderedProminent)
        }.foregroundColor(.white).padding(24)
    }

    private func openSettings() {
        if let u = URL(string: UIApplication.openSettingsURLString) { openURL(u) }
    }
}
