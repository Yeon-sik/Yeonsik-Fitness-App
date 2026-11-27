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
        tap(app.tabBars.buttons["설정"])
        tap(bodyControl(app, identifier: "display-lb", label: "파운드"))
        tap(app.tabBars.buttons["피트니스"])
        tap(app.buttons["무산소 운동"])
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
        let exercise = app.descendants(matching: .any)["exercise-Bench Press"]
        tap(exercise)
        func field(_ label: String, _ index: Int = 0) -> XCUIElement {
            app.textFields.matching(NSPredicate(format: "label == %@", label)).element(boundBy: index)
        }
        let menu = bodyControl(app, identifier: "menu-set-1", label: "1세트 설정")
        let completions = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "complete-set-"))
        let complete = completions.element(boundBy: 0)
        let firstRemove = bodyControl(app, identifier: "remove-set-1", label: "1세트 삭제")
        let weight = field("중량(lb)")
        let reps = field("횟수")
        let rir = field("RIR")
        XCTAssertTrue(firstRemove.waitForExistence(timeout: 10))
        XCTAssertFalse(firstRemove.isEnabled)
        tap(menu)
        tap(bodyControl(app, identifier: "set-unit-kg", label: "kg"))
        XCTAssertTrue(field("중량(kg)").waitForExistence(timeout: 10))
        tap(menu)
        tap(bodyControl(app, identifier: "set-unit-lb", label: "lb"))
        enterBodyWeight("95", into: weight, in: app)
        tap(app.buttons["다음"])
        tap(app.keyboards.keys["8"])
        assertBodyWeight("8", in: reps)
        tap(app.buttons["다음"])
        tap(app.keyboards.keys["3"])
        assertBodyWeight("3", in: rir)
        tap(app.buttons["입력 저장"])
        tapEnabled(complete)
        let screen = XCTAttachment(screenshot: app.screenshot())
        screen.name = "일반 세트 95lb 8회 RIR 3"
        screen.lifetime = .keepAlways
        add(screen)
        let firstID = complete.identifier
        XCTAssertTrue(firstID.hasPrefix("complete-set-"), complete.debugDescription)
        tap(app.navigationBars.buttons.firstMatch)
        let readyToFinish = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: finish)
        XCTAssertEqual(XCTWaiter.wait(for: [readyToFinish], timeout: 15), .completed)

        app.terminate()
        app.launch()
        tap(app.tabBars.buttons["피트니스"])
        let resume = app.descendants(matching: .any)["resume-workout"]
        tap(resume)
        tap(exercise)
        XCTAssertEqual(complete.identifier, firstID)
        assertBodyWeight("95", in: weight)
        assertBodyWeight("8", in: reps)
        assertBodyWeight("3", in: rir)
        enterBodyWeight("99", into: weight, in: app, replacing: "95")
        tap(app.navigationBars.buttons.firstMatch)
        tap(exercise)
        assertBodyWeight("99", in: weight)
        tap(menu)
        tap(bodyControl(app, identifier: "set-discard-draft", label: "입력 취소"))
        assertBodyWeight("95", in: weight)
        enterBodyWeight("100", into: weight, in: app, replacing: "95")
        enterBodyWeight("10", into: reps, in: app, replacing: "8")
        enterBodyWeight("2", into: rir, in: app, replacing: "3")
        tap(app.buttons["입력 저장"])
        tapEnabled(bodyControl(app, identifier: "add-set", label: "세트 추가"))
        let second = field("중량(lb)", 1)
        enterBodyWeight("50", into: second, in: app)
        enterBodyWeight("6", into: field("횟수", 1), in: app)
        enterBodyWeight("1", into: field("RIR", 1), in: app)
        tap(app.buttons["입력 저장"])
        tapEnabled(completions.element(boundBy: 1))
        XCTAssertFalse(firstRemove.isEnabled)
        tapEnabled(bodyControl(app, identifier: "remove-set-2", label: "2세트 삭제"))
        waitUntilGone(second)
        XCTAssertEqual(complete.identifier, firstID)
        assertBodyWeight("100", in: weight)
        tap(app.navigationBars.buttons.firstMatch)
        app.terminate()
        app.launch()
        tap(app.tabBars.buttons["피트니스"])
        tap(resume)
        tap(finish)
        let history = app.descendants(matching: .any)["history-\(name)"]
        XCTAssertTrue(history.waitForExistence(timeout: 15))

        app.terminate()
        app.launch()
        tap(app.tabBars.buttons["피트니스"])
        tap(app.buttons["무산소 운동"])
        XCTAssertTrue(routine.waitForExistence(timeout: 15))
        XCTAssertFalse(resume.exists)
        tap(app.tabBars.buttons["기록"])
        tap(history)
        XCTAssertTrue(app.staticTexts["Bench Press"].exists)
        XCTAssertTrue(app.staticTexts["100 lb · 10회"].exists)
        XCTAssertTrue(app.staticTexts["RIR 2"].exists)
        XCTAssertTrue(app.staticTexts["추정 1RM 60.5 kg"].exists)
        tap(app.tabBars.buttons["기록"])
        XCTAssertTrue(history.waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertFalse(app.staticTexts["100 lb · 10회"].exists)
        app.terminate()
    }

    @MainActor
    func testSharedBodyEditorPersistsAndBackCancels() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let recordsTab = app.tabBars.buttons["기록"]
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

        XCTAssertTrue(app.tabBars.buttons["메인"].waitForExistence(timeout: 15))
        XCTAssertEqual(app.tabBars.buttons.allElementsBoundByIndex.map(\.label), ["메인", "피트니스", "기록", "설정"])
        XCTAssertTrue(app.tabBars.buttons["메인"].isSelected)
        tap(app.tabBars.buttons["피트니스"])
        tap(app.buttons["체중 기록"])
        XCTAssertTrue(date.waitForExistence(timeout: 15))
        tap(bodyControl(app, identifier: "weight-cancel", label: "취소"))
        waitUntilGone(date)
        tap(recordsTab)
        tap(app.buttons["체중 기록"])
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
        tap(recordsTab)
        tap(app.buttons["체중 기록"])
        tap(record)
        assertBodyWeight("70.5", in: input)
        enterBodyWeight("72.5", into: input, in: app, replacing: "70.5")
        tap(save)
        waitUntilGone(date)
        tap(record)
        assertBodyWeight("72.5", in: input)
        tap(bodyControl(app, identifier: "weight-delete", label: "이 기록 삭제"))
        waitUntilGone(record)

        tap(app.buttons["닫기"])
        tap(app.tabBars.buttons["설정"])
        tap(bodyControl(app, identifier: "display-lb", label: "파운드"))
        tap(bodyControl(app, identifier: "display-light", label: "밝게"))
        app.terminate()
        app.launch()
        tap(app.tabBars.buttons["설정"])
        assertSelected(bodyControl(app, identifier: "display-lb", label: "파운드"))
        assertSelected(bodyControl(app, identifier: "display-light", label: "밝게"))
        tap(app.tabBars.buttons["메인"])
        tap(app.buttons["체중"])
        XCTAssertTrue(app.textFields["체중 lb"].waitForExistence(timeout: 15))
        enterBodyWeight("225", into: input, in: app)
        tap(save)
        waitUntilGone(date)
        tap(app.tabBars.buttons["설정"])
        tap(bodyControl(app, identifier: "display-kg", label: "킬로그램"))
        tap(bodyControl(app, identifier: "display-system", label: "기기 설정"))
        assertSelected(bodyControl(app, identifier: "display-system", label: "기기 설정"))
        tap(bodyControl(app, identifier: "display-dark", label: "어둡게"))
        assertSelected(bodyControl(app, identifier: "display-dark", label: "어둡게"))
        tap(recordsTab)
        let addRecord = app.buttons["records-add"]
        let weightKind = app.buttons["records-add-weight"]
        tap(addRecord)
        XCTAssertTrue(weightKind.waitForExistence(timeout: 10))
        XCTAssertEqual(weightKind.label, "체중")
        tap(addRecord)
        waitUntilGone(weightKind)
        tap(addRecord)
        tap(weightKind)
        assertBodyWeight("102.06", in: input)
        tap(save)
        waitUntilGone(date)
        tap(app.buttons["체중 기록"])
        tap(record)
        assertBodyWeight("102.06", in: input)
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
    private func assertSelected(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let selected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "selected == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [selected], timeout: 10), .completed, element.debugDescription, file: file, line: line)
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
    private func tapEnabled(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 15), .completed, element.debugDescription, file: file, line: line)
        element.tap()
    }

    @MainActor
    private func tap(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Missing \(element)", file: file, line: line)
        element.tap()
    }
}
