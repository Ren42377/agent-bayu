# Agent Instructions

## Code Writing Instructions

- Keep logging minimal. Add logs only for errors, important state changes, or diagnostics that materially help debugging.
- Write all text inside code in English, including variable names, function names, class names, constants, string literals, error messages, log messages, and docstrings.
- Do not use emoji.
- Do not use em dashes.
- Do not add comments in code.

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
- GitHub Actions uses Gradle dependency caching. The debug APK is uploaded as a plain `.apk` artifact, previous APK artifacts are deleted at the start of every run, and lint plus unit tests run after the APK is published so a failing check never blocks the download.
- Do not download Android SDK, Gradle distributions, or build artifacts locally unless required for a specific test. Remove temporary downloads after use.
