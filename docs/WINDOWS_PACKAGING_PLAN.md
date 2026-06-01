# Windows Packaging Plan For LLM/Operator

Goal: generate a distributable Windows package for qfolder using `scripts\package-windows.ps1`.

## Expected Output

- Default portable package: `packages\windows\qfolder-windows-x64.zip`
- Optional installer outputs if requested and supported by the machine: `.exe` or `.msi` under `packages\windows\`

## Repository Context

- Project root contains `README.md`, `build.sh`, `scripts\package-windows.ps1`, and `p2p-client\pom.xml`.
- Main Maven module is `p2p-client`.
- Java requirement is JDK 21+.
- Windows packaging must run on Windows. Do not attempt to create the Windows package from Linux unless explicitly setting up a dedicated cross-packaging workflow, which this repo does not currently provide.

## Required Tools On Windows

Install or verify:

1. JDK 21+ with `jpackage.exe`.
2. Maven available as `mvn`, or `MAVEN_HOME` configured.
3. PowerShell.
4. Internet access, unless `cloudflared.exe` already exists at `%LOCALAPPDATA%\qfolder\bin\cloudflared.exe`.
5. Optional for installer formats: WiX Toolset, if `jpackage` requires it for `exe` or `msi`.

Recommended JDK: Eclipse Temurin 21 or another full JDK distribution that includes `jpackage.exe`.

## Preflight Checks

Open PowerShell in the repository root and run:

```powershell
Get-Location
Test-Path .\scripts\package-windows.ps1
Test-Path .\p2p-client\pom.xml
java -version
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\jpackage.exe" --version
mvn -version
```

Expected:

- Current directory is the repository root.
- Both `Test-Path` commands return `True`.
- `java -version` reports Java 21 or newer.
- `$env:JAVA_HOME` is not empty and points to a JDK, not a JRE.
- `jpackage.exe --version` succeeds.
- `mvn -version` succeeds.

If `JAVA_HOME` is missing, set it for the current PowerShell session, adapting the path:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

If Maven is installed but `mvn` is not in `PATH`, either fix `PATH` or set `MAVEN_HOME`:

```powershell
$env:MAVEN_HOME = "C:\Program Files\Apache\maven"
$env:Path = "$env:MAVEN_HOME\bin;$env:Path"
```

## Recommended Build: Portable App Image ZIP

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType app-image
```

Expected output directory:

```text
packages\windows\
```

Expected artifact:

```text
packages\windows\qfolder-windows-x64.zip
```

The script will:

1. Build `p2p-client` with Maven using `clean package -DskipTests -q`.
2. Copy `p2p-client\target\p2p-client-1.0-SNAPSHOT-fat.jar` to the jpackage input as `qfolder.jar`.
3. Copy `p2p-client\src\main\resources\qfolder.properties.example` as packaged `qfolder.properties`.
4. Copy local `cloudflared.exe` from `%LOCALAPPDATA%\qfolder\bin\cloudflared.exe`, or download it from Cloudflare GitHub releases.
5. Run `jpackage` with `--type app-image`.
6. Compress the generated app image to `qfolder-windows-x64.zip`.

## Optional Installer Builds

Only run these after `app-image` works.

For EXE:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType exe
```

For MSI:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType msi
```

If `jpackage` fails with a WiX-related error, install WiX Toolset, reopen PowerShell so `PATH` is refreshed, and retry.

## Post-Build Validation

After building the `app-image` ZIP:

```powershell
Test-Path .\packages\windows\qfolder-windows-x64.zip
Get-ChildItem .\packages\windows
```

Then validate manually:

1. Extract `qfolder-windows-x64.zip` to a temporary directory.
2. Run the generated qfolder executable inside the extracted `qfolder` folder.
3. Confirm the app opens.
4. Confirm it can start the local WebSocket endpoint.
5. Confirm `cloudflared.exe` is present under the packaged app content or otherwise available to the app.

If Windows SmartScreen warns about an unsigned app, that is expected unless the project has a signing certificate and signing flow configured. This repository currently does not document a signing step.

## Troubleshooting

### `JAVA_HOME no esta configurado`

Set `JAVA_HOME` to a JDK 21+ directory and retry.

### `jpackage.exe no encontrado en JAVA_HOME`

`JAVA_HOME` points to the wrong directory or to a JRE. Use a full JDK 21+ install.

### `mvn` Not Found

Install Maven and add it to `PATH`, or set `MAVEN_HOME`.

### Cloudflared Download Fails

Manually download:

```text
https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
```

Place it at:

```text
%LOCALAPPDATA%\qfolder\bin\cloudflared.exe
```

Then rerun the packaging script.

### `exe` Or `msi` Packaging Fails

First verify `app-image` works. For installer formats, install WiX Toolset if `jpackage` asks for it. If the goal is simply distribution/testing, prefer the ZIP portable package.

## Final Report Template

When done, report:

```text
Windows packaging result:
- Machine/OS:
- Java version:
- Maven version:
- Package type:
- Artifact path:
- Artifact size:
- Manual launch tested: yes/no
- Notes/errors:
```
