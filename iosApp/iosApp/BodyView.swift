import SwiftUI
import YeonsikShared

struct BodyView: UIViewControllerRepresentable {
    var openToday = false
    var onClose: () -> Void = {}

    func makeUIViewController(context: Context) -> UIViewController {
        BodyMetricsHostKt.makeBodyMetricsViewController(openToday: openToday, onClose: onClose)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

struct DisplaySettingsView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosDisplaySettingsKt.makeDisplaySettingsViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
