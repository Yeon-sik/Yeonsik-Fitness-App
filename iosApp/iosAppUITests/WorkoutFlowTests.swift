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
    func testSharedBodyEditorPersistsAndBackCancels() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let bodyTab = app.tabBars.buttons["체중"]
        let add = bodyControl(app, identifier: "weight-add", label: "체중 기록 추가")
        let date = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@ OR label BEGINSWITH %@", "weight-date", "날짜 선택")
        ).firstMatch
        let select = bodyControl(app, identifier: "weight-date-confirm", label: "선택")
        let input = app.textFields.firstMatch
        let save = bodyControl(app, identifier: "weight-save", label: "저장")
        let record = app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier == %@ OR (label CONTAINS %@ AND label CONTAINS %@)",
            "weight-record", "kg", "수정"
        )).firstMatch

        tap(bodyTab)
        XCTAssertTrue(add.waitForExistence(timeout: 15), app.debugDescription)
        tap(add)
        tap(date)
        XCTAssertTrue(select.waitForExistence(timeout: 15))
        swipeBack(app)
        waitUntilGone(select)
        XCTAssertTrue(input.exists)
        swipeBack(app)
        waitUntilGone(input)

        tap(add)
        replace("70.5", in: input)
        tap(save)
        waitUntilGone(input)
        XCTAssertTrue(record.waitForExistence(timeout: 15))

        app.terminate()
        app.launch()
        tap(bodyTab)
        tap(record)
        XCTAssertEqual(input.value as? String, "70.5")
        replace("72.5", in: input)
        tap(save)
        waitUntilGone(input)
        tap(record)
        XCTAssertEqual(input.value as? String, "72.5")
        tap(bodyControl(app, identifier: "weight-delete", label: "이 기록 삭제"))
        waitUntilGone(record)

        tap(add)
        replace("72.75", in: input)
        tap(save)
        waitUntilGone(input)
        XCTAssertTrue(record.waitForExistence(timeout: 15))
        app.terminate()
    }

    @MainActor
    private func bodyControl(_ app: XCUIApplication, identifier: String, label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@ OR label == %@", identifier, label))
            .firstMatch
    }

    @MainActor
    private func swipeBack(_ app: XCUIApplication) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.008, dy: 0.50))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.50))
        start.press(forDuration: 0.04, thenDragTo: end)
    }

    @MainActor
    private func waitUntilGone(_ element: XCUIElement) {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [gone], timeout: 15), .completed)
    }

    @MainActor
    private func replace(_ text: String, in field: XCUIElement) {
        tap(field)
        if let value = field.value as? String, value != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
        }
        field.typeText(text)
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
