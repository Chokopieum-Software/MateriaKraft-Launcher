# How to Build Materia Launcher

This guide will help you build Materia Launcher from the source code.

## 1. System Requirements

Ensure your system meets the following requirements:

*   **Operating System**:
    *   Windows 10 or higher
    *   macOS
    *   Linux
*   **Java**:
    *   An installed or portable version of Java 21 or higher.
*   **Free Space**:
    *   A minimum of 2 GB of free disk space for downloading libraries and dependencies.

## 2. Cloning the Repository

First, clone the project repository to your local machine using Git:

```bash
git clone https://github.com/Chokopieum-Software/MateriaKraft-Launcher.git
cd MateriaKraft-Launcher
```

## 3. Building the Project

The project uses the Gradle Wrapper (`gradlew`) for building.

Open a terminal or command prompt in the root directory of the project.

### Build and Package the Launcher

**For Windows:**
```bash
gradlew.bat packageDistributable
```

**For macOS and Linux:**
```bash
./gradlew packageDistributable
```

After successfully running the command, you will find the ready-to-run distributables in the `build/distributable/` directory.
