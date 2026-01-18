# Как собрать Materia Launcher

Это руководство поможет вам собрать Materia Launcher из исходного кода.

## 1. Системные требования

Убедитесь, что ваша система соответствует следующим требованиям:

*   **Операционная система**:
    *   Windows 10 или выше
    *   macOS
    *   Linux
*   **Java**:
    *   Установленная или портативная [GraalVM 25](https://www.graalvm.org/downloads/). Это необходимо для компонента MLGD (Materia Launcher Game Daemon).
*   **Свободное место**:
    *   Минимум 2 ГБ свободного места на диске для загрузки библиотек и зависимостей.

## 2. Клонирование репозитория

Сначала клонируйте репозиторий проекта на свой локальный компьютер с помощью Git:

```bash
git clone https://github.com/Chokopieum-Software/MateriaKraft-Launcher.git
cd MateriaKraft-Launcher
```

## 3. Сборка проекта

Проект использует Gradle Wrapper (`gradlew`) для сборки. Вам нужно будет собрать два основных компонента: сам лаунчер и MLGD (Materia Launcher Game Daemon).

Откройте терминал или командную строку в корневом каталоге проекта.

### Шаг 3.1: Сборка MLGD (нативная компиляция)

Этот компонент требует GraalVM. Выполните следующую команду:

**Для Windows:**
```bash
gradlew.bat MLGD:nativeCompile
```

**Для macOS и Linux:**
```bash
./gradlew MLGD:nativeCompile
```

### Шаг 3.2: Сборка и упаковка лаунчера

После сборки MLGD соберите основной дистрибутив лаунчера:

**Для Windows:**
```bash
gradlew.bat packageDistributable
```

**Для macOS и Linux:**
```bash
./gradlew packageDistributable
```

После успешного выполнения обеих команд вы найдете готовые для запуска дистрибутивы в каталоге `build/distributable/`.
