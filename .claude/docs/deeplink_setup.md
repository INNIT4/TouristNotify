# App Links — Configuración de Digital Asset Links

`AndroidManifest.xml` declara `android:autoVerify="true"` para `https://touristnotify.app/place/*`. Sin un `assetlinks.json` válido publicado, la verificación falla y Android cae al diálogo de "Abrir con…", permitiendo que **otra app pueda registrarse para interceptar el deep link**.

Este documento explica cómo publicar el archivo correctamente.

---

## 1. Obtener el SHA-256 del keystore

### Release keystore
```bash
keytool -list -v -keystore release.keystore -alias <YOUR_ALIAS> | grep "SHA256:"
```
La salida es algo como:
```
SHA256: 2A:B3:C4:D5:...:EF
```

### Debug keystore (opcional, útil para testing local)
```bash
keytool -list -v \
  -keystore "$HOME/.android/debug.keystore" \
  -alias androiddebugkey \
  -storepass android -keypass android | grep "SHA256:"
```

### Si subes a Play Store (Play App Signing)
La firma "real" la genera Google después del upload. Obtén el SHA-256 desde:
**Play Console → Tu app → Setup → App integrity → App signing key certificate → SHA-256**.
Usa **ese** valor para el archivo de producción.

---

## 2. Editar `.well-known/assetlinks.json`

El template está en [`.well-known/assetlinks.json`](../../.well-known/assetlinks.json). Reemplaza:

- `REPLACE_WITH_RELEASE_KEYSTORE_SHA256_FINGERPRINT` → SHA-256 del keystore real (o Play App Signing).
- `REPLACE_WITH_DEBUG_KEYSTORE_SHA256_FINGERPRINT_OPTIONAL` → SHA-256 del keystore debug (o eliminar la línea si solo quieres release).

Ejemplo final:
```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.joseibarra.touristnotify",
    "sha256_cert_fingerprints": ["2A:B3:C4:..."]
  }
}]
```

---

## 3. Publicar el archivo

Debe estar accesible en **HTTPS** en exactamente esta URL:

```
https://touristnotify.app/.well-known/assetlinks.json
```

Requisitos:
- Status 200 OK.
- `Content-Type: application/json`.
- Sin redirecciones (`301`/`302` rompen la verificación).
- Sin Cloudflare con bot protection que devuelva 403.

---

## 4. Verificar

Desde la línea de comando:
```bash
curl -i https://touristnotify.app/.well-known/assetlinks.json
```

Con el [Statement List Generator](https://developers.google.com/digital-asset-links/tools/generator):
```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https%3A%2F%2Ftouristnotify.app&relation=delegate_permission%2Fcommon.handle_all_urls
```

Desde Android (con la app instalada):
```bash
adb shell pm get-app-links com.joseibarra.touristnotify
# Debe mostrar: Domain verification state: verified
```

---

## 5. Troubleshooting

| Síntoma | Causa probable |
|---|---|
| Diálogo "Abrir con..." aparece | `assetlinks.json` no accesible o SHA-256 incorrecto |
| `state: 1024` (legacy_failure) | Cache antiguo. `adb shell pm verify-app-links --re-verify <package>` |
| `state: 4` (none) | Otra app ganó la verificación. Hacer un test en device limpio |
| Funciona en debug pero no en release | Olvidaste agregar el SHA-256 del Play App Signing |

---

## 6. Schema custom (`touristnotify://`)

El schema `touristnotify://` **no se beneficia de App Links** — siempre puede ser interceptado por otra app. No hay solución a nivel sistema; mantenerlo solo como fallback (QR codes en sitios físicos) y validar el `placeId` con regex en `PlaceDetailsActivity.handleDeepLink()` (ya implementado vía `PLACE_ID_PATTERN`).
