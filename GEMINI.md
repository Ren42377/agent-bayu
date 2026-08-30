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

## Tasks
- Your task is solely to improve the UI/UX; the core logic or main features remain unchanged, as that is the responsibility of another agent—Claude.
- Design the UI/UX to feature a "liquid glass" aesthetic—reminiscent of iOS apps or the general Apple style. You are required to conduct research beforehand to understand exactly how Apple's designs are presented.
- For Liquid Glass, you can use this repo https://github.com/Kyant0/AndroidLiquidGlass to make it work on Android.

## Build
- Never build the application yourself; that is the responsibility of the primary agent, Claude.