# Agent Instructions

## Code Writing Instructions

- Keep logging minimal. Add logs only for errors, important state changes, or diagnostics that materially help debugging.
- Write all text inside code in English, including variable names, function names, class names, constants, string literals, error messages, log messages, and docstrings.
- Do not use emoji.
- Do not use em dashes.
- Do not add comments in code.
- When a task is complete, create a notification through Termux:API when the API is available.

## Project Context

- This project is named Agent Bayu.
- The main programming language is Kotlin.
- This is a native Android application built with Kotlin and Jetpack Compose.
- The application is a personal AI assistant for its owner. It may connect to services such as Google Tasks, Google Calendar, WhatsApp, and Instagram only through explicit user permissions and configured integrations.
- Keep the Android project portable across supported Android environments.
- Treat credentials, tokens, personal messages, calendar data, and task data as sensitive information. Never commit them to the repository.

## Build Instructions

- Build the application using GitHub Actions. A push to the repository runs the Android CI workflow.
- The local project environment does not currently have Gradle build support. Do not assume that Gradle is installed locally.
- Prefer triggering the `workflow_dispatch` event from GitHub Actions when a debug APK is needed.
- GitHub Actions uses Gradle dependency caching and parallel checks. Debug APK artifacts are retained temporarily and old Agent Bayu artifacts are cleaned up automatically.
- If an APK is downloaded temporarily for testing with an attached Android device, install it with `adb install --user 0 <apk-path>` and delete the APK immediately after installation.
- Do not download Android SDK, Gradle distributions, or build artifacts locally unless required for a specific test. Remove temporary downloads after use.
