"""Regression checks for the instrumentation gate; no Android device is used."""
import contextlib
import importlib.util
import io
import pathlib
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "instrumented_results", pathlib.Path(__file__).with_name("verify-instrumented-results.py")
)
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)
CLASS = "example.SmokeTest"


class InstrumentedResultsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.reports = self.root / CLASS
        self.reports.mkdir()

    def write(self, cases, filename="TEST-smoke.xml"):
        (self.reports / filename).write_text(
            "<testsuite>" + cases + "</testsuite>", encoding="utf-8"
        )

    def verify(self, skips=None):
        with contextlib.redirect_stdout(io.StringIO()):
            return VERIFIER.verify_results(self.root, [CLASS], skips or {})

    def test_missing_xml_fails_even_with_a_logcat_file(self):
        (self.reports / "logcat.txt").write_text("no results", encoding="utf-8")
        self.assertFalse(self.verify())

    def test_zero_testcases_fails(self):
        self.write("")
        self.assertFalse(self.verify())

    def test_successful_execution_passes(self):
        self.write(f'<testcase classname="{CLASS}" name="runs"/>')
        self.assertTrue(self.verify())

    def test_failure_and_error_fail(self):
        for tag in ["failure", "error"]:
            with self.subTest(tag=tag):
                self.write(f'<testcase classname="{CLASS}" name="runs"><{tag}>boom</{tag}></testcase>')
                self.assertFalse(self.verify())

    def test_report_for_another_class_cannot_satisfy_the_gate(self):
        self.write('<testcase classname="example.OtherTest" name="runs"/>')
        self.assertFalse(self.verify())

    def test_unapproved_skip_fails(self):
        self.write(f'<testcase classname="{CLASS}" name="runs"/>'
                   f'<testcase classname="{CLASS}" name="optional"><skipped/></testcase>')
        self.assertFalse(self.verify())

    def test_documented_optional_skip_keeps_other_tests_required(self):
        self.write(f'<testcase classname="{CLASS}" name="runs"/>'
                   f'<testcase classname="{CLASS}" name="optional"><skipped/></testcase>')
        self.assertTrue(self.verify({f"{CLASS}#optional": "Private fixture not supplied"}))

    def test_all_skipped_fails_even_if_documented(self):
        self.write(f'<testcase classname="{CLASS}" name="optional"><skipped/></testcase>')
        self.assertFalse(self.verify({f"{CLASS}#optional": "Private fixture not supplied"}))

    def test_malformed_xml_fails(self):
        (self.reports / "TEST-smoke.xml").write_text("<testsuite", encoding="utf-8")
        self.assertFalse(self.verify())

    def test_duplicate_results_cannot_inflate_execution_count(self):
        case = f'<testcase classname="{CLASS}" name="runs"/>'
        self.write(case)
        self.write(case, "TEST-duplicate.xml")
        self.assertFalse(self.verify())

    def test_stale_unlisted_class_is_not_counted(self):
        stale = self.root / "example.RemovedTest"
        stale.mkdir()
        (stale / "TEST-stale.xml").write_text(
            '<testsuite><testcase classname="example.RemovedTest" name="runs"/></testsuite>',
            encoding="utf-8",
        )
        self.assertFalse(self.verify())


if __name__ == "__main__":
    unittest.main()
