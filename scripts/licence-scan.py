#!/usr/bin/env python3
"""Lists the licence of every library in the release build, read from the POM files Gradle has cached, and fails when one is
missing a licence or has one this app (AGPL-3.0-or-later, F-Droid) cannot ship. Run `./gradlew :app:assembleRelease` first so
the cache has the POMs.

    scripts/licence-scan.py            # prints the table, exit 1 on a problem
"""
import glob, os, re, subprocess, sys
import xml.etree.ElementTree as ET

ALLOWED = ("apache", "mit", "bsd", "isc", "mozilla", "mpl", "eclipse public", "epl", "cc0", "public domain", "unlicense", "zlib",
           "android software development kit", "sil open font", "lgpl", "gpl-2.0-with-classpath", "gnu general public license, version 2")
# Libraries that publish no licence in their POM but whose licence is known.
KNOWN = {"org.jetbrains.kotlin": "Apache-2.0", "org.jetbrains.kotlinx": "Apache-2.0", "org.jetbrains": "Apache-2.0",
         "com.squareup": "Apache-2.0", "androidx": "Apache-2.0", "com.google.dagger": "Apache-2.0", "io.ktor": "Apache-2.0",
         "io.coil-kt.coil3": "Apache-2.0", "com.google.zxing": "Apache-2.0", "org.intellij": "Apache-2.0"}
# Exact coordinates whose POM has no licence entry; the licence is the one the project itself documents.
KNOWN_EXACT = {"javax.inject:javax.inject": "Apache-2.0 (JSR-330)",
               "com.google.guava:listenablefuture": "Apache-2.0 (Guava's empty stub artifact)",
               "com.google.code.findbugs:jsr305": "BSD-3-Clause (JSR-305 annotations)"}
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def dependencies():
    out = subprocess.run(["./gradlew", ":app:dependencies", "--configuration", "releaseRuntimeClasspath", "-q"],
                         capture_output=True, text=True).stdout
    found = {}
    for line in out.splitlines():
        m = re.search(r"([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([0-9][^ ]*)(?: -> ([^ ]+))?", line)
        if m:
            found[(m.group(1), m.group(2))] = m.group(4) or m.group(3)
    return found


def licences_of(group, artifact, version):
    """The licence names in the POM, or in its parent chain when the POM itself says nothing."""
    for pom in glob.glob(f"{CACHE}/{group}/{artifact}/{version}/*/{artifact}-{version}.pom"):
        root = ET.parse(pom).getroot()
        names = [n.text.strip() for n in root.findall("m:licenses/m:license/m:name", NS) if n.text]
        if names:
            return names
        parent = root.find("m:parent", NS)
        if parent is not None:
            g, a, v = (parent.find(f"m:{k}", NS).text for k in ("groupId", "artifactId", "version"))
            return licences_of(g, a, v)
    return []


def main():
    problems, rows = [], []
    for (group, artifact), version in sorted(dependencies().items()):
        names = (
            licences_of(group, artifact, version)
            or ([KNOWN_EXACT[f"{group}:{artifact}"]] if f"{group}:{artifact}" in KNOWN_EXACT else [])
            or [v for k, v in KNOWN.items() if group == k or group.startswith(k + ".")]
        )
        label = "; ".join(names) or "UNKNOWN"
        rows.append((f"{group}:{artifact}:{version}", label))
        if not names:
            problems.append(f"no licence found: {group}:{artifact}:{version}")
        elif not any(any(a in n.lower() for a in ALLOWED) for n in names):
            problems.append(f"licence to check: {group}:{artifact}:{version} -> {label}")
    for name, label in rows:
        print(f"{label:45.45}  {name}")
    print(f"\n{len(rows)} libraries")
    if problems:
        print("\n".join(problems))
        sys.exit(1)


if __name__ == "__main__":
    main()
