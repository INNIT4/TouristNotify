# Política de Privacidad — LUPITA (Tourist Notify)

**Última actualización:** 2026-04-25  
**Responsable del tratamiento:** *[Nombre legal del responsable a completar]*  
**Contacto:** *[email de contacto a completar]*

LUPITA respeta tu privacidad. Esta política explica qué datos recolectamos,
para qué los usamos, con quién los compartimos y cómo puedes ejercer tus
derechos.

---

## 1. Datos que recolectamos

| Categoría | Datos | Origen |
|---|---|---|
| **Identidad** | Email, contraseña hasheada, alias (nickname) | Tú nos los das al registrarte |
| **Perfil opcional** | Foto de perfil | Tú la subes desde Profile |
| **Ubicación** | GPS preciso cuando abres el mapa, GPS en segundo plano si activas notificaciones de proximidad | Servicios de ubicación de Android |
| **Contenido del usuario** | Reseñas, ratings, favoritos, check-ins, rutas guardadas | Tu interacción con la app |
| **Preferencias para IA** | Tiempo, presupuesto, intereses, petición libre que escribes en "Generar ruta" | Formulario de preferencias |
| **Datos técnicos** | Versión de la app, modelo del dispositivo, ID de instalación, errores (Crashlytics) | Solo si autorizas opt-in en onboarding |
| **Métricas de uso** | Vistas de pantalla, eventos (Firebase Analytics) | Solo si autorizas opt-in |

No recolectamos datos biométricos, financieros, de salud, ni de otras apps.

---

## 2. Para qué los usamos (base legal)

| Tratamiento | Base legal GDPR / LFPDPPP |
|---|---|
| Crear y mantener tu cuenta | Ejecución de contrato (Art. 6.1.b) |
| Mostrarte el catálogo de lugares y rutas | Ejecución de contrato |
| Guardar tus favoritos, check-ins, reseñas | Ejecución de contrato |
| Generar rutas con IA (envía tus preferencias a Google Gemini) | Ejecución de contrato |
| Notificaciones de proximidad (geofencing) | Consentimiento explícito (Art. 6.1.a) |
| Crashlytics + Performance Monitoring | Consentimiento explícito (revocable) |
| Firebase Analytics | Consentimiento explícito (revocable) |
| Cumplimiento de obligaciones legales (auditoría, responder a autoridades) | Obligación legal (Art. 6.1.c) |

---

## 3. Con quién los compartimos

LUPITA usa servicios de terceros que actúan como **encargados de tratamiento**:

| Proveedor | Rol | País | Garantías |
|---|---|---|---|
| **Google LLC** (Firebase Auth, Firestore, Storage, Crashlytics, Analytics, Remote Config, App Check) | Backend, auth, almacenamiento, telemetría | EE. UU. | DPA Firebase + SCC EU |
| **Google LLC** (Maps SDK, Routes API, Places API) | Mapas y navegación | EE. UU. | DPA Google Maps |
| **Google LLC** (Generative Language API / Gemini) | IA generativa para rutas | EE. UU. | DPA Generative AI |
| **OpenWeatherMap Ltd.** | Datos climáticos | Reino Unido | Términos de uso comerciales |

No vendemos tus datos a terceros con fines publicitarios.

---

## 4. Tiempo de retención

- **Cuenta y perfil**: mientras la cuenta exista. Eliminada en 30 días tras tu solicitud de borrado.
- **Check-ins, favoritos, reseñas**: mientras la cuenta esté activa. Anonimizadas tras borrado.
- **Datos técnicos / Crashlytics**: 90 días.
- **Datos compartidos con Gemini**: políticas Google Cloud — hasta 55 días en plan estándar, retención cero en plan empresarial.
- **Logs de servidor**: 30 días.

---

## 5. Tus derechos

Puedes ejercerlos en cualquier momento desde **Perfil → Privacidad** o
escribiendo a *[email de contacto]*.

| Derecho | Cómo |
|---|---|
| Acceso (qué datos tenemos) | Botón "Exportar mis datos" en Perfil |
| Rectificación | Editar nickname, foto |
| Borrado ("derecho al olvido") | Botón "Eliminar mi cuenta" |
| Portabilidad | Mismo botón "Exportar mis datos" devuelve JSON |
| Oposición / revocar consentimiento | Toggles de Crashlytics, Analytics y Notificaciones de proximidad |
| Limitar el tratamiento | Contactando al responsable |

Tienes derecho a presentar una reclamación ante la autoridad de protección
de datos competente (INAI en México, AEPD en España, etc.).

---

## 6. Menores de edad

LUPITA no está dirigida a menores de 13 años. Si descubrimos que recolectamos
datos de un menor sin consentimiento parental, los borramos inmediatamente.

---

## 7. Seguridad

- Comunicación cifrada con TLS 1.2+ (HTTPS-only).
- Tokens de sesión cifrados con Android Keystore (AES-256-GCM).
- Base local cifrada con SQLCipher.
- Reglas de Firestore con default-deny.
- Firebase App Check con Play Integrity en producción.

En caso de brecha que afecte tus datos, te notificaremos en menos de 72 horas
(GDPR Art. 33) y a la autoridad correspondiente.

---

## 8. Cambios

Si modificamos esta política te avisaremos con un banner in-app. La fecha
de última actualización está al inicio.

---

## 9. Contacto

Para cualquier duda relacionada con tus datos:

**Email:** *[a completar]*  
**Dirección postal:** *[a completar]*  
**DPO (si aplica):** *[a completar]*

---

> Esta política debe publicarse también en `https://touristnotify.app/privacidad`
> y referenciarse desde el listing de Google Play (campo "Privacy Policy URL").
