# User guide

Bright-Stats is an experimental statistical workbench. Start with the synthetic
example, learn one workflow at a time, and save an original-data copy before
changing preparation or model settings. Available actions can be found with
**Ctrl+F** in the action search. Exact action wording may vary by navigation language.

## 1. First session

Run `python scripts/run_public.py --example` (Linux: `python3`). The supplied
example contains three reflective constructs X, M and Y with synthetic data.
Open the Model, Data and Results tabs to inspect their relationships. Calculate
PLS-SEM, review convergence and the measurement/structural results, then save a
project under a new name. It is a demonstration, not a real-world dataset.

## 2. Import and prepare data

Use **Import data...** for UTF-8 CSV/TSV/semicolon files, Excel XLS/XLSX, or supported
SPSS SAV/ZSAV files. The public `examples/demo.csv` is a safe first import.

1. Inspect headers, delimiter and decimal convention in the preview. Commas may
   mean decimal separators or field delimiters; choose explicitly.
2. Assign numeric, text or ignored column roles. Text categories are retained but
   are not silently treated as quantitative measurements.
3. Declare missing markers and choose the supported treatment. Check the original
   and prepared views, replacement/removal counts and retained source rows.
4. For Excel choose the worksheet and header row. Formula cells use cached values;
   Bright-Stats does not run spreadsheet macros or recalculate formulas.
5. For SPSS review labels and whether user-defined missing codes should be missing
   or retained as observed data.

Do not mistake arbitrary category codes for meaningful metric distances. Use
**Transform / recode data...** to create explicit dummy/recode columns where
appropriate. Fixed operations include affine/log/square-root/square/product/sum/
mean/reverse transformations. They append columns and retain original-cell lineage;
transformations precede missing-value preparation. Check domains and scale bounds.
All-level dummy coding can cause collinearity when an intercept is also present.

### Pairwise missing-data workflow

Choose **Pairwise missing-data estimation...** for overlap matrices or configured
PLS/PLSc/GSCA/covariance-only SEM. This recovers all original numeric rows, including
cases removed by prior casewise preparation. Each variable pair has its own actual
count and pair-specific covariance/correlation. Read the counts and matrix warnings.
This method needs an appropriate missingness assumption such as MCAR; it is not
FIML or general MAR correction. It does not fabricate case scores or ordinary
incomplete-data likelihood tests. Non-PD matrices are not silently repaired.

## 3. Specify the model

Create measured constructs, assign the intended indicator columns, choose the
measurement type and weight mode, and connect the structural paths. Use the typed
editor for the supported PLS, GSCA, covariance-SEM, regression and PROCESS families.
Inspect node/link properties and bindings; a visible diagram is not proof that
its requested calculation is supported. Illegal or unsupported graphs are rejected.

- Reflective/formative measurement and Mode A/B weights are separate choices.
- Check construct names, indicator direction/reverse coding and the target equation.
- For RAM SEM inspect observed order, fixed markers, free parameters, equality
  constraints, bounds, intercept/mean options and identification.
- Use undo/redo and connected-subgraph copy/paste; verify identifiers and bindings
  after editing. Save before a substantial redesign.

Repeated-indicator HCMs can use the same physical indicator across blocks, but not
a duplicate within one block. For two-stage HCMs declare lower-order dimensions,
retained constructs and the second-stage graph. An embedded specification includes
its repeated-indicator HOC/LOC relationships in stage one; use a distinct name for
the stage-one HOC and final HOC. The two-stage bootstrap refits both stages.
Exported score-model projects condition on their saved scores; their warning is
important when doing subsequent inference.

## 4. Choose an analysis

| Aim | Workflow | What to inspect |
| --- | --- | --- |
| Composite structural modeling | PLS-SEM, Mode A/B, path/factor settings | Weights/loadings, paths, R-squared, reliability, convergence and identification caveats |
| Reflective common-factor correction | Consistent PLS / PLSc | Reliability admissibility, corrected correlations and failed corrected fits |
| Covariance/factor modeling | CB-SEM/CFA and grouped/invariance workflows | Specification, properness, identification, fit/residuals and constraints |
| Generalized components | GSCA | Masks/cross-loadings, objective, convergence and measurement interpretation |
| Continuous/binary outcomes | Linear/robust or logistic regression | Outcome coding, covariance method, rank, residuals or separation |
| Dimension reduction | PCA/model PCA and rotations | Selected variables, covariance/correlation choice, retention and rotation |
| Moderated/indirect relationships | PROCESS, nonlinear/polynomial and selected copula systems | Equation hierarchy, fixed probes, assumptions and full-pipeline uncertainty |
| Higher-order components | Explicit two-stage HCM | Stage definitions, repeated/consumed dimensions, both-stage estimation |
| Held-out performance | Predictive validation / CVPAT / model comparison | Training-only preparation, matching splits, benchmarks and held-out errors |
| Heterogeneity | Group comparisons, MICOM, FIMIX or PLS-POS | Membership definition, minimum group sizes, seeds/multistart, invariance/degeneracy |
| Relative importance/performance | IPMA | Ranges, reverse coding, score units and indicator/construct interpretation |
| Necessity/measurement constraints | NCA or tetrad analysis | Estimator/subset definitions, degeneracy, multiplicity and assumptions |

For adjusted copula systems, select the explicit adjusted estimator and specify
endogenous variables, exogenous/binary controls, equations and polynomial terms.
Do not infer causal identification merely from significant paths or a successful
control-function fit. Original and adjusted formulations are distinct methods.

## 5. Resampling and interpretation

Run a small supported bootstrap initially to check settings and model behavior,
then increase attempts for the intended final analysis. Save the seed, confidence
level, interval method and inference convention. Percentile and genuine jackknife
BCa are available in supported standard workflows; reference compatibility
conventions have separately defined intervals/tests. BCa may require many extra
full fits. Different conventions should not be mixed under the same label.

Inspect attempted/successful/failed counts and failure categories. Failed fits
are not replaced. Singular designs, inadmissible reliability/covariance, undefined
quantities or poor identification need investigation. A missing bound or P value
is not zero. Convergence alone does not establish a valid model. Confidence bands
at fixed probes are not automatically simultaneous bands or causal effects.

Use **Cancel calculation** or Escape during supported tasks. A cancelled task does
not yield a valid partial report. Previously saved files remain separate snapshots.

## 6. Prediction and model comparisons

Choose repeated folds or holdout settings, keep reproducible seeds, and compare
candidates on matching samples/splits. Preparation should use training data within
the supported prediction pipeline. Review original-unit RMSE/MAE, benchmarks,
Q-squared prediction and the applicable CVPAT/model-comparison assumptions.
Training fit is not evidence of unseen-case performance. Comparisons of covariance
models also require the declared nesting/likelihood conditions.

## 7. Save, report and share

- **Save project as...** retains data, model and supported settings. A workspace
  catalogs local projects and history; removing a catalog entry does not imply
  deleting the underlying project file.
- **Save report...** writes a numeric/input/settings snapshot. Reopening a saved
  report does not make it a live recalculation of the current model.
- **Export HTML report...** produces self-contained offline HTML. **Export graphic...**
  supports the current diagram and applicable plot panels in PNG/SVG.
- Use the report-section selector to navigate long results. Review all assumptions
  and warnings, not just the first coefficient table. Display rounding does not
  change stored numeric precision.

Projects and reports can contain the original dataset and category labels. Review
and anonymize those files before sharing them. The public repository contains only
synthetic examples, not your own analysis files.

## 8. Preferences and good practice

Choose light/dark/high-contrast presentation, navigation language, numeric locale,
precision, font and supported working-history/data limits in Preferences. Scientific
labels remain English and user text is retained. Build a modest model first; large
model/target/bootstrap combinations can exceed memory or implementation budgets.

Keep a reproducible record of the question, data preparation, model, assumptions,
seed/options, diagnostics, sensitivity checks and exported report. Compare relevant
alternative specifications and benchmarks. The app supports analysis; it does not
automatically choose a scientifically justified model or certify a conclusion.


## Multi-level hierarchies

Use **Multi-level hierarchy...** to declare a sequence of transitions. Start with
the current source blocks and paths. Each transition lists retained constructs,
higher-order dimensions, measurement type, weights and the next structural graph.
Indices refer to the immediately previous level, not the original column numbers.
The dialog can add transitions with a stage builder; review the full JSON before
running. Source-column and model bindings are checked when settings are reused.

Choose composite PLS or hierarchical PLSc moments explicitly. The latter propagates
reliability-corrected covariances, with no invented higher-factor individual scores.
Reflective factor correction needs at least three indicators/dimensions and proper
reliabilities/covariances. A failed correction should prompt model/data inspection,
not forced matrix repair. Inspect all level-specific results and warnings.
**Hierarchy pipeline bootstrap...** resamples original cases and refits every level;
standard percentile and genuine leave-one-out BCa intervals are supported.

## Latent interaction likelihood

Use **Latent interaction likelihood...** for a separate Normal/Student-t latent product or
quadratic likelihood. Define predictor/outcome indicator columns and product pairs
in its dialog; the ordinary canvas paths are not silently copied. A term with the
same predictor twice is a quadratic. Choose jointly centered Normal or covariance-scaled Student-t predictors (fixed df > 4),
with fixed unit first-indicator markers; outcome measurement uses a fixed zero
first-marker intercept. The supported model has 1–4 predictors, one outcome and
3–12 disjoint reflective indicators per factor, with independent measurement errors.

Selected indicators must be complete in the original retained source, even if the
prepared table contains mean replacements. Missing cells in unused columns are
allowed. This is not FIML. The declared Student-t family is a distributional assumption,
not a general correction for nonnormality. Student-t integration can be expensive;
review both-order stability and the work budget.

Run the point fit first. Inspect optimizer convergence, the two quadrature orders,
integration stability, information rank and boundary diagnostics separately. If
integration changes materially, increase the two quadrature orders within the
stated limits; do not interpret a merely converged but unstable solution. Multistart
helps explore local optima but does not prove a global optimum. Use the optional
matched linear baseline only on the same observations and measurement model.

Reports show raw marker-unit estimates, observed-information and sandwich SEs,
likelihood/AIC/BIC, model-implied latent explained variance, and conditional-slope
plots. Plot bands are pointwise observed-information intervals, not bootstrap
intervals or causal evidence. CFI/TLI/RMSEA are intentionally absent for this
non-Gaussian marginal likelihood. **Latent likelihood bootstrap...** refits all
measurement/structural parameters and the integration check per original-case draw.
BCa also refits every leave-one-out sample and can be expensive. Failed replicates
are counted without replacement; use adequate attempts for substantive inference.

Save the project and its report archive. Reopening a report restores the estimates,
settings and conditional plots. Export HTML for tables and PNG/SVG for graphics.
The CLI equivalents are `latent-interaction`, `latent-interaction-bootstrap`,
`hierarchy-pls`, `hierarchy-plsc` and their `-bootstrap` variants, taking a JSON file.


## Modern research workflow

Start on **Home**. Its three built-in, seeded synthetic examples demonstrate missing
continuous data, ordered-response modeling and clustered research. Opening an
example asks before replacing unsaved work. Nothing is fitted until you request it.
**Review analysis readiness** describes missingness, constants, rank and bindings;
a readiness screen is not a model-validity certificate.

Under **Preferences**, choose **Light**, **Dark**, **System** or **High contrast**,
plus Full/Reduced/Off motion, accent, density, font/numeric format, layout and chart
presets. System follows OS appearance changes. Reduced/Off avoid model-layout
movement and indeterminate activity animation; numerical values are never animated.
Layout presets rearrange presentation without changing data or model estimates.

On the model canvas, use zoom/fit, Space or middle-button pan, Shift-drag on empty
space for a marquee, and the minimap to navigate. Grid and snapping are optional.
Undo/redo track logical edits; short visual layout transitions do not alter estimates.

Reports provide factual overview cards, exact method/provenance, a searchable tree,
collapsible assumptions and numeric table sorting/filtering/pinning. Filters affect
the view; report exports retain the complete canonical table. Mixed-value sorting
places numbers before text. Long labels remain available through tooltips.

On supported charts use **Ctrl+wheel** to zoom, **Alt+drag** to pan, **Ctrl+0** to
reset, and legend clicks/keyboard activation to hide series. Right-click to reset,
restore all series or copy plotted TSV. Hiding a series does not delete its data;
SVG metadata retains source values and visibility state. Power plots shade finite-
replicate Wilson limits; lines between tested sample sizes are visual guides only.

Choose **Interval & matrix graphics** in the analysis overview for compatible
archived confidence/credible intervals and fitted covariance or ordinal-response
residual matrices. These use saved numeric values without reconstructing SEs or
P values. Parameter units can differ. Heatmaps show relative signed magnitude,
not significance. Hover or arrow keys expose full cell values; Enter links named
columns. Export the selected diagnostic as SVG in that window.

Explicit source-bound points can highlight original/prepared data rows or columns.
Use **View linked data** after selecting one. Row identities survive preparation
removal and table sorting. A report from a different source is rejected; open its
source project first. Unsupported plots do not invent a mapping.

## Advanced estimators

- **FIML** uses original selected continuous source cells and their missingness,
  even when the working grid was mean-replaced. Configure means/covariances/paths,
  then review convergence, local information rank, bounds, informative rows and
  pattern accounting. MAR is an assumption, not a diagnosed fact.
- **Multiple imputation** creates proper continuous normal completed draws. Review
  independent-chain traces and split diagnostics, each completed fit, and Rubin
  within/between/total uncertainty, degrees of freedom and missing information.
  Failed analyses are not silently dropped. Preserve seeds and draw hashes.
- **Ordinal SEM** requires explicitly ordered category labels and reflective factor
  bindings. Confirm every variable's order and selected-row policy. Review category
  counts, polychoric estimation, full robust covariance, fit adjustments and residuals.
- **Power/precision studio** needs a complete generating model, target/null,
  sample-size grid, repetitions, disturbance and missingness scenarios. Inspect all
  attempted and failed fits, conditional/per-attempt rates and Monte Carlo limits.
- **Cluster/two-level studio** binds a real source cluster column. Marginal
  sandwich/whole-cluster bootstrap and within/between Gaussian likelihood are
  different analyses; choose deliberately. Inspect informative groups and source IDs.
- **Regularized SEM** specifies elastic-net candidates and nested validation/stability
  budgets. Outer assessment remains separate from training selection. Inspect failed
  folds/candidates and retained scales; selection does not justify ordinary P values.
- **Bayesian SEM** requires explicit proper-prior review, chains, warmup, retained
  draws and seeds. Inspect all chain traces, rank/folded R-hat, bulk/tail ESS, MCSE,
  credible intervals, and actual prior/posterior predictive replications.
- **Influence/specification studio** refits selected case or whole-cluster deletions,
  or declared alternative models on one common source sample. Inspect failures and
  exact identities; no automatic deletion, pooled P values or causal conclusions.

CLI commands include `sem-fiml`, `impute-data`, `sem-mi`, `ordinal-sem`,
`sem-simulate`, `sem-cluster`, `sem-two-level`, `sem-regularized`, `sem-bayesian`,
`sem-influence` and `sem-multiverse`, each taking a method-specific JSON file.
Use the desktop dialogs and saved project settings to learn the schema; a project
file is not interchangeable with a CLI request. Unsupported fields/invalid bounds
are rejected rather than silently interpreted.

## Calculation details and reproducibility

**Calculation details** shows actual task phase, elapsed time and reported work.
Known iteration/attempt totals can produce a fraction; unknown solver work remains
indeterminate. Attempts include failed fits. Engine return and result-view rendering
are distinct phases. Cancel from the panel/footer or press Escape; a cancellation
is not saved as a completed report. A calculation that already finished before the
request can still return its completed result. Cancellation does not roll back your
configured settings.

Choose **Export reproducibility bundle...** for a local ZIP. The default contains
analysis settings, runtime/code identity and instructions, plus a checksum manifest;
it excludes observations, import history and report snapshots. Settings still contain
scientific labels and potentially case/group membership or user-entered text.
Explicitly enable data to include the project, and optionally the current report
(which contains its historical source). Select each entry to inspect its complete
text and cautions. Changing inclusion clears the review checkbox. Mark it reviewed,
then save. No programs execute and no files are uploaded.

Keep the archive private when it contains sensitive content. Extract and verify
entry sizes/SHA-256 against `manifest.json`; checksums do not authenticate authorship.
Open `project.json` or `analysis.bsreport.json` through the corresponding app action.
Settings-only archives require original data to be supplied separately. Matching
seeds/runtime/code identity improve reproducibility but do not guarantee bit-identical
floating-point results on every platform.
