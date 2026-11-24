# Revisión de accesibilidad y contraste (Android)

Historias: TS-012 (internacionalización y accesibilidad) y US-042 (usar la aplicación con necesidades de accesibilidad).
Fecha de la revisión: 24/11/2026. Dispositivo: emulador Pixel (API 37, 1080×2400, 420 dpi), idioma español.

## Cómo se verificó

| Verificación | Cómo | Dónde queda |
|---|---|---|
| Contraste | Cálculo WCAG 2.1 de los pares de color del tema y de los tintes translúcidos | `ContrastTest` (JVM): lee el tema real y falla si un par baja del mínimo |
| Nombres, roles y estados | Árbol de semántica de Compose y árbol de accesibilidad del sistema (lo que lee TalkBack) | `ComponentAccessibilityTest` (instrumentada) y auditoría por pantalla con `uiautomator` |
| Tamaño táctil | Medición de cada elemento pulsable (mínimo 48 dp) | Auditoría por pantalla y `ComponentAccessibilityTest` |
| Escalado de texto | Fuente del sistema al 200 % y captura de las pantallas principales | `ComponentAccessibilityTest` y capturas |
| Textos traducibles | Búsqueda de literales en la interfaz y prueba de paridad de recursos | `StringResourcesTest` |

## Hallazgos y correcciones

| # | Hallazgo | Criterio WCAG | Gravedad | Corrección |
|---|---|---|---|---|
| 1 | Botón principal con texto blanco: 3.9:1 en el extremo oscuro y 2.5:1 en el claro | 1.4.3 | Bloqueante | Verde principal #3B7334 y degradado hasta #45803D (5.7:1 y 4.8:1) |
| 2 | Texto verde (enlaces, subtítulos) 3.5:1 sobre crema; iniciales de avatar 3.9:1 | 1.4.3 | Bloqueante | Mismo verde nuevo (5.0:1 sobre crema) |
| 3 | Texto atenuado 4.0:1 sobre los tintes de arena | 1.4.3 | Mayor | Gris #58625C (mínimo 4.7:1 en todas las superficies) |
| 4 | Texto blanco sobre la foto de bienvenida 3.8:1 en la zona más clara | 1.4.3 | Mayor | Velo más opaco (5.9:1) |
| 5 | Tres colores de gráficos con 1.7 a 2.8:1 frente al panel | 1.4.11 | Menor (los gráficos tienen leyenda y valores) | Nueva paleta con al menos 3:1; sano sigue verde, tratamiento marrón y observación pasa a ámbar |
| 6 | El botón flotante extendido no exponía su texto: rol de botón sin nombre | 4.1.2, 1.1.1 | Bloqueante | `AniTecExtendedFab` con el nombre como descripción; reemplaza los 9 usos |
| 7 | Los campos vacíos no viajaban en la petición y el servidor respondía en inglés «The FollowUp field is required» | 3.3.1, 3.3.3 | Bloqueante (impedía registrar una vacuna sin diagnóstico) | `encodeDefaults = true` en el JSON compartido |
| 8 | Con texto al 200 %, etiquetas partidas a media palabra en la barra inferior («Pacie-ntes») | 1.4.4 | Mayor | Etiquetas en una línea con tope de 120 %; el nombre completo sigue disponible para el lector de pantalla |
| 9 | Con texto al 200 %, botones con texto cortado o partido («Atendid-a», «Elimina-r», «Solicitar acceso» desbordado) | 1.4.4, 1.4.10 | Mayor | `ButtonRow`: los botones pasan a la línea siguiente cuando no caben |

## Pendiente o limitado

- La etiqueta «Actividades» de la barra inferior se recorta con puntos suspensivos cuando la fuente supera el 100 %; el nombre completo se lee en voz alta. Mostrarla completa exigiría una barra más ancha o un nombre más corto.
- Los mensajes de validación del servidor (cuando un campo realmente falta) siguen en inglés: el backend usa los textos por defecto de ASP.NET.
- No se recorrió TalkBack con gestos sobre cada pantalla; la comprobación de nombres, roles y estados se hizo con el árbol de accesibilidad y pruebas de Compose.
- Pantallas del ganadero y formularios de animales, finanzas y actividades: revisadas por captura al 200 % solo en el formulario sanitario y las listas; falta la auditoría automática de esas pantallas.
- Flutter no se revisó.
