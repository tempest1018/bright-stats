# Public development build

Production source is under `src/main/java`; JavaFX styles/resources are under
`src/main/resources`; test code is under `src/test/java`. `scripts/build_public.py`
is the supported public build path. It uses only the JDK/Python and the pinned
open-source Maven Central artifacts in `dependencies.lock.json`.

The build checks every artifact SHA-256, compiles with Java release25, creates
`build/public/<platform>/bright-stats.jar` and its `lib` directory, copies available
third-party notices and checks a synthetic CLI example. `--test` compiles the test
sources and executes 24 self-contained core groups: bounded input, transforms,
workspace, preferences, pairwise, higher-order and observed/component adjusted
copula systems, extended input/missingness, recursive hierarchies and marginal latent
interaction likelihood; new FIML, imputation, ordinal/source binding, simulation,
cluster/source binding, regularized, Bayesian, expanded interaction, sensitivity,
reproducibility archive and authored-example groups are included. The latent test includes a full jackknife and can take
several minutes. They generate synthetic results below the ignored build directory.

Some retained compatibility tests require excluded reference fixtures and are not
executed by the public subset. There is no claim that public `--test` runs the
private38-suite acceptance harness. Preserve these distinctions when
adding tests or reporting validation.

The output build receipt includes source/dependency counts and the artifact hash.
Keep output/libs together. No proprietary JAR, key, recovered source or local tool
receipt is needed. The app's GUI entrypoint is `org.brightstats.desktop.Launcher`;
its CLI entrypoint is `org.brightstats.cli.Main`. The public launch helper supplies
the JavaFX module path. It uses normal local user preferences, so avoid using your
real profile for automated destructive fixture tests.

Before publishing run `python scripts/check_publication.py`, review the exact staged
file manifest and verify the intended branch/remote. The public allowlist excludes
private analysis, tools, reference binaries, recovered code, outputs and internal
work notes. Do not broaden it casually or commit user projects/reports.
