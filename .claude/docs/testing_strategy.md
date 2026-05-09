# Testing Strategy — LUPITA

## Test Pyramid

```
         [ UI / E2E ]          ~15% — Espresso, critical flows (Sprint 3)
      [ Integration ]          ~25% — Firebase Emulator, Room in-memory
   [ Unit Tests ]              ~60% — JUnit 4 + MockK + Coroutines-test
```

## Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Coverage report (HTML + XML)
./gradlew jacocoTestDebugUnitTestReport

# Verify coverage threshold (currently 20%)
./gradlew verifyCoverage

# Instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest
```

## Test File Locations

```
app/src/test/                          # Unit tests (run on JVM via Robolectric)
  managers/FavoritesManagerTest.kt     # Tests 1+2: Firestore path + auth
  managers/CheckInManagerTest.kt       # Tests 3+4: duplicate check-in + stats
  managers/AuthManagerTest.kt          # Tests 5+6: guest mode flag
  routing/PreferencesValidationTest.kt # Test 7: input validation
  routing/RouteGeneratorTest.kt        # Test 8: Gemini prompt structure
  db/AppDatabaseTest.kt                # Test 9: Room CRUD in-memory

app/src/androidTest/                   # Instrumented tests (run on device)
  base/FirestoreEmulatorTest.kt        # Base class for emulator tests
  firestore/FirestoreSecurityRulesTest.kt  # Test 10: security rules
  ui/LoginToFavoriteFlowTest.kt        # E2E: login → guest mode
```

## Firebase Emulator

```bash
# Start (requires Firebase CLI: npm install -g firebase-tools@13)
./scripts/start-emulators.sh

# Stop
./scripts/stop-emulators.sh
```

Emulator config: `firebase.json` (Firestore port 8080, Auth port 9099).

## Coverage Thresholds

| Sprint | Threshold | Command |
|---|---|---|
| Sprint 1 (current) | 20% | `./gradlew verifyCoverage` |
| Sprint 2 | 45% | Update threshold in `build.gradle.kts` |
| Sprint 3 | 65% | Update threshold in `build.gradle.kts` |

## Key Testability Decisions

- `FavoritesManager` and `CheckInManager` are classes (not objects) with injectable constructors
- `CheckInManager` accepts `clock: () -> Long` for deterministic time-based tests
- `RouteInputValidator` and `RouteGenerator` are pure objects (no Android Context) → directly testable in JVM
- `AuthManager` remains an `object` but tests use Robolectric for real Context
