# Security and data handling

Do not put credentials, license/activation material, private datasets or original
proprietary binaries into issues, pull requests or this repository. Use minimal
synthetic reproductions. Do not publish a suspected secret to demonstrate an issue;
use GitHub's private reporting channel if enabled, or contact the maintainer through
an appropriate private channel before disclosing details.

The app processes local files and does not need an online account for calculations.
Projects and report archives can retain the original data and labels. Review them
before sharing. Strict own-input parsing and escaped HTML/SVG text reduce specific
input risks; they are not malware scanning or a general sandbox. Do not execute
untrusted programs merely because an analysis file or document suggests doing so.

Public dependencies come from official Maven Central URLs with committed SHA-256
pins. The first build needs network access; application calculations can be offline.
Keep the JDK and operating-system security updates current. The preview has not
received an independent comprehensive security audit.
