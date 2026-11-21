"""Decide whether a main-branch Java release needs publishing. No credentials are used."""

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

POM_PATH = "implementations/java/pom.xml"
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
CENTRAL = "https://repo.maven.apache.org/maven2"


def coordinates(xml):
    """Read the explicit project coordinates, without resolving Maven properties."""
    project = ET.fromstring(xml)
    values = tuple((project.findtext("m:" + name, namespaces=NAMESPACE) or "").strip()
                   for name in ("groupId", "artifactId", "version"))
    if not all(values):
        raise ValueError("The Java POM must declare groupId, artifactId and version explicitly")
    return values


def validate_release(coordinate):
    """Accept the project's release-candidate and final version formats."""
    group, artifact, version = coordinate
    if not re.fullmatch(r"[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*", group):
        raise ValueError("Invalid Maven groupId")
    if not re.fullmatch(r"[a-z][a-z0-9-]*", artifact):
        raise ValueError("Invalid Maven artifactId")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-RC[1-9][0-9]*)?", version):
        raise ValueError("Use a release version such as 1.0.0-RC1 or 1.0.0")


def previous_coordinates(before):
    """Read the pre-push POM, distinguishing initial publication from a Git failure."""
    if before == "0" * 40:
        return None
    if not re.fullmatch(r"[a-f0-9]{40}", before):
        raise ValueError("The push event must contain a valid previous commit")
    subprocess.run(["git", "cat-file", "-e", before + "^{commit}"], check=True,
                   capture_output=True)
    listing = subprocess.run(["git", "ls-tree", "--name-only", before, "--", POM_PATH],
                             check=True, capture_output=True, text=True)
    if not listing.stdout.strip():
        return None
    previous = subprocess.run(["git", "show", before + ":" + POM_PATH],
                              check=True, capture_output=True, text=True)
    return coordinates(previous.stdout)


def is_published(coordinate):
    """Only a confirmed Central 404 means the coordinates are available to publish."""
    group, artifact, version = coordinate
    url = f"{CENTRAL}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    request = urllib.request.Request(url, method="HEAD",
                                     headers={"User-Agent": "Clean-Java-Release"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                raise RuntimeError(f"Unexpected Maven Central response: HTTP {response.status}")
            return True
    except urllib.error.HTTPError as error:
        with error:
            if error.code == 404:
                return False
            raise RuntimeError(f"Maven Central availability check failed: HTTP {error.code}") from error


def plan_release(event_name, ref, before, pom):
    """Return the publication decision and an explanation suitable for workflow logs."""
    if ref != "refs/heads/main":
        return False, "", "Publishing runs only on main"
    if event_name not in ("push", "workflow_dispatch"):
        raise ValueError("Unsupported publishing event")
    current = coordinates(pom.read_text(encoding="utf-8"))
    validate_release(current)
    if event_name == "push" and previous_coordinates(before) == current:
        return False, current[2], "Java coordinates are unchanged; verification handles this merge"
    if is_published(current):
        return False, current[2], "This version is already on Maven Central; it will not be republished"
    return True, current[2], "Publish " + ":".join(current)


def main():
    """Read the GitHub event and write only non-sensitive step outputs."""
    event_name = os.environ["GITHUB_EVENT_NAME"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    publish, version, reason = plan_release(event_name, os.environ["GITHUB_REF"],
                                           event.get("before", ""), Path(POM_PATH))
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"publish={str(publish).lower()}\nversion={version}\n")
    print(reason)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"Release decision failed: {error}", file=sys.stderr)
        sys.exit(1)
