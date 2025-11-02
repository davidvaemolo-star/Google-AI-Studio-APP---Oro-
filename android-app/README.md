# Oro Native Android Scaffold

This directory contains a Jetpack Compose starting point that mirrors the behaviour of the original Vite/React Oro training planner. The goal is to provide Android Studio ready source files so you can open the project, sync Gradle, and continue iterating natively.

## React -> Android mapping

- **Application state**  
  - React `App.tsx` keeps `view`, `zones`, and `devices` in component state.  
  - Android uses a `MainViewModel` (`MainViewModel.kt`) with `MutableStateFlow` for view selection, zone list, and devices.
- **Navigation**  
  - React toggles screens with `BottomNavBar`.  
  - Android uses a Material3 `NavigationBar` (`BottomNavigation.kt`) that switches the Compose content.
- **Training zones**  
  - React `ZoneBlock` renders each card, handles +/- controls, and drag-sort.  
  - Android `TrainingScreen.kt` renders a `LazyVerticalGrid` of `ZoneCard` composables with Material buttons for increment/decrement and uses `rememberReorderableLazyGridState` (from `compose-reorderable`) for drag-and-drop.
- **Device management**  
  - React `ConnectionScreen` simulates scans, connects devices, and lets you reorder assigned seats.  
  - Android `ConnectionScreen.kt` reuses the same fake scan/connect logic inside the `MainViewModel`, and exposes drag-to-reorder using `rememberReorderableLazyListState`.
- **Icons & styling**  
  - React ships custom SVG paths in `Icon.tsx`.  
  - Android defines matching `ImageVector` assets in `Icons.kt` and applies gradients/colours pulled from `Theme.kt`.

Open the project in Android Studio (`File > Open > android-app`) and let Gradle sync.  Most of the work you will do from here is inside `app/src/main/java/com/orotrain/oro/ui`.
