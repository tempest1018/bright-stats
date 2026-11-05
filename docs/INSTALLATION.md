# Windows and Linux source setup

These instructions build a development preview. They do not install a system
service, change systemwide Node, or provide an offline installer. Keep the complete
output directory together: the application JAR depends on its `lib` directory.

## Prerequisites

- Windows x86-64 or Linux x86-64. ARM64 and macOS are not supported by these scripts.
- A **JDK 25 or newer**, including `java`, `javac` and `jar`. A JRE alone is insufficient.
  Obtain a JDK from an official supplier such as [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25).
- [Python 3.10 or newer](https://www.python.org/downloads/) and [Git](https://git-scm.com/downloads).
- Internet for the first dependency download; approximately 1 GB free working space
  is a practical starting allowance for the JDK, dependencies, sources and build.
- A graphical desktop for the GUI. Linux needs the distribution's JavaFX native
  prerequisites: GTK 3, X11/Xtst/Xi/Xrender and ALSA libraries. Package names vary
  across Ubuntu/Debian releases. SSH without a display supports the CLI only.

## Windows PowerShell

Install the prerequisites, open a new PowerShell window, and check:

```powershell
java -version
javac -version
python --version
git --version
```

If needed, point this PowerShell session to your JDK. Replace the example path
with the folder containing its `bin` directory:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
```

Clone and build:

```powershell
git clone https://github.com/tempest1018/bright-stats.git
cd bright-stats
python scripts/build_public.py --test
python scripts/run_public.py --example
```

If `python` is unavailable but the Python launcher is installed, replace it with
`py -3`. Output: `build/public/windows-x64/`.

## Linux shell

Install Git/Python through your distribution and a JDK from its official supplier.
Set `JAVA_HOME` if your JDK is not already on PATH:

```sh
export JAVA_HOME="$HOME/tools/jdk-25"  # replace with your actual JDK directory
java -version
"$JAVA_HOME/bin/javac" -version
python3 --version
git clone https://github.com/tempest1018/bright-stats.git
cd bright-stats
python3 scripts/build_public.py --test
python3 scripts/run_public.py --example
```

Output: `build/public/linux-x64/`. Run from a graphical login session. In a
headless environment use the CLI; GUI/display setup is separate.

## Run without the example

```sh
python scripts/run_public.py
```

The GUI uses the current user's normal local profile and does not upload analysis
files. Save projects to a folder you control. Close the window to stop the app.

## Command line

Windows:

```powershell
java -jar build/public/windows-x64/bright-stats.jar --help
java -jar build/public/windows-x64/bright-stats.jar pls examples/pls.json
```

Linux:

```sh
java -jar build/public/linux-x64/bright-stats.jar --help
java -jar build/public/linux-x64/bright-stats.jar pls examples/pls.json
```

JSON output can be redirected to a file. CLI results are not automatically a GUI
report archive: use the app's Save report action for that format. The `decode`
command requires a caller-supplied authorized key; no vendor keys are included.

## Offline use and rebuilding

After one successful online build:

```sh
python scripts/build_public.py --offline --test
python scripts/run_public.py
```

The build verifies SHA-256 for every cached dependency. Missing or altered cache
files cause an error in offline mode. Do not disable integrity checks; rebuild
online to restore the exact pinned artifact. An offline source rebuild also needs
the JDK and Python already installed. The future installers will bundle a runtime;
that packaging has not been completed or certified.

## Troubleshooting

- **JDK missing/old:** check `JAVA_HOME`, `java -version` and `javac -version`; use
  JDK 25+. The script prefers `JAVA_HOME` over PATH.
- **Dependency error:** check connectivity to `repo.maven.apache.org`; use the
  committed lock file unchanged. No GitHub token is required to build public source.
- **Linux native-library/display error:** install your distribution's GTK/X11/ALSA
  JavaFX prerequisites and run in a graphical desktop. Do not use root for the GUI.
- **Out of memory:** reduce dataset/model size or resampling attempts. The launcher
  currently sets a 2 GiB maximum Java heap; it does not reserve all that memory at startup.
- **Old class files:** rerun the build; it recreates only its checked generated
  classes/libraries/test directories. Do not combine output from different versions.
- **A statistical calculation is rejected:** inspect the error, model specification,
  data types, variance, rank and missingness. Do not replace undefined results with zero.
