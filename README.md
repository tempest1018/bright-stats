# Bright-Stats

Experimental, independent offline statistical modeling for Windows and Linux,
written in Java with a JavaFX desktop interface and a calculation CLI.

**Development preview. No standalone installers are published yet. Exact
SmartPLS feature/numerical parity and overall superiority are not established.**
The application runs without activation or an online account. It is not affiliated
with SmartPLS. Read the [known limitations](docs/KNOWN_ISSUES.md) before relying on
an analysis.

## What you can do

- Fit PLS-SEM, consistent PLS, covariance-based SEM/CFA and GSCA models.
- Use observed-data Gaussian FIML, proper continuous multiple imputation with Rubin
  pooling, robust ordinal polychoric/DWLS SEM, and seeded power/precision studies.
- Explore independent-cluster inference, two-level Gaussian SEM, nested-CV elastic
  net, proper-prior Bayesian SEM, and explicit deletion/specification sensitivity.
- Run linear/robust and binary logistic regression, PCA, conditional-process,
  nonlinear/moderation, higher-order and selected copula/control-function models.
- Fit multi-level composite hierarchies, hierarchical PLSc moments and a scoped
  marginal Normal or covariance-scaled Student-t latent interaction/quadratic likelihood.
- Explore prediction, model/group comparisons, measurement invariance, segmentation,
  importance-performance, tetrads and necessary-condition analysis.
- Import CSV/TSV, Excel and SPSS data; retain preparation history and create fixed
  row transformations or categorical recodes.
- Use seeded resampling, explicit failure accounting, numerical diagnostics and
  saved project/report snapshots.
- Edit typed diagrams, manage local workspaces, export self-contained HTML reports
  and supported PNG/SVG graphics.
- Personalize Light/Dark/System appearance, contrast, motion, accent and layout;
  explore linked plots and archived matrix/interval graphics. Review exact contents
  before saving an optional-data reproducibility ZIP.

These are implemented **scoped contracts**, not a claim that every combination,
reference option or statistical method is available. Some workflows deliberately
reject unsupported or numerically inadmissible configurations.

## Quick start

Requirements: **JDK 25 or newer**, **Python 3.10 or newer**, Git, and Windows or
Linux **x86-64**. A graphical desktop is required for the GUI. The first build
needs internet access for pinned open-source dependencies from Maven Central.

### Windows (PowerShell)

1. Install an x64 JDK 25+, Python 3.10+ and Git using the
   [Windows prerequisite instructions](docs/INSTALLATION.md#windows-step-by-step).
   Open a new PowerShell window after installation.
2. Download the source and build:

   ```powershell
   git clone https://github.com/tempest1018/bright-stats.git
   cd bright-stats
   python scripts/build_public.py
   ```

3. Launch the desktop app:

   ```powershell
   python scripts/run_public.py
   ```

   Add `--example` to open the included synthetic model. If your installation uses
   the Python launcher, replace `python` with `py -3` throughout.

### Linux (terminal in a graphical desktop)

1. Install an x86-64 JDK 25+, Python 3.10+, Git and the native GUI libraries using
   the [Linux prerequisite instructions](docs/INSTALLATION.md#linux-step-by-step).
2. Download the source and build:

   ```sh
   git clone https://github.com/tempest1018/bright-stats.git
   cd bright-stats
   python3 scripts/build_public.py
   ```

3. Launch the desktop app:

   ```sh
   python3 scripts/run_public.py
   ```

   Add `--example` to open the included synthetic model.

On either platform, optionally run the public checks with
`build_public.py --test` using the same Python command as above. Tests may take
several minutes. For later launches, open a terminal in the cloned `bright-stats`
folder and repeat the launch command; rebuilding is unnecessary unless the source
changes. Keep the generated JAR and adjacent `lib` directory together.

The public build does not require proprietary installers, recovered source,
reference datasets, credentials or private tool receipts. After the initial
verified dependency download, `build_public.py --offline --test` rebuilds from
cache. Running the application does not need internet access. Source builds still
require your installed JDK; a bundled offline runtime will come with future installers.

See [Windows and Linux installation](docs/INSTALLATION.md) for complete steps,
CLI commands, platform prerequisites and troubleshooting.

## Documentation

- [User guide](docs/USER_GUIDE.md): prepare data, build models, choose analyses,
  review diagnostics, save projects and interpret reports.
- [Known issues and limits](docs/KNOWN_ISSUES.md): unsupported methods, compatibility,
  translation, resource limits and installer status.
- [Developer guide](docs/DEVELOPMENT.md): build outputs, public tests and dependency integrity.
- [Security and data handling](SECURITY.md).
- [Third-party components](THIRD_PARTY.md).
- [Synthetic examples](examples/README.md).

## Validation status

Validation combines independent mathematical oracles, strict input/cancellation
checks, native desktop round trips, source-bearing graphics, and Windows/offline
Linux execution. The public source build supplies 24 self-contained test groups;
private compatibility fixtures and logs are excluded. Public tests do not reproduce
the full private comparison harness. Passing scoped checks do not establish every
native feature, finite-sample coverage property or clean-install scenario.
