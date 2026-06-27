# Focus Interceptor

Focus Interceptor is a high-performance Android utility designed to help users minimize digital distractions through mindful delays. Built with jetpack Compose and leveraging Android Accessibility Services, it intercepts targeted application launches and imposes a customizable countdown before granting access, encouraging mindful app usage.

## Features
- **Mindful Pause Overlay**: Intercepts selected apps and forces a countdown, preventing impulsive opening.
- **Ultra-low Latency**: Highly optimized background service with in-memory caching for zero-lag interception.
- **Defensive Protections**: Prevents easy bypass by requiring a reboot to disable core protections.
- **Beautiful UI**: Modern, premium Jetpack Compose user interface.

## Prerequisites
- Android Studio Ladybug or later
- Android SDK 35
- Java 11

## Run Locally
1. Clone the repository.
2. Open Android Studio and select **Open** to choose the project directory.
3. Allow Android Studio to sync Gradle and build the project.
4. Run the app on an emulator or physical device.

## CI/CD
This project is configured with GitHub Actions to automatically generate a deployable debug APK for easy side-loading upon every push to the `main` branch. See the Actions tab in GitHub to download the latest artifact.

## Contributing and Architecture
Please see the [BEST_PRACTICES.md](BEST_PRACTICES.md) file for guidelines on contributing, code style, and the underlying architectural principles (MVVM) of the application.
