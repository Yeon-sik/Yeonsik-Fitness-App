import XCTest

final class WorkoutFlowTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testWorkoutRemainsAfterRelaunch() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        let name = "CI Workout \(UUID().uuidString.prefix(8))"
        tap(app.buttons["create-routine"])
        enter(name, into: app.textFields["routine-name"])
        enter("Bench Press", into: app.textFields["exercise-name"])
        tap(app.buttons["save-routine"])
        let routine = app.descendants(matching: .any)["routine-\(name)"]
        tap(routine)
        tap(app.buttons["start-workout"])

        let finish = app.buttons["finish-workout"]
        XCTAssertTrue(finish.waitForExistence(timeout: 15))
        XCTAssertFalse(finish.isEnabled)
        tap(app.buttons["add-set"])
        tap(app.segmentedControls.buttons["lb"])
        tap(app.descendants(matching: .any)["set-rir"])
        tap(app.buttons["2"])
        enter("100", into: app.textFields["set-weight"])
        enter("10", into: app.textFields["set-reps"])
        tap(app.buttons["save-set"])
        let readyToFinish = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: finish)
        XCTAssertEqual(XCTWaiter.wait(for: [readyToFinish], timeout: 15), .completed)

        app.terminate()
        app.launch()
        let resume = app.descendants(matching: .any)["resume-workout"]
        tap(resume)
        tap(finish)
        let history = app.descendants(matching: .any)["history-\(name)"]
        XCTAssertTrue(history.waitForExistence(timeout: 15))

        app.terminate()
        app.launch()
        XCTAssertTrue(routine.waitForExistence(timeout: 15))
        XCTAssertFalse(resume.exists)
        tap(app.tabBars.buttons["기록"])
        tap(history)
        XCTAssertTrue(app.staticTexts["Bench Press"].exists)
        XCTAssertTrue(app.staticTexts["100 lb · 10회"].exists)
        XCTAssertTrue(app.staticTexts["RIR 2"].exists)
        XCTAssertTrue(app.staticTexts["추정 1RM 60.5 kg"].exists)
        app.terminate()
    }

    @MainActor
    private func enter(_ text: String, into field: XCUIElement) {
        tap(field)
        field.typeText(text)
    }

    @MainActor
    private func tap(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Missing \(element)", file: file, line: line)
        element.tap()
    }
}
