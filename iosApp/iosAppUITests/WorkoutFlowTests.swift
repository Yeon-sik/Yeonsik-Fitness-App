import XCTest

final class WorkoutFlowTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testWorkoutSyncShowsAccountAndSignedOutState() {
        let app = XCUIApplication()
        app.launch()
        tap(app.tabBars.buttons["기록"])
        tap(app.buttons["동기화 계정"])
        XCTAssertTrue(app.textFields["프로젝트 HTTPS 주소"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["로그인"].isEnabled)
        tap(app.buttons["닫기"])
        tap(app.buttons["하루 운동 기록"])
        let openSync = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "완료 운동 기기 간 동기화")).firstMatch
        tap(openSync)
        let signInInstructions = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "같은 Fitness 계정")).firstMatch
        XCTAssertTrue(signInInstructions.waitForExistence(timeout: 15))
        app.terminate()
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
        let input = app.textFields.matching(NSPredicate(format: "label BEGINSWITH %@", "체중 ")).firstMatch
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
        XCTAssertTrue(date.waitForExistence(timeout: 10), app.debugDescription)
        swipeBack(app)
        waitUntilGone(date)

        tap(add)
        enterBodyWeight("70.5", into: input, in: app)
        tap(save)
        waitUntilGone(date)
        XCTAssertTrue(record.waitForExistence(timeout: 15))

        app.terminate()
        app.launch()
        tap(bodyTab)
        tap(record)
        assertBodyWeight("70.5", in: input)
        enterBodyWeight("72.5", into: input, in: app, replacing: "70.5")
        tap(save)
        waitUntilGone(date)
        tap(record)
        assertBodyWeight("72.5", in: input)
        tap(bodyControl(app, identifier: "weight-delete", label: "이 기록 삭제"))
        waitUntilGone(record)

        tap(add)
        enterBodyWeight("72.75", into: input, in: app)
        tap(save)
        waitUntilGone(date)
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
    private func waitUntilGone(
        _ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line
    ) {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [gone], timeout: 15), .completed, element.debugDescription, file: file, line: line)
    }

    @MainActor
    private func assertBodyWeight(_ text: String, in field: XCUIElement) {
        XCTAssertTrue(field.waitForExistence(timeout: 10), field.debugDescription)
        let committed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", text), object: field)
        XCTAssertEqual(XCTWaiter.wait(for: [committed], timeout: 5), .completed, field.debugDescription)
    }

    @MainActor
    private func enterBodyWeight(
        _ text: String, into field: XCUIElement, in app: XCUIApplication, replacing previous: String = ""
    ) {
        tap(field)
        let keys = app.keyboards.firstMatch.keys
        for _ in previous {
            tap(keys["Delete"])
        }
        for character in text {
            tap(keys[String(character)])
        }
        assertBodyWeight(text, in: field)
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
