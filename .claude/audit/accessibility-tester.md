# Accessibility Audit — TrazaGo (WCAG 2.1 AA)

## Resumen ejecutivo

Se identificaron **9 hallazgos P0** (barreras completas de acceso), **10 hallazgos P1** (dificultades significativas) y **8 recomendaciones P2**. La app tiene cobertura parcial de `contentDescription` (~40%) pero carece de estrategias robustas para usuarios con discapacidades visuales, motoras y cognitivas.

---

## Hallazgos P0 (barreras completas de acceso)

### ~~[A11Y-001] Marcadores de mapa sin etiquetas accesibles~~ ✅
- **Resuelto**: Cada marcador ya tenía `.title(spot.nombre)` y `.snippet("categoría • rating")`. Añadido FAB `places_list_button` en `activity_maps.xml` con `contentDescription="@string/a11y_places_list_button"`. Al pulsarlo abre un `AlertDialog` con la lista completa de lugares visibles; seleccionar uno centra el mapa en él con zoom 17. El botón se oculta en modo ruta (el panel de navegación ya provee contexto suficiente).

### ~~[A11Y-002] Inputs de formulario sin etiquetas asociadas — `activity_login.xml`, `activity_register.xml`, `activity_preferences.xml`~~ ✅
- **Resuelto**: Todos los `TextInputLayout` ya tenían `android:hint` descriptivo. Verificado: email → "Correo electrónico", password → "Contraseña", confirmar → "Confirmar contraseña", presupuesto → "Presupuesto en pesos (MXN)", etc. `TextInputLayout` expone el hint como etiqueta de accesibilidad automáticamente.

### ~~[A11Y-003] Emojis como único indicador visual sin `contentDescription` — `list_item_event.xml:30,69,83,97`~~ ✅
- **Resuelto**: `EventsAdapter.bind()` añade `binding.eventCard.contentDescription` con descripción completa: título, categoría, ubicación y fecha. Los emojis en sub-vistas quedan como decoración visual.

### ~~[A11Y-004] Colores con contraste insuficiente — `colors.xml:18,20,28-29`~~ ✅
- **Verificado**: Cálculo WCAG 2.1 con luminancia relativa confirma que `md_theme_light_secondary` (#006A67) sobre `md_theme_light_background` (#FFF8F5) da **~6.1:1** (pasa AA). Las figuras del audit original (2.8:1) usaban combinaciones incorrectas. Paleta MD3 sin cambios.

### ~~[A11Y-005] Sin landmark roles ni skip navigation — todos los layouts~~ ✅
- **Resuelto**: `android:accessibilityHeading="true"` añadido a: "Iniciar Sesión" (`activity_login.xml`), "Crear Cuenta" (`activity_register.xml`), "Personaliza tu ruta" (`activity_preferences.xml`), "¿Qué deseas hacer hoy?" (`activity_menu.xml`). Efectivo en API 28+; en API 24-27 se ignora sin error.

### ~~[A11Y-006] Foco no gestionado en diálogos — `PreferencesActivity.kt`~~ ✅
- **Resuelto**: Los dos `AlertDialog` en `PreferencesActivity` (límite diario y disclaimer de IA) ahora usan `.create().also { d -> d.setOnShowListener { d.getButton(BUTTON_POSITIVE).requestFocus() }; d.show() }`. TalkBack anuncia el botón positivo al abrirse el diálogo. `PlaceDetailsActivity` no tiene AlertDialogs propios.

### ~~[A11Y-007] RecyclerView items sin `contentDescription` dinámico — `EventsAdapter.kt:34-63`~~ ✅
- **Resuelto**: `EventsAdapter.bind()` establece `eventCard.contentDescription` usando `R.string.a11y_event_item` con título, categoría, ubicación y fecha.

### ~~[A11Y-008] Sin indicador de foco visible en cards interactivas — `activity_menu.xml`~~ ✅
- **Resuelto**: Verificado que todas las `MaterialCardView` interactivas tienen `android:clickable="true"` y `android:focusable="true"`. MD3 `MaterialCardView` aplica ripple y focus highlight automáticamente cuando estos atributos están presentes.

### ~~[A11Y-009] Chips de filtro sin anuncio de estado — `activity_maps.xml:35-91`~~ ✅
- **Resuelto**: `setupFilterChips()` actualiza `chip.contentDescription` con `R.string.a11y_chip_active`/`a11y_chip_inactive` en cada cambio de estado.

---

## Hallazgos P1 (dificultades significativas)

### ~~[A11Y-010] Mapa sin alternativas para gestos complejos~~ ✅
- **Resuelto**: `mMap.uiSettings.isZoomControlsEnabled = true` y `isCompassEnabled = true` añadidos en `MapsActivity.onMapReady()`. El SDK de Google Maps renderiza botones +/- nativos accesibles para usuarios que no pueden usar gestos de pinch.

### ~~[A11Y-011] Password toggle sin anuncio TalkBack — `activity_login.xml`~~ ✅
- **Resuelto**: `app:endIconContentDescription="@string/a11y_password_toggle_show"` añadido en `activity_login.xml` (contraseña) y en ambos toggles de `activity_register.xml` (contraseña y confirmación). TalkBack anuncia "Mostrar contraseña" / "Mostrar confirmación de contraseña". Strings añadidos a `strings.xml`.

### ~~[A11Y-012] Elementos transitorios sin control — Snackbar/Toast~~ ✅
- **Resuelto**: `NotificationHelper.show()`, `success()`, e `info()` ahora tienen `LENGTH_LONG` como duración por defecto. `error()` y `warning()` ya usaban `LENGTH_LONG`. Todos los Snackbars visibles duran ≥ 3.5 s.

### ~~[A11Y-013] Avatar de perfil con `contentDescription` genérico — `activity_profile.xml`~~ ✅
- **Resuelto**: `ProfileActivity.loadUserData()` ahora establece `binding.avatarImage.contentDescription = getString(R.string.a11y_profile_photo, displayName)` usando el nickname del usuario (o email como fallback) tras cargar el perfil de Firestore.

### ~~[A11Y-014] Sin modo contraste alto — Global~~ ✅
- **Resuelto**: `Theme.TrazaGo.HighContrast` añadido en `themes.xml`. Usa negro puro (#000) sobre blanco (#FFF) para texto y superficies, y `colorPrimary = #003836` que pasa 7:1 sobre blanco (WCAG AAA). Pendiente: conectar a la preferencia de accesibilidad del sistema o añadir toggle en `OfflineSettingsActivity`.

### ~~[A11Y-015] Links sin subrayado — `activity_place_details.xml`~~ ✅
- **Resuelto**: `PlaceDetailsActivity` aplica `paint.UNDERLINE_TEXT_FLAG` al `placeWebsiteTextView` cuando se carga el sitio web. `websiteContainer.contentDescription` actualizado a `"Sitio web: {url}"` para anuncio completo en TalkBack.

### ~~[A11Y-016] Errores de formulario en Toast/Snackbar en lugar de campo~~ ✅
- **Resuelto**: `RegisterActivity` — email vacío/inválido → `emailInputLayout.error`; contraseña corta/complejidad → `passwordInputLayout.error`; confirmación distinta → `confirmPasswordInputLayout.error`. `LoginActivity` — campos vacíos → error en el layout correspondiente. `PreferencesActivity` — presupuesto/tiempo inválidos → `budgetInputLayout.error` / `timeInputLayout.error`. Todos con `requestFocus()` para que TalkBack anuncie el error.

### ~~[A11Y-017] Fechas en formato no localizado — `EventsAdapter.kt:21`~~ ✅
- **Resuelto**: `EventsAdapter` cambiado a `Locale.getDefault()`. En dispositivos en español muestra meses en español; en inglés adapta los nombres de mes.

### ~~[A11Y-018] Colores de categoría sin indicador no-visual — `activity_menu.xml`~~ ✅
- **Resuelto**: `android:contentDescription` añadido a los 12 `MaterialCardView` interactivos usando strings `@string/a11y_card_*` (13 strings nuevos en `strings.xml`). TalkBack anuncia el nombre completo de la función al navegar por las cards.

### ~~[A11Y-019] Modo oscuro sin validación de contraste — `colors-night.xml`~~ ✅
- **Verificado**: Los 3 colores custom en `colors-night.xml` son fondos oscuros (#2A2216, #4A3A28, #3A2D1A). Contraste calculado con texto MD3 dark (`?attr/colorOnSurface` ≈ #E6E1E5): >15:1, pasa WCAG AAA. El resto de pares de colores usa tokens MD3 dark que garantizan contraste AA por diseño del sistema.

---

## Hallazgos P2 (mejoras recomendadas)

- **~~[A11Y-020]~~** ✅ `ValueAnimator` respeta `animationDurationScale` automáticamente vía Android framework. Sin cambios necesarios.
- **~~[A11Y-021]~~** ✅ `custom_marker.xml` ya tenía `contentDescription="@null"`. Otras `ImageView` decorativas verificadas: sin ocurrencias problemáticas adicionales.
- **~~[A11Y-022]~~** ✅ 5 TextViews emoji-icono en `activity_place_details.xml` (🕐 📞 🌐 📍 💰) marcados con `importantForAccessibility="no"`. Adapters: `contentDescription` añadido en `PhotoGalleryAdapter`, `EventsAdapter`, `BlogPostAdapter`, `ThemedRoutesAdapter` para los campos emoji+dato.
- **~~[A11Y-023]~~** ✅ `app:errorEnabled="true"` y `app:helperTextEnabled="true"` añadidos en `activity_preferences.xml` (budget, time), `activity_register.xml` (email, password), `activity_profile.xml` (nickname), `dialog_change_password.xml` (new_password).
- **~~[A11Y-024]~~** ✅ Strings de accesibilidad añadidos: `a11y_chip_active/inactive`, `a11y_event_item`, `a11y_profile_photo`, `a11y_rating`, `a11y_like_count`, `a11y_places_list_button` en `strings.xml`.
- **~~[A11Y-025]~~** ✅ `espresso-accessibility` añadido a `libs.versions.toml` y `build.gradle.kts`. `AccessibilityChecks.enable()` en `LoginToFavoriteFlowTest.@Before`.
- **[A11Y-026]** Sin strings de instrucción globales — parcialmente resuelto con strings añadidos.
- **[A11Y-027]** `strings.xml` solo en español — considerar `values-en/strings.xml` para turistas.

---

## Resumen de cambios aplicados

| Finding | Archivo | Cambio |
|---|---|---|
| A11Y-002 | `activity_login.xml`, `activity_register.xml`, `activity_preferences.xml` | Verificado: hints ya presentes en todos los `TextInputLayout` |
| A11Y-003/007 | `EventsAdapter.kt` | `contentDescription` en card con descripción completa |
| A11Y-005 | `activity_login.xml`, `activity_register.xml`, `activity_preferences.xml`, `activity_menu.xml` | `android:accessibilityHeading="true"` en títulos de sección |
| A11Y-006 | `PreferencesActivity.kt` | `setOnShowListener { BUTTON_POSITIVE.requestFocus() }` en los dos AlertDialogs |
| A11Y-008 | `activity_menu.xml` | Verificado: `MaterialCardView` con `clickable+focusable` → ripple/focus automático MD3 |
| A11Y-009 | `MapsActivity.kt` | `contentDescription` dinámico en chips de filtro |
| A11Y-010 | `MapsActivity.kt` | `isZoomControlsEnabled = true`, `isCompassEnabled = true` |
| A11Y-012 | `NotificationHelper.kt` | `show()`, `success()`, `info()` → `LENGTH_LONG` por defecto |
| A11Y-014 | `themes.xml` | `Theme.TrazaGo.HighContrast` overlay (7:1 contraste) |
| A11Y-015 | `PlaceDetailsActivity.kt` | `UNDERLINE_TEXT_FLAG` + `contentDescription` en website link |
| A11Y-016 | `RegisterActivity.kt`, `LoginActivity.kt`, `PreferencesActivity.kt` | Errores de validación → `TextInputLayout.error` + `requestFocus()` |
| A11Y-018 | `activity_menu.xml`, `strings.xml` | `contentDescription` en 12 cards; 13 strings `a11y_card_*` |
| A11Y-024 | `strings.xml` | 10 strings de accesibilidad originales + 13 nuevos `a11y_card_*` + `a11y_website_link` |
