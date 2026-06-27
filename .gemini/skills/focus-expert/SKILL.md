---
name: Focus Interceptor Expert
description: Core architectural rules, best practices, and testing mandates for the Focus app. ALWAYS ACTIVATE this skill when modifying the Focus codebase.
---

# Focus App Development Constraints

Welcome, AI Agent. When you are operating within the `focus` project, you are required to strictly adhere to the following principles.

## 1. Architectural Mandates
The codebase underwent a major refactoring to eliminate "God Objects". Do not combine responsibilities back into massive files.
- **`AppInterceptorService.kt`**: Must only handle `AccessibilityEvent` traffic and lifecycle hooks. No business logic.
- **`SessionManager.kt`**: Owns all timer/session logic. Tracks absolute `expiryTime` to prevent bypasses.
- **`OverlayManager.kt`**: Owns Native View/WindowManager inflation and Jetpack Compose lifecycle injection.
- **`OverlayUI.kt`**: Contains dumb, stateless Jetpack Compose views.

## 2. Testing Constraints (CRITICAL)
- **Zero Regression Policy:** If you modify `SessionManager.kt` or any critical coroutine timing logic, you **MUST** run `./gradlew testDebugUnitTest` before committing.
- Do not bypass failing tests; fix the underlying logic.
- We use Kotlin Coroutines `TestScope` and `timeProvider` injection to mock absolute timestamps in tests. Do not use raw `System.currentTimeMillis()` in `SessionManager`; use `timeProvider()`.

## 3. Logging Strategy
- **Never use `android.util.Log.d()` directly.**
- ALWAYS use `FocusLogger.d()`, `FocusLogger.i()`, or `FocusLogger.e()`.
- `FocusLogger` abstracts Crashlytics breadcrumbs safely. Using raw `Log` will break unit tests and skip production analytics.

## 4. UI/UX Rules
- Jetpack Compose views must be state-hoisted.
- Any new features must maintain the Material 3 design system. Use beautiful, modern aesthetics with dynamic animations (e.g., the breathing animation in `OverlayUI`).

## 5. Coroutine Best Practices
- Never use `GlobalScope`.
- Ensure tests that launch infinite `while(isActive)` loops (like `sessionMonitorJob`) clean up properly by calling `sessionManager.clearAll()` at the end of the `runTest` block to avoid `UncompletedCoroutinesError`.
