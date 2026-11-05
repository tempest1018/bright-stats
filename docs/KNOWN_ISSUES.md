# Known issues and limitations

This is a development preview, not a fully accepted SmartPLS replacement. A passing
scoped test does not establish every native option, file version or output.

## Statistical and compatibility limits

- Exact SmartPLS 1:1 feature/numerical parity and overall superiority are unproven.
- Covariance-SEM moderation can use explicit matched product indicators. A separate
  marginal Normal/Student-t latent product/quadratic likelihood is now available; it is an
  independent estimator, not a claim of native LMS EM or numerical parity. It is
  limited to 1–4 predictors with a joint Normal or covariance-scaled Student-t distribution, one latent outcome, simple reflective blocks
  with 3–12 indicators, independent measurement errors and complete selected cells.
  Quadrature stability, local information rank and optimizer convergence are checked
  separately. Absolute covariance-model CFI/TLI/RMSEA are not supplied for this fit.
- Ordinal CFA/SEM uses explicit 2–12-level declarations, polychoric moments, DWLS,
  full moment/threshold influence covariance, robust SEs and a scaled/shifted test.
  It is a scoped theta-scale reflective estimator, not a general categorical/MNAR
  model. Selected ordinal rows must be complete or explicitly excluded once.
- Gaussian FIML assumes ignorable MCAR/MAR and a correctly specified mean/covariance
  model. It does not fix MNAR or supply invented complete-data fit indices.
  Continuous normal imputation is not suitable for arbitrary categorical variables;
  its chains, missing draws, complete-data fits and Rubin diagnostics need review.
- Two-level SEM is a continuous Gaussian independent-cluster random-intercept
  likelihood with separate within/between RAM structures. Random slopes and arbitrary
  cross-level constraints are unavailable. Marginal cluster inference needs enough
  independent informative clusters; small-cluster corrections are not a guarantee.
- Regularized SEM uses training-only scaling and genuinely nested validation.
  Selection does not confer ordinary post-selection SEs/P values. Candidate or
  fold failures remain visible rather than being silently replaced.
- Bayesian SEM uses adaptive coordinate random-walk Metropolis with warmup-only
  adaptation and proper priors. Rank/folded R-hat, bulk/tail ESS and predictive
  diagnostics are finite-chain screens, not proof of convergence. This is not HMC/NUTS.
- Monte Carlo power/precision applies to declared data-generating models and tested
  sample sizes. Failed fits, Monte Carlo uncertainty and uncertainty definition
  matter; connecting curves do not certify untested sample-size thresholds.
- Influence uses exact declared deletions; it does not automatically remove outliers.
  Specification sensitivity compares prespecified models on a common sample, retains
  failures, and groups parameter ranges only when modeled locations match. Exploration
  does not provide multiplicity correction or causal confirmation.
- Recursive composite hierarchies support 1–7 declared transitions (2–8 fitted
  levels). Every resample refits the original cases and all levels. Hierarchical
  PLSc is a separate corrected-moment method; reflective dimensions need at least
  three indicators/dimensions, inadmissible corrections are rejected, and artificial
  higher-factor case scores are not supplied. This is not proof of native parity.
- Pairwise missing-data estimation is a covariance/moment method with assumptions
  such as MCAR. It does not provide FIML/MAR inference, fabricated case scores, or
  ordinary likelihood tests justified by a single effective sample size.
- Supported generated-score/control-function/interaction workflows have distinct
  assumptions. Not every possible combination or endogenous interaction is accepted.
- Native project/report/version compatibility is limited. Bright-Stats projects
  and reports are its own formats. Authorized legacy decoding requires the caller's
  key; no proprietary keys or application classes are distributed.
- Reference and standard resampling conventions differ. Undefined/nonregular tests
  and insufficiently usable resamples can leave intervals or P values unavailable.
- Synthetic/oracle checks do not certify finite-sample coverage or every real dataset.

## Interface and data limits

- Four navigation languages and ten numeric locales are supported; complete
  scientific-label translation is not provided.
- General own JSON input limits include 64 MiB, depth64, eight million value nodes,
  four million characters per string/key and 256 characters per numeric literal.
  Individual workflows apply stricter limits. Large permitted inputs can still
  exceed the memory available on a particular machine.
- General prepared datasets are capped at one million rows, ten thousand columns
  and five million cells; estimator-specific limits can be much lower.
- Excel uses cached formula results. Formulas/macros are not recalculated or run.
- Arbitrary category codes are not automatically valid quantitative/ordinal models.
- Shared/overlapping indicators can invalidate simple discriminant-validity
  interpretations; a successful fit is not a substantive justification.
- Projects/report snapshots contain data. The app does not automatically anonymize
  them or remove sensitive category labels before export.

- Diagram workspaces and estimator budgets are bounded. The current canvas has a
  fixed working extent; large models can crowd even with zoom/minimap controls.
  PNG output is capped at 16 megapixels; matrix graphics support up to 150 variables.
- Plot linking is available only for explicit recorded source bindings (currently
  PCA scores/loadings, deletion influence and named diagnostic matrices). It never
  guesses identities from axis values; unrelated saved sources are rejected.
- Table sorting places numeric values before text and null values last. It preserves
  canonical source order for report export; visible filters do not truncate archives.
- Reproducibility ZIP review is not anonymization or a malware/security certification.
  Settings retain scientific names and may include membership/user text. Reports
  include historical data and case-level outputs, so report inclusion requires data
  inclusion. Review is bounded to 16 MiB per entry / 64 MiB total; archives are not
  installers and run nothing. Checksums detect changes, not authenticate a sender.
- Some numerical phases have no defensible work denominator. Their progress remains
  indeterminate; completed attempts do not mean successful or regular fits.

## Build, testing and delivery limits

- Public source builds target Windows/Linux x86-64. ARM64/macOS and standalone
  EXE/SH/DEB installers are not delivered yet.
- A JDK and Python are needed to build. Running the source preview needs Java and
  native JavaFX prerequisites; the runtime is not bundled yet.
- The first build downloads pinned dependencies; later cached builds and application
  use can be offline. Cold offline builds without a populated cache cannot work.
- Public self-contained tests are a subset of the private development checks.
  Private reference fixtures/logs are deliberately absent. Historical development
  suite totals must not be interpreted as a publicly reproducible reference suite.
- Clean-machine installation, upgrades, uninstall behavior and packaged runtime
  notices remain part of the future installer acceptance stage.
- A project-wide distribution license has not been selected by the maintainer.

For reproducible issues include the platform/JDK version, exact action/options and
a minimal synthetic/anonymized example. Do not post credentials or private reports.
