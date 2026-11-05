# Third-party components

Production dependencies are pinned in dependencies.lock.json and downloaded from
official Maven Central. No proprietary reference binaries or private keys are
included. Components retain their own copyright/license terms.

| Component | Version | License family |
| --- | --- | --- |
| OpenJFX base/graphics/controls | 27.0.1 | GPLv2 with Classpath Exception |
| Gson | 2.14.0 | Apache-2.0 |
| Apache Commons Math | 3.6.1 | Apache-2.0 |
| Apache POI / OOXML / OOXML Lite | 5.5.1 | Apache-2.0; packaged schema notices also apply |
| Apache XMLBeans | 5.3.0 | Apache-2.0 |
| Commons Compress | 1.28.0 | Apache-2.0 |
| Commons IO | 2.21.0 | Apache-2.0 |
| Commons Collections | 4.5.0 | Apache-2.0 |
| Commons Codec | 1.20.0 | Apache-2.0 |
| SparseBitSet | 1.3 | Apache-2.0 |
| Log4j API | 2.24.3 | Apache-2.0 |
| CurvesAPI | 1.08 | BSD-style; see artifact license |

The build extracts available top-level META-INF LICENSE/NOTICE/COPYING entries
into the output notices directory. This table is an inventory, not a substitute
for the complete component/distribution notices. Runtime/native licensing and
complete notice aggregation must be checked when standalone installers are bundled.
The JDK is supplied separately by the user for source builds.
