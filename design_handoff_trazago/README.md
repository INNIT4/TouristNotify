# Handoff: Rediseño Visual TrazaGo

## Overview
Rediseño de la app TrazaGo (turismo en Álamos, Sonora) para alinear la UI con los colores del logo: azul marino, verde, teal y rojo. Incluye 6 pantallas: Onboarding, Login, Menú principal, Mapa, Detalle de lugar y Perfil.

## About the Design Files
Los archivos en este paquete son **prototipos de diseño creados en HTML** — referencias visuales que muestran la apariencia y comportamiento deseado. **No son código de producción.** La tarea es recrear estos diseños en el proyecto Android existente (Kotlin + XML + Material Design 3) usando los patrones y librerías ya establecidos.

## Fidelity
**High-fidelity (hifi)** — Mockups con colores finales, tipografía, espaciado e interacciones. El desarrollador debe recrear la UI lo más fiel posible usando Material Design 3 en Android.

## Screens / Views

### 1. Onboarding (3 páginas con ViewPager2)
- **Propósito**: Introducir al usuario a la app
- **Layout**: Columna centrada, fondo `#FFF8F5`
- **Componentes**:
  - Círculo decorativo: 160px, fondo `accent + 8% opacity`, emoji centrado (72px)
  - Título: 28px, peso 700, color `#221A15`
  - Descripción: 16px, color `#52443C`, line-height 1.6
  - Indicadores: puntos 8px, activo 24px ancho, color accent `#2E7D32`
  - Botón "Siguiente"/"Comenzar": ancho completo, padding 16px, border-radius 16px, fondo `#2E7D32`, texto blanco 17px peso 600
  - Botón "Omitir": texto sin fondo, color `#52443C`, 15px
- **Páginas**:
  1. 🗺️ "Explora Álamos" — "Descubre lugares históricos, eventos culturales y experiencias únicas en este Pueblo Mágico"
  2. 📍 "Notificaciones Inteligentes" — "Recibe avisos automáticos cuando estés cerca de lugares turísticos interesantes"
  3. ✨ "Crea Rutas Personalizadas" — "Genera itinerarios con IA o explora rutas predeterminadas diseñadas por expertos"

### 2. Login
- **Propósito**: Autenticación o modo invitado
- **Layout**: Columna centrada, padding horizontal 28px
- **Componentes**:
  - Logo: 72px, border-radius 20%
  - Título "TrazaGo": 26px, peso 700
  - Subtítulo: 14px, color `#52443C`
  - Inputs (email, contraseña): padding 14px 16px, border-radius 12px, border 2px `#F4DED4`, fondo `#F4DED4`, 15px
  - Botón "Iniciar Sesión": igual que onboarding
  - Botón "Crear Cuenta": borde 2px accent, fondo transparente, texto accent
  - Link "Continuar como invitado": texto 14px, color `#52443C`

### 3. Menú Principal
- **Propósito**: Hub de navegación principal
- **Layout**: Columna con scroll
- **Componentes**:
  - Header: logo 44px + "Bienvenido a / TrazaGo" + avatar botón (40px circular, fondo accent 10%)
  - Widget clima: border-radius 20px, gradiente `#5CB8B2 → #2E7D32`, texto blanco. Muestra temp, ubicación, recomendación
  - Barra búsqueda: border-radius 14px, fondo `#F4DED4`, icono lupa, placeholder "Buscar lugares…"
  - Grid de menú (2 columnas, gap 12px): 10 items
    1. Generar Ruta IA — color `#2E7D32` (large)
    2. Ver Mapa — color accent (large)
    3. Rutas Temáticas — color `#5CB8B2`
    4. Mis Rutas — color `#6C5E2F`
    5. Top Lugares — color `#F59E0B`
    6. Eventos — color `#9C27B0`
    7. Favoritos — color `#E53935`
    8. Estadísticas — color `#1A73E8`
    9. Blog — color `#FF5722`
    10. Servicios — color `#006A67`
  - Cada card: padding 20px 16px, border-radius 20px, fondo blanco, sombra sutil. Icono en círculo 44px (border-radius 14px, fondo color+8%)
  - Bottom nav: 4 items (Inicio, Mapa, Favoritos, Perfil), íconos 22px, label 11px

### 4. Mapa
- **Propósito**: Explorar lugares en mapa interactivo
- **Layout**: Mapa fullscreen con overlays
- **Componentes**:
  - Barra superior: botón back (44px, border-radius 14px, fondo blanco, sombra) + input búsqueda
  - Chips de categoría: scroll horizontal, border-radius 20px, 13px, padding 8px 16px
    - Categorías: Todos, Museos (`#9C27B0`), Restaurantes (`#FF5722`), Hoteles (`#2196F3`), Iglesias (`#00BCD4`), Parques (`#4CAF50`)
    - Chip activo: fondo accent, texto blanco. Inactivo: fondo blanco
  - Pins del mapa: 36px, forma gota (border-radius 50% 50% 50% 0, rotado -45deg), color por categoría, punto blanco 12px central
  - Botón "Mi ubicación": 48px circular, fondo blanco, sombra, icono navegación accent
  - Card preview (bottom sheet): border-radius 24px arriba, sombra. Foto placeholder 80px (border-radius 16px), nombre 17px peso 600, rating con estrella, horario. Botones: "Ver Detalles" (accent) + corazón + cerrar

### 5. Detalle de Lugar
- **Propósito**: Info completa de un lugar turístico
- **Layout**: Scroll vertical
- **Componentes**:
  - Hero imagen: 260px, gradiente placeholder. Botones flotantes: back y favorito (40px circular, fondo rgba negro 30%, backdrop-blur)
  - Dots de galería: 4 puntos, activo 16px ancho
  - Badge categoría: padding 4px 10px, border-radius 8px, fondo categoría+12%, texto color categoría, 12px peso 600
  - Título: 24px, peso 700
  - Rating: 5 estrellas (llenas `#F59E0B`, vacías `#ddd`) + "4.7 (128)" 14px
  - Quick info cards (2 columnas): border-radius 14px, fondo `#F4DED4`, icono + label 12px + valor 14px peso 500
  - Botón Check-in: flex 1, padding 14px, border-radius 14px, fondo accent. Al hacer check-in: fondo verde 12%, texto `#4CAF50`, icono check
  - Botón navegación: padding 14px 18px, fondo accent 15%
  - Sección "Acerca de": título 16px peso 600, texto 14px line-height 1.7
  - Reseñas: cards con border-radius 14px, nombre 14px peso 600, estrellas, texto 13px

### 6. Perfil
- **Propósito**: Info del usuario, stats e insignias
- **Layout**: Scroll vertical
- **Componentes**:
  - Header: botón back + "Mi Perfil" 20px peso 700
  - Avatar: 80px circular, fondo accent 20%, icono usuario
  - Nombre: 20px peso 700, email 14px
  - Stats (3 columnas): border-radius 16px, fondo blanco. Número 24px peso 700 color accent, label 12px
  - Insignias: iconos en cuadros 52px, border-radius 16px
  - Lista de opciones: padding 16px, border-radius 14px, "Cerrar Sesión" en rojo `#E53935`

## Interactions & Behavior
- **Onboarding**: ViewPager2 con 3 páginas, dots indicadores, botón cambia texto en última página
- **Menú cards**: scale(0.96) al presionar, transición 150ms
- **Mapa chips**: filtrado de marcadores por categoría
- **Mapa pins**: scale(1.2) al presionar, abre bottom sheet con preview
- **Place detail**: favorito toggle (corazón relleno/vacío), check-in toggle con cambio visual
- **Navegación**: stack de historial con botón back

## Design Tokens

### Colores Principales (del logo)
| Token | Hex | Uso |
|-------|-----|-----|
| Navy Blue | `#1C2E4A` | Texto "Traza", headers |
| Green | `#2E7D32` | "Go", accent principal, CTAs |
| Teal | `#5CB8B2` | Brújula, acentos secundarios |
| Red | `#D93B3B` | Pin del mapa, alertas |

### Colores de Superficie
| Token | Hex | Uso |
|-------|-----|-----|
| Background | `#FFF8F5` | Fondo principal (light) |
| On Background | `#221A15` | Texto principal |
| Surface Variant | `#F4DED4` | Inputs, cards secundarias |
| On Surface Variant | `#52443C` | Texto secundario |
| Card | `#FFFFFF` | Fondo de cards |
| Dark Background | `#1A120E` | Fondo (dark mode) |
| Dark Surface | `#2A1F19` | Cards (dark mode) |

### Colores de Categorías (Marcadores del mapa)
| Categoría | Hex |
|-----------|-----|
| Museos | `#9C27B0` |
| Restaurantes | `#FF5722` |
| Hoteles | `#2196F3` |
| Iglesias | `#00BCD4` |
| Parques | `#4CAF50` |
| Tiendas | `#FFC107` |

### Tipografía
- Font: DM Sans (equivalente a sans-serif-medium en Android)
- Headline: 28-32px, peso 700
- Title: 20-24px, peso 700
- Body: 14-16px, peso 400
- Caption: 11-13px, peso 400-600

### Espaciado y Radios
- Border radius small: 8-12px
- Border radius medium: 14-16px
- Border radius large: 20-24px
- Padding cards: 16-20px
- Gap grid: 12px
- Padding pantalla horizontal: 20px

## Assets
- `logo.jpeg` — Logo de TrazaGo (brújula + mapa de Sonora)

## Files
- `TrazaGo Prototype.html` — Archivo principal del prototipo
- `app.jsx` — Componentes React (todas las pantallas)
- `ios-frame.jsx` — Frame de dispositivo iOS
- `tweaks-panel.jsx` — Panel de tweaks
- `logo.jpeg` — Logo
