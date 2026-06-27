# Best Practices & Architectural Guidelines

Welcome to the Focus Interceptor project. This document outlines the core principles, coding standards, and architectural patterns we enforce to ensure the application remains high-performance, maintainable, and robust.

## 1. Architectural Pattern: MVVM (Model-View-ViewModel)
We strictly adhere to the MVVM pattern for the UI layer to enforce a separation of concerns:
- **Model**: Data classes, Repositories, and DataStore interactions (e.g., `DataStoreRepository`). Data sources should be exposed as Kotlin Flows.
- **ViewModel**: State holders that manage UI state and handle business logic. ViewModels expose `StateFlow` to the UI and receive user intentions via function calls.
- **View**: Jetpack Compose functions. Views must be as dumb as possible, only observing state and emitting events to the ViewModel. **No business logic in Composable functions.**

## 2. Jetpack Compose Guidelines
- **State Hoisting**: Keep Compose functions stateless where possible. Hoist state to the caller or the ViewModel.
- **Performance**: 
  - Use `remember` and `derivedStateOf` to avoid unnecessary recompositions.
  - Avoid performing heavy computations (like loading Bitmaps or querying the PackageManager) directly in the UI thread or within the composition phase.
  - Use `LazyColumn` for lists and specify `key`s for all items.

## 3. High-Performance Background Services
The `AppInterceptorService` is the hot-path of the application and must operate with ultra-low latency:
- **Zero Allocations**: Avoid object allocations (like `new String()`, or complex object creation) inside the `onAccessibilityEvent` callback.
- **In-Memory Caching**: Keep critical data (like targeted packages) in volatile memory caches rather than querying DataStore synchronously.
- **Coroutines over Threads**: Use Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`) for async work instead of raw Threads or Handlers. 

## 4. General Kotlin Rules
- Use `val` instead of `var` wherever possible.
- Leverage Kotlin Extension functions to reduce boilerplate.
- Use structured concurrency: never launch a coroutine using `GlobalScope`. Always use lifecycle-aware scopes (`viewModelScope`, `lifecycleScope`) or structured Custom Scopes.

## 5. Security & Defensive Programming
- Ensure the app respects the user's intent. The anti-bypass reboot constraints must remain secure.
- Gracefully handle situations where permissions are revoked unexpectedly.
