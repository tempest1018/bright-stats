# Synthetic examples

`demo.csv` and `pls.json` contain the same100 generated observations, with nine
numeric indicators for X, M and Y. They are entirely synthetic: no personal data,
private project or vendor sample dataset is included.

Open `pls.json` with the project's public launch helper `--example`, or import
`demo.csv` and define three reflective ModeA blocks: X1–X3, M1–M3 and Y1–Y3.
Connect X→M, X→Y and M→Y. Calculate PLS-SEM and inspect convergence, measurement
results, paths and R-squared. Use a fixed seed if trying bootstrap. The numbers
illustrate the software workflow, not a substantive scientific conclusion.
