"""Exercise publication decisions without making network requests or publishing artifacts."""

from contextlib import contextmanager
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error

import release

POM = """<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
<groupId>com.clean-arch-enablers</groupId>
<artifactId>clean-example</artifactId>
<version>1.0.0-RC1</version>
</project>"""
CURRENT = ("com.clean-arch-enablers", "clean-example", "1.0.0-RC1")


class ReleaseTest(unittest.TestCase):
    @contextmanager
    def pom_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "pom.xml"
            path.write_text(POM, encoding="utf-8")
            yield path

    def test_reads_project_coordinates(self):
        self.assertEqual(CURRENT, release.coordinates(POM))

    def test_requires_explicit_coordinates(self):
        with self.assertRaises(ValueError):
            release.coordinates(POM.replace("<version>1.0.0-RC1</version>", ""))

    def test_rejects_invalid_coordinates_and_snapshot_versions(self):
        for coordinate in (("bad/group", "clean-example", "1.0.0"),
                           (CURRENT[0], "bad/artifact", "1.0.0"),
                           (CURRENT[0], CURRENT[1], "1.0.0-SNAPSHOT"),
                           (CURRENT[0], CURRENT[1], "${version}"),
                           (CURRENT[0], CURRENT[1], "1.0.0-RC0")):
            with self.subTest(coordinate=coordinate), self.assertRaises(ValueError):
                release.validate_release(coordinate)
        release.validate_release(CURRENT)
        release.validate_release((CURRENT[0], CURRENT[1], "1.0.0"))

    @patch("release.is_published")
    @patch("release.previous_coordinates")
    def test_unchanged_version_does_not_contact_central(self, previous, published):
        previous.return_value = CURRENT
        with self.pom_file() as pom:
            result = release.plan_release("push", "refs/heads/main", "a" * 40, pom)
        self.assertFalse(result[0])
        published.assert_not_called()

    @patch("release.is_published", return_value=False)
    @patch("release.previous_coordinates", return_value=None)
    def test_first_java_implementation_is_a_release(self, previous, published):
        with self.pom_file() as pom:
            self.assertEqual((True, "1.0.0-RC1"),
                             release.plan_release("push", "refs/heads/main", "a" * 40, pom)[:2])

    @patch("release.is_published", return_value=False)
    @patch("release.previous_coordinates", return_value=(CURRENT[0], CURRENT[1], "1.0.0"))
    def test_changed_version_can_publish(self, previous, published):
        with self.pom_file() as pom:
            self.assertTrue(release.plan_release("push", "refs/heads/main", "a" * 40, pom)[0])

    @patch("release.is_published", return_value=True)
    def test_manual_rerun_skips_existing_version(self, published):
        with self.pom_file() as pom:
            self.assertFalse(release.plan_release("workflow_dispatch", "refs/heads/main", "", pom)[0])

    @patch("release.is_published", return_value=False)
    @patch("release.previous_coordinates")
    def test_manual_retry_does_not_need_version_change(self, previous, published):
        with self.pom_file() as pom:
            self.assertTrue(release.plan_release("workflow_dispatch", "refs/heads/main", "", pom)[0])
        previous.assert_not_called()

    @patch("release.is_published")
    def test_feature_branches_never_publish(self, published):
        self.assertFalse(release.plan_release("workflow_dispatch", "refs/heads/feature/work", "",
                                             Path("missing.xml"))[0])
        published.assert_not_called()

    def test_unexpected_events_fail(self):
        with self.assertRaises(ValueError):
            release.plan_release("pull_request", "refs/heads/main", "", Path("missing.xml"))

    @patch("release.urllib.request.urlopen")
    def test_central_existing_release(self, urlopen):
        urlopen.return_value.__enter__.return_value.status = 200
        self.assertTrue(release.is_published(CURRENT))
        request = urlopen.call_args.args[0]
        self.assertEqual("HEAD", request.method)
        self.assertEqual("https://repo.maven.apache.org/maven2/com/clean-arch-enablers/"
                         "clean-example/1.0.0-RC1/clean-example-1.0.0-RC1.pom", request.full_url)

    @patch("release.urllib.request.urlopen")
    def test_only_404_means_unpublished(self, urlopen):
        urlopen.side_effect = urllib.error.HTTPError("url", 404, "Missing", {}, None)
        self.assertFalse(release.is_published(CURRENT))
        for code in (401, 403, 429, 500, 503):
            urlopen.side_effect = urllib.error.HTTPError("url", code, "Error", {}, None)
            with self.subTest(code=code), self.assertRaises(RuntimeError):
                release.is_published(CURRENT)

    @patch("release.urllib.request.urlopen")
    def test_network_failure_does_not_allow_publish(self, urlopen):
        urlopen.side_effect = urllib.error.URLError("Connection failed")
        with self.assertRaises(urllib.error.URLError):
            release.is_published(CURRENT)

    @patch("release.urllib.request.urlopen")
    def test_unexpected_success_response_does_not_allow_publish(self, urlopen):
        urlopen.return_value.__enter__.return_value.status = 204
        with self.assertRaises(RuntimeError):
            release.is_published(CURRENT)

    def test_initial_push_has_no_previous_coordinates(self):
        self.assertIsNone(release.previous_coordinates("0" * 40))

    def test_invalid_previous_commit_is_rejected(self):
        with self.assertRaises(ValueError):
            release.previous_coordinates("--unsafe")

    @patch("release.subprocess.run")
    def test_git_failure_is_not_treated_as_initial_release(self, run):
        run.side_effect = subprocess.CalledProcessError(128, ["git"])
        with self.assertRaises(subprocess.CalledProcessError):
            release.previous_coordinates("a" * 40)

    @patch("release.subprocess.run")
    def test_previous_commit_without_java_pom(self, run):
        run.return_value.stdout = ""
        self.assertIsNone(release.previous_coordinates("a" * 40))
        self.assertEqual(2, run.call_count)

    @patch("release.subprocess.run")
    def test_previous_commit_coordinates(self, run):
        run.side_effect = [Mock(), Mock(stdout=release.POM_PATH), Mock(stdout=POM)]
        self.assertEqual(CURRENT, release.previous_coordinates("a" * 40))

    @patch("release.plan_release", return_value=(True, "1.0.0-RC1", "Publish example"))
    def test_github_outputs_contain_the_plan(self, plan):
        with tempfile.TemporaryDirectory() as directory:
            event = Path(directory) / "event.json"
            output = Path(directory) / "output"
            event.write_text(json.dumps({"before": "a" * 40}), encoding="utf-8")
            with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/heads/main",
                                         "GITHUB_EVENT_PATH": str(event), "GITHUB_OUTPUT": str(output)}):
                with patch("sys.stdout", new_callable=io.StringIO):
                    release.main()
            self.assertEqual("publish=true\nversion=1.0.0-RC1\n", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
