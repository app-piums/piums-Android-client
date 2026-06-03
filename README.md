# Piums Cliente — Android

Aplicación Android para clientes de la plataforma Piums. Permite buscar artistas, gestionar reservas, realizar pagos y comunicarse en tiempo real.

## Stack

- **Kotlin** + Jetpack Compose
- **Hilt** — inyección de dependencias
- **Retrofit** — cliente HTTP
- **Firebase** — notificaciones push (FCM)
- **Biometría** — autenticación por huella/Face ID
- **Min SDK:** 26 (Android 8.0) / **Target SDK:** 35

## Módulos principales

| Módulo | Descripción |
|---|---|
| `auth` | Login, registro, recuperación de contraseña |
| `onboarding` | Flujo de configuración inicial |
| `home` | Feed principal con artistas destacados |
| `search` | Búsqueda y filtros de artistas |
| `artist` | Perfil público del artista, servicios y reseñas |
| `booking` | Creación y seguimiento de reservas |
| `payments` | Checkout, historial y comprobantes |
| `inbox` | Mensajería en tiempo real |
| `myspace` | Espacio personal del cliente (favoritos, historial) |
| `profile` | Configuración de cuenta |
| `reviews` | Calificaciones y reseñas |
| `disputes` | Gestión de disputas |
| `coupons` | Cupones y descuentos |
| `notifications` | Centro de notificaciones |

## Configuración

1. Clonar el repositorio
2. Agregar `google-services.json` en `app/`
3. Definir `local.properties`:
   ```
   sdk.dir=/ruta/a/android/sdk
   ```
4. Abrir en Android Studio y ejecutar

## Entornos

| Entorno | BASE_URL |
|---|---|
| Debug | `https://client.piums.io/` |
| Release | `https://client.piums.io/` |

## Versión

`1.0.0` (versionCode 1)
