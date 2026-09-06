#!/usr/bin/env python3
"""Scoped public-coordinate OSV check; unknown coverage is never a clean verdict."""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import urllib.request
import zipfile

API = "https://api.osv.dev/v1/querybatch"
LOCKS = ("ui/javafx-desktop/gradle.lockfile", "ui/compose-logger/gradle.lockfile")
RETIRED = {"Graph3d.jar", "j3dcore.jar", "j3dutils.jar", "vecmath.jar"}


def inventory(root, tracked_jars=None):
    packages = {}
    unknown = []
    excluded = []

    def add(name, version, origin):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+", name) or not version:
            raise ValueError("Invalid package coordinates in " + origin)
        packages.setdefault((name, version), set()).add(origin)

    for path in LOCKS:
        entries = 0
        for line in (root / path).read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#") or line.startswith("empty="):
                continue
            coordinate, separator, _ = line.partition("=")
            fields = coordinate.split(":")
            if not separator or len(fields) != 3:
                raise ValueError("Unrecognized Gradle lock entry in " + path)
            add(fields[0] + ":" + fields[1], fields[2], path)
            entries += 1
        if not entries:
            raise ValueError("Empty Gradle dependency lock: " + path)

    mappings = json.loads((root / "packaging/security/bundled-maven.json").read_text(encoding="utf-8"))
    if tracked_jars is None:
        tracked_jars = subprocess.check_output(
            ["git", "-C", str(root), "ls-files", "lib/common/*.jar"], text=True).splitlines()
    for relative in tracked_jars:
        path = root / relative
        if path.name in RETIRED:
            excluded.append({"path": relative, "reason": "Excluded by current application-image builders"})
            continue
        if relative in mappings:
            mapping = mappings[relative]
            if hashlib.sha256(path.read_bytes()).hexdigest() != mapping["sha256"]:
                raise ValueError("Bundled coordinate mapping hash changed: " + relative)
            add(mapping["name"], mapping["version"], relative)
            continue
        found = False
        with zipfile.ZipFile(path) as archive:
            for member in archive.namelist():
                if not (member.startswith("META-INF/maven/") and member.endswith("/pom.properties")):
                    continue
                properties = {}
                for line in archive.read(member).decode("utf-8").splitlines():
                    if "=" in line and not line.lstrip().startswith("#"):
                        key, value = line.split("=", 1)
                        properties[key.strip()] = value.strip()
                add(properties["groupId"] + ":" + properties["artifactId"], properties["version"], relative)
                found = True
        if not found:
            unknown.append({"path": relative, "reason": "No verified Maven identity; not queried"})
    return ([{"name": name, "version": version, "origins": sorted(origins)}
             for (name, version), origins in sorted(packages.items())], unknown, excluded)


def post(queries):
    # Only public package names/versions/page tokens are sent, never repository files.
    request = urllib.request.Request(API, data=json.dumps({"queries": queries}).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response)


def query(packages, request=post):
    findings = [set() for _ in packages]
    for start in range(0, len(packages), 100):
        pending = [(index, {"package": {"ecosystem": "Maven", "name": packages[index]["name"]},
                            "version": packages[index]["version"]})
                   for index in range(start, min(start + 100, len(packages)))]
        seen = set()
        for _ in range(50):
            if not pending:
                break
            result = request([item[1] for item in pending])
            rows = result.get("results")
            if not isinstance(rows, list) or len(rows) != len(pending):
                raise ValueError("Incomplete OSV batch response")
            remaining = []
            for (index, sent), row in zip(pending, rows):
                if not isinstance(row, dict) or "error" in row:
                    raise ValueError("Invalid OSV package result")
                vulnerabilities = row.get("vulns", [])
                if not isinstance(vulnerabilities, list):
                    raise ValueError("Invalid OSV vulnerability list")
                for vulnerability in vulnerabilities:
                    identifier = vulnerability.get("id")
                    if not isinstance(identifier, str) or not identifier:
                        raise ValueError("OSV vulnerability is missing its identifier")
                    findings[index].add(identifier)
                token = row.get("next_page_token")
                if token:
                    if not isinstance(token, str) or (index, token) in seen:
                        raise ValueError("Invalid/repeated OSV pagination token")
                    seen.add((index, token))
                    remaining.append((index, dict(sent, page_token=token)))
            pending = remaining
        if pending:
            raise ValueError("OSV pagination limit reached; scan incomplete")
    return [dict(package, advisories=sorted(identifiers)) for package, identifiers in zip(packages, findings)
            if identifiers]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory-only", action="store_true", help="Do not contact OSV; not an advisory verdict")
    arguments = parser.parse_args()
    report = {"checked_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "api": API,
              "limitations": ["Database matches are not proof of exploitability or safety",
                              "Unmapped bundled jars and native binaries are not covered",
                              "JDK/OS and unlocked Android/build-tool transitives are not covered",
                              "Not a source-code, secret, license or full dependency audit"]}
    try:
        root = pathlib.Path(__file__).resolve().parents[2]
        packages, unknown, excluded = inventory(root)
        report.update(packages=packages, queried_count=0, unmapped_jars=unknown, excluded_jars=excluded)
        if arguments.inventory_only:
            report["status"] = "inventory_only_not_queried"
            code = 0
        else:
            findings = query(packages)
            report.update(queried_count=len(packages), findings=findings,
                          status="advisories_found" if findings else "partial_no_known_advisories")
            code = 1 if findings else 0
    except Exception as failure:
        report.update(status="scan_incomplete", error=str(failure))
        code = 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return code


if __name__ == "__main__":
    sys.exit(main())
