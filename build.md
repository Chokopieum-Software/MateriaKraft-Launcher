# How to Build Materia Launcher

This guide will help you build Materia Launcher from the source code.

## 1. System Requirements

Ensure your system meets the following requirements:

*   **Operating System**:
    *   Windows 10 or higher
    *   macOS
    *   Linux
*   **Java**:
    *   An installed or portable version of [GraalVM 25](https://www.graalvm.org/downloads/). This is required for the MLGD (Materia Launcher Game Daemon) component.
*   **Free Space**:
    *   A minimum of 2 GB of free disk space for downloading libraries and dependencies.

## 2. Cloning the Repository

First, clone the project repository to your local machine using Git:

```bash
git clone https://github.com/Chokopieum-Software/MateriaKraft-Launcher.git
cd MateriaKraft-Launcher
```

## 3. Building the Project

The project uses the Gradle Wrapper (`gradlew`) for building. You will need to build two main components: the launcher itself and MLGD (Materia Launcher Game Daemon).

Open a terminal or command prompt in the root directory of the project.

### Step 3.1: Build MLGD (Native Compilation)

This component requires GraalVM. Execute the following command:

**For Windows:**
```bash
gradlew.bat MLGD:nativeCompile
```

**For macOS and Linux:**
```bash
./gradlew MLGD:nativeCompile
```

### Step 3.2: Build and Package the Launcher

After building MLGD, build the main launcher distributable:

**For Windows:**
```bash
gradlew.bat packageDistributable
```

**For macOS and Linux:**
```bash
./gradlew packageDistributable
```

After successfully running both commands, you will find the ready-to-run distributables in the `build/distributable/` directory.
