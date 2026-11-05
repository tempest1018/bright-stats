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

## Windows step by step

### 1. Install the prerequisites

If you already have the required versions, skip their installation.

- Download a **Windows x64 JDK 25** installer from the official
  [Temurin downloads](https://adoptium.net/temurin/releases/?version=25).
  Choose **JDK**, not JRE. Run the MSI and enable its **PATH** and **JAVA_HOME**
  options. Follow the [official Windows installation guide](https://adoptium.net/installation/windows).
- Install Python 3.10+ from [Python for Windows](https://www.python.org/downloads/windows/).
  Follow the installer instructions to make `python` or the `py` launcher available.
- Install [Git for Windows](https://git-scm.com/downloads/win), including its
  command-line tools.

Close existing terminals and open a **new PowerShell** window. Check:

```powershell
java -version
javac -version
jar --version
python --version
git --version
```

All three Java tools must report 25 or newer; Python must report 3.10 or newer.
If `python` does not work, try `py -3 --version` and replace every `python` command
below with `py -3`. If Windows opens the Store instead, use the installed Python
launcher or correct the Python PATH/app execution alias.

If Java is missing or an older installation is selected, replace the example
below with the actual JDK installation folder (the folder containing `bin`):

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
jar --version
```

These assignments affect this terminal only. The installer options can make the
selection persistent. The build helper prefers `JAVA_HOME` over PATH, so correct
an old `JAVA_HOME` even if `java -version` on PATH already looks right.

### 2. Get the source

Choose a folder where you can write files. For example:

```powershell
Set-Location $HOME
git clone https://github.com/tempest1018/bright-stats.git
Set-Location bright-stats
```

If you already cloned the repository, open that existing folder instead of cloning
again. Run the remaining commands from the folder containing `README.md`.

### 3. Build the app

```powershell
python scripts/build_public.py
```

Allow the first build to download its pinned dependencies. Wait for a successful
exit before launching. Output is `build/public/windows-x64/bright-stats.jar` with
an adjacent `lib` directory and `build-receipt.json`.

Optional public functionality checks (may take several minutes):

```powershell
python scripts/build_public.py --test
```

### 4. Launch and use the app

```powershell
python scripts/run_public.py
```

To start with the supplied synthetic PLS model instead:

```powershell
python scripts/run_public.py --example
```

Follow the [user guide](USER_GUIDE.md) to import your data, edit a model, calculate,
inspect diagnostics and save a project/report. Close the window to exit.

### 5. Open it again later

Open PowerShell in your existing `bright-stats` folder and run
`python scripts/run_public.py`. No rebuild or internet is needed for a normal
launch. If you open PowerShell elsewhere, first use `Set-Location` with the path
to your clone. Use the Python launcher substitution if that is how you installed
Python. There is no installed Start-menu shortcut in this source-build preview.

## Linux step by step

### 1. Install the prerequisites

Use a graphical **x86-64 Linux** session. These scripts do not supply ARM64 or
macOS dependencies. Distribution-specific package names differ.

On Ubuntu/Debian, install the basic tools:

```sh
sudo apt update
sudo apt install git python3 ca-certificates
```

Install GTK 3, X11, Xtst, Xi, Xrender and ALSA runtime libraries through your
distribution's package manager if they are missing. Desktop installations often
already include them; package names vary by distribution and release.

For the JDK, use an official supplier's installation instructions, such as
[Temurin Linux packages](https://adoptium.net/installation/linux). Follow its
repository/key setup for your distribution, then install `temurin-25-jdk`.
Alternatively, install without changing system Java:

1. Download the **Linux x64 JDK 25 tar.gz** from
   [Temurin downloads](https://adoptium.net/temurin/releases/?version=25).
2. Use your archive manager to extract it into a directory you own, such as
   `~/tools`. Note the full path of the extracted JDK folder.
3. Replace the placeholder below with that exact path:

   ```sh
   export JAVA_HOME="$HOME/tools/REPLACE-WITH-EXTRACTED-JDK-FOLDER"
   export PATH="$JAVA_HOME/bin:$PATH"
   ```

These exports apply to the current terminal. To reuse this private JDK later,
repeat them or add the same exports to your shell startup file. Do not paste the
placeholder unchanged. If using a package-managed JDK, ensure any existing
`JAVA_HOME` points to that installation rather than an older JDK.

Verify the tools before proceeding:

```sh
java -version
javac -version
jar --version
python3 --version
git --version
```

Java tools must report 25+ and Python 3.10+. A JRE alone cannot build the app.

### 2. Get the source

```sh
cd "$HOME"
git clone https://github.com/tempest1018/bright-stats.git
cd bright-stats
```

Use your existing clone if you have one. Run the remaining commands from the
folder containing `README.md`.

### 3. Build the app

```sh
python3 scripts/build_public.py
```

The first build downloads and verifies dependencies. Output is
`build/public/linux-x64/bright-stats.jar` with adjacent `lib` and
`build-receipt.json`. Wait for a successful exit.

Optional public functionality checks (may take several minutes):

```sh
python3 scripts/build_public.py --test
```

### 4. Launch and use the app

```sh
python3 scripts/run_public.py
```

Or open the included synthetic model:

```sh
python3 scripts/run_public.py --example
```

Launch from your graphical desktop as your normal user, not with `sudo`.
SSH without a display supports the CLI only. See the [user guide](USER_GUIDE.md)
for data import, modeling, calculations and saving. Close the window to exit.

### 5. Open it again later

Open a terminal in your existing `bright-stats` folder and run
`python3 scripts/run_public.py`. If you used a private JDK archive, set its
`JAVA_HOME`/PATH first as in step 1. There is no need to rebuild or reconnect to
the internet for a normal launch.

## Launch behavior

The Python helper launches the **GUI**. Double-clicking the JAR or using
`java -jar` is not the documented desktop launch method: the JAR's default entry
point is the CLI. Keep the platform output folder intact; do not copy only its
JAR. Windows and Linux builds have different native dependencies.

The GUI uses the current user's normal local profile and does not upload analysis
files. Save projects to a folder you control.

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

Windows (PowerShell):

```powershell
python scripts/build_public.py --offline --test
python scripts/run_public.py
```

Linux:

```sh
python3 scripts/build_public.py --offline --test
python3 scripts/run_public.py
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
