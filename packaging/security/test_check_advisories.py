import unittest
import hashlib
import json
import pathlib
import tempfile
import zipfile
from check_advisories import inventory, query, LOCKS


class AdvisoryResponseTests(unittest.TestCase):
    packages = [{"name": "example:fixture", "version": "1.0", "origins": ["synthetic"]}]

    def test_empty_result_is_not_a_missing_response(self):
        self.assertEqual([], query(self.packages, lambda requests: {"results": [{}]}))
        for response in ({}, {"results": []}, {"results": [None]}, {"results": [{"error": "failed"}]}):
            with self.assertRaises(ValueError):
                query(self.packages, lambda requests: response)

    def test_findings_and_pagination_are_not_silently_dropped(self):
        requests = []

        def request(batch):
            requests.append(batch)
            return {"results": [{"vulns": [{"id": "SYNTHETIC-2"}]}]} if "page_token" in batch[0] else {
                "results": [{"vulns": [{"id": "SYNTHETIC-1"}], "next_page_token": "next"}]}

        found = query(self.packages, request)
        self.assertEqual(["SYNTHETIC-1", "SYNTHETIC-2"], found[0]["advisories"])
        self.assertEqual("next", requests[1][0]["page_token"])
        self.assertEqual({"ecosystem": "Maven", "name": "example:fixture"}, requests[0][0]["package"])

    def test_repeated_pagination_is_incomplete_not_clean(self):
        with self.assertRaises(ValueError):
            query(self.packages, lambda requests: {"results": [{"next_page_token": "same"}]})

    def test_missing_vulnerability_id_is_incomplete(self):
        with self.assertRaises(ValueError):
            query(self.packages, lambda requests: {"results": [{"vulns": [{}]}]})

    def test_batching_preserves_package_association(self):
        packages = [dict(self.packages[0], name="example:fixture" + str(i)) for i in range(201)]
        sizes = []

        def request(batch):
            sizes.append(len(batch))
            return {"results": [{"vulns": [{"id": item["package"]["name"]}]} for item in batch]}

        findings = query(packages, request)
        self.assertEqual([100, 100, 1], sizes)
        self.assertEqual([item["name"] for item in packages], [item["advisories"][0] for item in findings])

    def test_inventory_maps_metadata_and_reports_unknowns(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for lock in LOCKS:
                (root / lock).parent.mkdir(parents=True, exist_ok=True)
                (root / lock).write_text("example:locked:1.0=runtimeClasspath\n", encoding="utf-8")
            (root / "packaging/security").mkdir(parents=True)
            (root / "packaging/security/bundled-maven.json").write_text("{}", encoding="utf-8")
            (root / "lib/common").mkdir(parents=True)
            with zipfile.ZipFile(root / "lib/common/known.jar", "w") as archive:
                archive.writestr("META-INF/maven/example/known/pom.properties",
                                 "groupId=example\nartifactId=known\nversion=2.0\n")
            with zipfile.ZipFile(root / "lib/common/unknown.jar", "w") as archive:
                archive.writestr("README", "synthetic")
            paths = ["lib/common/known.jar", "lib/common/unknown.jar", "lib/common/Graph3d.jar"]
            packages, unknown, excluded = inventory(root, paths)
            self.assertEqual(["example:known", "example:locked"], [item["name"] for item in packages])
            self.assertEqual("lib/common/unknown.jar", unknown[0]["path"])
            self.assertEqual("lib/common/Graph3d.jar", excluded[0]["path"])
            self.assertEqual(2, len(packages[1]["origins"]))
            mapping = {"lib/common/unknown.jar": {"name": "example:mapped", "version": "3.0", "sha256": "wrong"}}
            (root / "packaging/security/bundled-maven.json").write_text(json.dumps(mapping), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "hash changed"):
                inventory(root, paths)
            mapping["lib/common/unknown.jar"]["sha256"] = hashlib.sha256((root / "lib/common/unknown.jar").read_bytes()).hexdigest()
            (root / "packaging/security/bundled-maven.json").write_text(json.dumps(mapping), encoding="utf-8")
            self.assertEqual([], inventory(root, paths)[1])
            (root / LOCKS[0]).write_text("", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Empty Gradle"):
                inventory(root, paths)


if __name__ == "__main__":
    unittest.main()
