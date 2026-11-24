# Pruebas automatizadas de Android

Historia: TS-014 (automatizar pruebas de los flujos móviles críticos). Última ejecución completa: 23/11/2026.

## Suites

| Suite | Dónde corre | Cantidad | Qué comprueba |
|---|---|---|---|
| Unitarias | JVM | 309 | Reglas de dominio (alcance por rol, estados, consentimiento, recordatorios, fechas, versiones y claves de idempotencia), mapeo de errores HTTP, repositorios con dobles, ViewModels y contraste de colores |
| Integración | Dispositivo | 10 | Repositorios, cola de sincronización y caché sobre Room y Retrofit reales, contra un servidor falso |
| Interfaz de punta a punta | Dispositivo | 4 | Pantallas, navegación, ViewModels, repositorios y caché reales, contra el servidor falso |
| Componentes y accesibilidad | Dispositivo | 8 | Nombre, rol y estado de los componentes base, tamaño táctil y texto al 200 % |
| Persistencia | Dispositivo | 11 | Consultas de Room y migraciones 7→8 y 8→9 con datos |
| Navegación y fotos | Dispositivo | 9 | Barra inferior y reducción de imágenes antes de subirlas |

## Correspondencia con TS-014

- **Escenario 1, pruebas de dominio:** las reglas críticas tienen casos de éxito y alternativos y no dependen del reloj ni de la red (`ConsentTest`, `RemindersTest`, `ActivityActionsTest`, `FollowUpTest`, `ConflictResolutionTest`, `OutboxTest`, `ContrastTest`).
- **Escenario 2, integración controlada:** `CriticalFlowsIntegrationTest` usa los repositorios reales sobre Room en memoria y Retrofit real, con `FakeBackend`, que sigue las reglas del API: claves de idempotencia, versiones, 412 y respuestas del consentimiento.

## Flujos cubiertos de punta a punta

| Flujo | Prueba |
|---|---|
| Un ganadero inicia sesión y llega a su inicio | `CriticalFlowsUiTest.aRancherSignsInAndLandsOnTheirHome` |
| Aceptar la solicitud de un veterinario | `...aRancherAcceptsTheRequestOfAVeterinarian...` |
| Revocar el acceso, con confirmación | `...aRancherRevokesTheAccessOfAVeterinarian_afterConfirming` |
| Editar un registro que cambió mientras tanto, ver el conflicto y conservar el propio | `...anEditOfARecordChangedMeanwhile...` |
| Crear con la respuesta perdida: un solo registro y la misma clave | `CriticalFlowsIntegrationTest.aCreateWhoseAnswerIsLost...` |
| Crear sin conexión: queda en cola, no se pierde | `...aChangeMadeWithoutConnection...` |
| Servidor caído o con error: la caché no cambia | `...aServerThatIsDown...`, `...aFailingServer...` |

## Cómo ejecutarlas

```powershell
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

La segunda necesita un emulador o dispositivo. Las pruebas de interfaz leen los textos de los recursos, así que no dependen del idioma del dispositivo, y concede ellas mismas el permiso de red local que piden las compilaciones de depuración.

## Límites conocidos

- Las pruebas de interfaz usan un servidor falso, no el backend real; la verificación contra el backend se hizo a mano en el emulador.
- No hay pruebas de interfaz de los recordatorios (notificación y permiso): se verificaron a mano y su lógica tiene pruebas unitarias.
- Las pruebas de Flutter no se tocaron.
