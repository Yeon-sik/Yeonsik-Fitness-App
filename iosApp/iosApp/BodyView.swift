import SwiftUI
import YeonsikShared

struct BodyView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        BodyMetricsHostKt.makeBodyMetricsViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
