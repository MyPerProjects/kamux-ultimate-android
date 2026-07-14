# Kamux Ultimate Android

Cliente móvil nativo para el reproductor musical Kamux Ultimate. Construido íntegramente con Kotlin y Jetpack Compose, diseñado para ofrecer una experiencia de streaming de grado comercial con reproducción en segundo plano y manejo inteligente de recursos del sistema operativo.

## Arquitectura y Tecnologías

El proyecto sigue patrones modernos de desarrollo en Android bajo la arquitectura MVVM (Model-View-ViewModel):

* **Interfaz de Usuario:** Jetpack Compose (100% declarativo).
* **Motor Multimedia:** AndroidX Media3 (ExoPlayer).
* **Servicio en Segundo Plano:** MediaSessionService (Control desde la pantalla de bloqueo y notificaciones).
* **Red y API:** Retrofit2 y OkHttp3.
* **Carga de Imágenes:** Coil (Optimizada para Jetpack Compose).
* **Gestión de Estado:** Coroutines y StateFlow.

## Características Principales

* **Reproducción en Segundo Plano:** El servicio sobrevive al cierre de la interfaz gráfica y mantiene la música activa en el sistema.
* **Gestión de Foco de Audio (Audio Focus):** El reproductor respeta las políticas nativas de Android, pausando el audio ante llamadas entrantes o bajando el volumen (Ducking) temporalmente al recibir notas de voz de otras aplicaciones.
* **Sincronización de Letras:** Lectura de metadatos en tiempo real para iluminar la línea exacta de la canción que se está reproduciendo.
* **Carga de Imágenes Optimizada:** Uso de imágenes asíncronas recortadas en hardware y gestión de caché visual con Coil.
* **Control de Caché del Reproductor (LoadControl):** Configuración personalizada del buffer de ExoPlayer para optimizar la red y reducir los micro-cortes, trabajando en sincronía con el sistema de Spooling del backend.

## Requisitos del Entorno

* Android Studio (Versión reciente compatible con Compose y Kotlin DSL).
* Java Development Kit (JDK) 11 o superior.
* SDK de Android configurado para un mínimo de API 24 (Android 7.0) y un objetivo de API 36.

## Configuración y Seguridad (Obligatorio)

Por medidas de seguridad de infraestructura, las URLs del backend no están quemadas en el código fuente ni se suben al repositorio. Debes inyectarlas localmente antes de compilar.

1. Clona este repositorio en tu máquina local.
2. Abre el proyecto en Android Studio.
3. En la raíz del proyecto (al mismo nivel que `build.gradle.kts`), busca o crea un archivo llamado `local.properties`.
4. Añade las rutas de tu backend (API y proxy de streaming) asegurando la barra final en la API:

KAMUX_API_BASE_URL="https://tu-dominio-api.com/"
KAMUX_STREAM_URL="http://tu-dominio-proxy.com:5002"

5. Sincroniza Gradle y haz un "Rebuild Project". El compilador generará la clase `BuildConfig` inyectando estas variables de forma segura.

## Compilación y Ejecución

* **Modo Depuración:** Conecta un dispositivo físico o emulador y ejecuta la aplicación directamente desde Android Studio.
* **Modo Producción:** El bloque de `release` en Gradle está configurado para empaquetar la aplicación. Genera el APK/AAB firmado desde la pestaña "Build" > "Generate Signed Bundle / APK".

## Permisos del Sistema

La aplicación declara y justifica los siguientes permisos en su `AndroidManifest.xml`:
* `INTERNET`: Para consumir el backend y descargar el flujo de audio.
* `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Obligatorios en Android 14+ para mantener el reproductor activo fuera de la app.
* `WAKE_LOCK`: Para prevenir que el CPU se duerma mientras el servicio procesa la música en segundo plano.
* `POST_NOTIFICATIONS`: Para mostrar el control de reproducción en la barra superior.