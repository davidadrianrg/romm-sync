# Revisión de diseño de Atril: coherencia visual (Android 2400×1080 + escritorio 2560×1440)

Fuente original: `/tmp/atril-screens/manifest.md` (ya borrado; ver el último seguimiento para las capturas actuales), con 18 capturas de Android y 12 de escritorio. Se abrieron todas.
Notas sobre las capturas:
- `android/23` y `android/24` son el mismo archivo (mismo md5). Muestran la Biblioteca desplazada, no el detalle de un juego, así que **la pantalla de detalle no se ha revisado**. `desktop/01` y `desktop/03` también son idénticas.
- Las capturas son de las 08:42–08:48. Después se integraron `c7fd511` y `9a512ee`. Las comprobé con las capturas más recientes de la misma carpeta (`v2-library-3do`, `w-lib-empty`): **ya están corregidos** la franja vacía sobre la barra de gestos, que Play y Biblioteca compartieran icono en el rail de Android y el estado vacío de la Biblioteca en Android (que solo era texto). No se repiten aquí.
- No hay capturas de **carga ni de error** (RomM sin conexión, fallo de sincronización, descarga fallida). Ver P1-11.
- `desktop/12` y `desktop/15` muestran rutas personales (`/home/david/...`), aunque el manifiesto dice que no las hay. Hay que revisarlo antes de publicar las capturas.

Leyenda: **P0** = parece roto o es ilegible · **P1** = rompe la coherencia o la jerarquía · **P2** = pulido.

---

## P0: parece roto

1. **Iconos que en escritorio se ven como rectángulos en blanco.** Afecta al logo del rail ("Atril"), al elemento "Biblioteca" del rail, a los encabezados de sección Launcher, Sistema, Controles y Acerca de, al botón aleatorio de la Biblioteca (cuadrado blanco en x≈2302), al marcador de carátula ausente ("Dr. Dangerous…") y a la miniatura de los cartuchos (`desktop/10`–`16`, `20`, `30`). En Android esos mismos iconos se ven bien.
   → Usar en escritorio los mismos `ImageVector` de Material que en Android (GridView, Casino, Info, SportsEsports, Settings) en lugar de los recursos que no se resuelven. Añadir un test de captura que falle si un icono se renderiza como un rectángulo de color sólido.

2. **La escala en escritorio es demasiado pequeña para 1440p.** El texto de cuerpo mide unos 12–14 px, los títulos de página unos 22 px y los chips unos 30 px de alto. Ajustes y Saves ocupan el 30 % superior y dejan el resto vacío (`desktop/10`–`16`, `30`). El control deslizante mide 2400 px de ancho (`desktop/14`), "Sincronizar ahora" 2450 px (`desktop/30`) y los interruptores quedan a unos 2400 px de su etiqueta (`desktop/11`, `14`, `15`).
   → Aplicar un factor de escala de UI en escritorio (respetar `GDK_SCALE`/DPI o un ajuste de "Tamaño de interfaz") y limitar el ancho del contenido de Ajustes y Saves a unos 1100 dp, centrado o alineado a la izquierda.

3. **El panel de información de Play en escritorio se solapa con el cartucho siguiente.** El cartucho de N64 se corta en seco en x≈1530 y el título y los botones empiezan justo encima (`desktop/01`). Además, el título del juego enfocado queda a unos 400 px de su cartucho.
   → Reservar una columna para la información a la derecha del cartucho enfocado con un hueco fijo, y atenuar o difuminar los vecinos en vez de recortarlos.

---

## P1: jerarquía y coherencia

1. **Dos modelos de navegación según la pestaña.** Play usa hamburguesa + cajón sin rail (`android/01`, `02`, `desktop/01`, `02`). Las demás pestañas usan rail sin hamburguesa (`android/10`–`31`, `desktop/10`–`30`). Al pasar de Play a Biblioteca cambia toda la estructura de la pantalla. Además, el menú de Play no coincide entre plataformas:
   - Android (`android/02`): cajón a toda altura, 4 destinos con iconos y el actual resaltado.
   - Escritorio (`desktop/02`): desplegable de unos 110 px, sin iconos, sin fondo atenuado y sin el destino actual. Tiene 5 destinos distintos ("Biblioteca, Saves, Ajustes, Plataformas, Descargas"; falta "Jugar") y tapa el chip "Game Boy Advance".
   → Elegir un único modelo. Recomendación: rail también en Play (que se pueda colapsar u ocultar en modo inmersivo), y el menú Select/F10 como el mismo cajón en ambas plataformas, con los mismos destinos y el mismo resaltado. Comprobar que el texto de ayuda de `android/16` y `desktop/16` cuadra con el resultado (se cambió en `9a512ee`).

2. **Las etiquetas y la marca difieren entre plataformas.** El rail dice "Sync" en Android y "Saves" en escritorio, y la pestaña de ajustes es "Sync & saves". La marca es el logotipo "ATRIL" en Android y un avatar redondo con "Atril" en escritorio. La versión aparece en el rail de escritorio pero no en el de Android. En el escritorio, "Atajos: Ctrl+1…5" (`desktop/16`, `messages*.properties:124-126`) promete 5 secciones, pero el rail tiene 4.
   → Una sola etiqueta para la pestaña y el mismo logotipo en ambas plataformas. Corregir el texto del atajo a 1…4, o añadir la quinta sección.

3. **No hay una cabecera de página común.**
   - Android: título en serif grande en y≈160 (Ajustes, Sync, Plataformas) pero en y≈208 en la cola de descargas (`android/31`). Play lleva el antetítulo "ATRIL" y la Biblioteca no tiene título (`android/20`).
   - Escritorio: "Ajustes" y "Saves" pequeños arriba a la izquierda, "Biblioteca" en línea con la barra de herramientas y "Jugar" con antetítulo.

   → Un componente `PageHeader(title, navIcon?, actions)` con altura y línea base fijas para todas las pantallas. En Android, en horizontal, la cabecera y las pestañas de Ajustes ocupan y=63–400 (un 31 % de la altura) y cada sección se ve cortada (`android/10`–`16`). Reducir el título a unos 32 sp o hacer que se desplace con el contenido.

4. **El estado "seleccionado" usa 3 colores.**
   - Verde: pestañas de Ajustes, chips de tema/idioma/conflictos (`desktop/11`, `13`), "Todos" en la Biblioteca de escritorio (`desktop/20`) y el rail de escritorio.
   - Ámbar: "All" en la Biblioteca de Android (`android/20`) y "Todas" en Play de escritorio (`desktop/01`).
   - Marrón: el rail y el cajón de Android.

   → Un único token de selección (p. ej. `secondaryContainer`) para chips, pestañas y rail en ambas plataformas. Reservar el ámbar relleno para la acción principal.

5. **La jerarquía de botones es incoherente.**
   - La acción principal unas veces es verde tonal ("Open Library" en `android/01`, "Select folder" en `android/12`, "Refresh" en `android/17`, "Gestionar plataformas" en `desktop/10`, "Comprobar cambios" en `desktop/30`) y otras ámbar ("Sync now" en `android/30`, "Escanear ahora" y "Guardar" en `desktop/12`, "Exportar metadata" en `desktop/15`).
   - "Disconnect RomM" (destructiva) es el botón más grande de `android/10`.
   - En `desktop/10` la acción principal es "Conectar con QR" aunque ya está "Conectado".
   - Botones a todo el ancho: `android/10`, `12`, `30` y `desktop/30`.

   → Ámbar relleno = como mucho una acción principal por tarjeta. Verde tonal = secundaria. Contorno o texto = terciaria. Destructivas en contorno con el color de error. Ancho según el contenido, salvo en móvil vertical. Cuando hay conexión, "Conectar con QR" pasa a secundaria.

6. **La barra de herramientas de la Biblioteca no está alineada y cambia entre plataformas.**
   - Android: el desplegable (h≈120) y el buscador (h≈146) tienen alturas y bordes superiores distintos (`android/20`, `22`). El botón de la cola de descargas aparece solo al elegir plataforma y desplaza el buscador. El dado es un cuadrado blanco relleno junto a iconos de línea.
   - Escritorio: el buscador va en una fila propia, las acciones tienen otro orden ("Todas las plataformas ▾", descargar, refrescar, "Subir juego", "Descargar faltantes (218)", "Orden: Nombre A-Z") y se mezclan cuatro estilos de botón.

   → La misma altura de 56 dp para el desplegable y el buscador. Acciones fijas, en el mismo orden y con las mismas etiquetas o tooltips en ambas plataformas. Un solo estilo de botón por fila. Reservar el hueco del botón de cola aunque no haya descargas.

7. **Hay tres formas de acceder a la cola de descargas.** En Play de Android es un círculo neutro con icono de descarga. En la Biblioteca de Android es un FAB verde con un ✓ y una insignia roja "8" (el ✓ sugiere "terminado" y el rojo no es de la paleta). En escritorio es una insignia "2" flotando suelta a la izquierda del "+" (`desktop/01`).
   → Un componente `DownloadQueueButton`: icono de descarga y `BadgedBox` con la insignia en ámbar encima del icono, igual en todas las pantallas.

8. **Las tarjetas de la Biblioteca tapan la carátula.** En Android el botón de descarga mide unos 88 px sobre tarjetas de unos 270 px y tapa el logo del juego (`android/22`, `v2-library-3do`). El título va sobre la ilustración sin un degradado uniforme ("Escape from Monster Man…", "Space Hulk"). En escritorio aparecen tamaños "0 KB" (Abu Simbel, Double Dragon, Dr. Dangerous) y la carátula ausente es una caja vacía (`desktop/20`).
   → Insignia de descarga de unos 32 dp en una esquina, semitransparente. Degradado inferior del transparente al 70 % de negro bajo el título y el tamaño. Ocultar los tamaños desconocidos. Para las carátulas ausentes, usar el marcador con iniciales de plataforma de `android/17`.

9. **La rejilla escalonada solo existe en Android.** `c7fd511` la mantiene a propósito, pero sigue dejando huecos grandes (bajo Gex y Road Rash en `v2-library-3do`), hace imprevisible el foco con D-pad, y el escritorio usa una rejilla uniforme.
   → Decidir un único comportamiento y aplicarlo en ambas plataformas. Mi recomendación es una rejilla uniforme 3:4 con `Fit` y franjas laterales, que favorece la navegación con mando. Si se mantiene la escalonada, llevarla también a escritorio.

10. **Las secciones de Ajustes no coinciden entre plataformas.**
    - Apariencia: en Android aparece primero Launcher ("Show physical media by default") y luego Language, sin Tema visible. En escritorio: Tema, Idioma y Launcher, con *otro* interruptor ("Obtener el medio físico automáticamente") (`android/11` frente a `desktop/11`).
    - Juegos locales: Android empieza por el directorio de ROMs y escritorio por las claves de SteamGridDB (`android/12` frente a `desktop/12`).
    - Descargas: el orden está invertido (`android/14` frente a `desktop/14`).
    - Sync: Android tiene el interruptor general, Wi-Fi y carga. Escritorio solo tiene la política de conflictos, y la sincronización automática está en la pestaña Saves (`android/13` frente a `desktop/13`, `30`).
    - Acerca de: Android empieza por Controles y escritorio por Actualizaciones.

    → Las mismas secciones con el mismo orden y nombre. Lo exclusivo de una plataforma (Wi-Fi/carga, bandeja del sistema) va al final de su pestaña. Si un ajuste existe en ambas, debe tener el mismo texto.

11. **Los estados vacíos se resuelven de cuatro formas distintas.**
    - Play: sin tarjeta, con icono, un título largo, CTA verde y enlace ámbar (`android/01`).
    - Sync: tarjeta de 365 px con el CTA fuera (`android/30`).
    - Cola de descargas: tarjeta con un icono con halo, sin CTA, que toca el rail y el borde derecho (x=210→2400) (`android/31`).
    - Biblioteca: tarjeta con icono, sin CTA (`w-lib-empty`).
    - Escritorio Saves: fila compacta (`desktop/30`).

    → Un único `EmptyState(icon, title, body, primaryAction?)` con o sin tarjeta, pero siempre igual, con el CTA dentro y el margen estándar de unos 42 px. Acortar el título de Play a algo como "Aún no hay juegos descargados". Diseñar y capturar también los estados de **carga** (skeleton de la rejilla y de los cartuchos) y de **error** (RomM inaccesible, sincronización fallida, descarga fallida con "Reintentar").

12. **La pantalla Saves de escritorio no usa tarjetas.** Solo el estado va en tarjeta. "Cambios pendientes" y "Sincronización automática" quedan sueltos, "Desactivada" repite "Off" y "Off" no está traducido (`desktop/30`).
    → Usar el mismo `SectionCard` que en Ajustes y traducir "Off" como "No".

---

## P2: pulido

1. **Tipografía de los interruptores.** El título de cada fila (p. ej. "Show physical media by default", "Wi-Fi only") va en peso normal con mucho espaciado entre letras y parece otra fuente que la del encabezado de sección (`android/11`, `13`–`15`). En escritorio son muy pequeños. → Usar `titleMedium` para el título y `bodyMedium` para la descripción en ambas plataformas.
2. **Iconos de sección genéricos en escritorio.** Servidor RomM, Plataformas, Tema, Idioma y Escanear usan el mismo icono de lista (`desktop/10`–`12`). → Usar los iconos de Android (Dns, Palette, Language, Search…).
3. **El cajón de Android es translúcido.** "ATRIL Play" se ve por detrás de "Atril" y el título se repite (antetítulo ATRIL + Atril + subtítulo) (`android/02`). → Superficie opaca, una sola marca y una capa de oscurecimiento algo más fuerte.
4. **Acción "None" en Plataformas.** Con el icono de rejilla tachada parece un estado, no una acción (`android/17`). → Botón de texto "Ocultar todas" o "Mostrar todas" según el estado.
5. **Selector de plataforma sin búsqueda.** Son 72 plataformas en un desplegable sin búsqueda que tapa los chips de filtro (`android/21`). → Diálogo o hoja con búsqueda y la selección actual marcada.
6. **Play en escritorio: chip "Medio físico".** Este chip verde entre los botones y el texto de ayuda parece un botón (`desktop/01`). → Etiqueta neutra junto a "Game Boy Advance · 1 MB", o un interruptor con su texto.
7. **Botones de Play en Android.** Los botones redondos de la cabecera miden unos 125 px y llevan relleno oscuro, más pesados que el rail. "+" y descargar no tienen etiqueta ni tooltip (`android/01`). → Botones de icono de 48 dp con `contentDescription`/tooltip, igual que las acciones de la Biblioteca.
8. **Fondo de Play.** Play tiene un degradado radial cálido y las demás pestañas negro plano, así que el salto se nota al cambiar de pestaña. → Aplicar una versión muy tenue del degradado a todas las pantallas, o a ninguna.
9. **Temas sin vista previa.** Los chips "Warm Archive / Midnight" no enseñan el color (`desktop/11`). → Añadir una muestra de color a cada chip.
10. **Enlace mal alineado en `desktop/10`.** "Avanzado: pegar un token de API" tiene una sangría distinta (x=124 frente a 112). → Alinearlo con el resto del contenido de la tarjeta.

---

## Diferencias con `docs/design-review-2026-11-05.md`
- Ya resueltos (según `v2-library-3do` y `w-lib-empty`): su #1 (franja vacía), el icono duplicado del rail de su #12 y su #17 (estado vacío de la Biblioteca).
- Su #8 pedía una rejilla uniforme, pero `c7fd511` mantuvo la escalonada. Aquí se replantea como una decisión de paridad (P1-9), no como un error.
- Nuevo en este informe: el panel de Ajustes de Android ocupa demasiada altura (P1-3), el botón principal "Conectar con QR" cuando ya hay conexión (P1-5), el desalineado de la barra de la Biblioteca (P1-6), "Ctrl+1…5" con 4 secciones (P1-2), los estados de carga y error sin diseñar (P1-11) y las rutas personales en las capturas.

## Orden sugerido
P0-1 (iconos) → P0-2 (escala y ancho máximo) → P1-1/P1-3 (navegación y cabecera comunes) → P1-4/P1-5 (tokens de selección y jerarquía de botones) → P0-3 (panel de Play) → el resto. P1-4, P1-5, P1-7 y P1-11 se resuelven sobre todo creando componentes compartidos en `core` (`PageHeader`, `EmptyState`, `DownloadQueueButton`, estilos de botón), lo que también evita que las plataformas vuelvan a divergir.

---

## Seguimiento (revisión de `b07dd12`, escala de UI en escritorio)
Comprobado en vivo en sway (`/tmp/opus-review/full.png` a 2560×1440 y `narrow2.png` a 853×1380):
- **P0-2, parte de escala: resuelto.** A pantalla completa en 1440p la UI se ve a 1,5× (chips de ~54 px, título del juego legible). En la ventana estrecha en mosaico sigue a 1×. Sigue pendiente el **ancho máximo** de Ajustes y Saves.
- Se corrigió un efecto secundario (`63ba708`). Al redimensionar la ventana por encima o por debajo del umbral se recreaba toda la UI: se perdían la búsqueda, el scroll y los diálogos abiertos, y la animación se volvía a reproducir.
- **P0-3 sigue igual a 1,5×.** El cartucho de N64 se corta en seco en x≈1530, justo donde empieza el panel de información.
- **Nuevo (P1), Play en ventana estrecha:** en `scale-1.png`, mientras carga la imagen del cartucho, el título del panel inferior se superpone a la etiqueta del marcador de posición ("Avatar: The Last Airbender…" aparece dos veces, encima). Con la imagen ya cargada (`narrow2.png`) no ocurre. → Reservar el hueco del panel inferior también en el estado de marcador de posición.
- Límite conocido de `desktopUiScale`: con escala del sistema exactamente 1,25 todavía se añade el factor (1,25 × 1,5). Es poco probable en Linux, pero conviene tenerlo en cuenta si se empaqueta para Windows.

---

## Seguimiento (revisión de `a069573` + capturas de Android del 2026-11-05 16:22)
Fuente: `~/.hermes/claude-loops/atril/screens/manifest.md` (16 capturas de Android en horizontal a 2400×1080). `/tmp/atril-screens` ya no existe y **no hay capturas de escritorio** de esta iteración (`grim` se colgaba), así que P0-1, P0-2 (ancho máximo), P0-3, P1-12 y P2-2/6/9 quedan **sin verificar**.

**Aviso:** las capturas (16:22–16:25) son **anteriores** a `1c21142` (16:29) y `a069573` (16:34). En `05` y `16` todavía se ve el botón verde con ✓ e insignia salmón "207" y las insignias de descarga opacas de unos 88 px en las tarjetas. Por eso:
- **P1-7 / P1-8 (insignias y botón batch): hecho en código, pendiente de verificación visual.** Revisando `a069573`: la pastilla ámbar crece con el texto y su ancho está limitado por el botón (46/52 dp), donde caben hasta 5 dígitos. Usa los mismos tokens que `DownloadQueueButton` y que el `Badge` de escritorio. No hay regresiones. Hay que repetir `05-library-acpc` con el APK actual.

Estado del resto según las capturas nuevas:
- **P1-4 (token de selección): hecho en parte.** En Android, el rail, los chips de la Biblioteca ("All"), las pestañas de Ajustes y los chips de idioma ya usan el mismo verde (`03`, `08`, `09`). **Pendiente:** el cajón de Play sigue resaltando "Play" en marrón/ámbar (`02`).
- **P1-1 (dos modelos de navegación): pendiente.** Play sigue con hamburguesa y cajón sin rail (`01`, `02`), y las demás pestañas con rail.
- **P1-2 (etiquetas): hecho en gran parte (ver seguimiento de `c38ab0f`).** La misma sección se llama "Saves" en el rail, "Sync" como título de página (`07`) y "Sync & saves" en la pestaña de Ajustes (`11`).
- **P1-3 (cabecera): pendiente.** En Ajustes, el título está en y≈95, las pestañas en y≈295 y el contenido empieza en y≈400. La cabecera ocupa un 37 % de la altura en horizontal y deja un hueco vacío de unos 130 px entre el título y las pestañas (`08`–`14`). **Nuevo (P2):** en la cola de descargas y en Plataformas, la flecha atrás (centro en y≈127) no está alineada con la línea del título (centro en y≈95) (`06`, `17`).
- **P1-5 (jerarquía de botones): hecho en parte.** "Disconnect RomM" ya va en contorno, pero sigue ocupando todo el ancho y sin el color de error (`08`). "Sync now" sigue siendo ámbar a todo el ancho (`07`).
- **P1-6 (barra de la Biblioteca): pendiente.** El desplegable (y 88–207) y el buscador (y 75–220) siguen teniendo alturas distintas (`03`, `05`). El dado aleatorio sigue siendo un cuadrado blanco relleno entre iconos de línea (`05`). El botón batch aparece al elegir plataforma y recorta el buscador (que pasa de terminar en x 2366 a terminar en x 2225).
- **P1-11 (estados vacíos): pendiente.** Play no usa tarjeta (`01`), la Biblioteca usa una tarjeta con márgenes en x 242–2366 (`03`) y la cola usa una tarjeta que va de borde a borde, en x 210–2400, pegada al rail y al borde derecho (`06`). En Sync, el CTA queda fuera de la tarjeta (`07`).
- **P2-1 (tipografía de los interruptores): pendiente.** Los títulos de "Show physical media by default", "Sync saves", "Wi-Fi only" y similares van en peso ligero y con más espaciado que los encabezados de tarjeta (`09`, `11`).
- **P2-3 (cajón translúcido): pendiente.** "ATRIL / Play" se sigue viendo detrás de la cabecera del cajón, y la marca sigue triplicada (ATRIL + Atril + subtítulo) (`02`).
- **P2-4 ("None" en Plataformas): pendiente** (`17`).
- **P2-5 (selector de plataformas sin búsqueda): pendiente.** Sigue siendo un desplegable simple para 72 plataformas (`04`).
- **P2-10 (sangría del enlace): pendiente, también en Android.** "Advanced: paste an API token" empieza en x≈326, mientras que el resto del contenido de la tarjeta empieza en x≈294 (`08`).
- **Nuevo (P2), hoja de detalle (`16`, revisada por primera vez):** debajo del título aparece el slug de la plataforma, "ACPC", en lugar de su nombre ("Amstrad CPC", como en el desplegable). → Mostrar el nombre de la plataforma. **Hecho** (`6b7fc09` + `feat/opus-review-4`, ver abajo).
- **Nuevo (P1, verificar), hoja de detalle en horizontal (`16`):** sin desplazarse no se ve ninguna acción principal (Descargar o Jugar). La hoja muestra la carátula, la descripción y "Media", y la acción, si existe, queda debajo. → Poner la acción principal junto al título, a la derecha de la carátula.
- Sigue sin haber capturas de los estados de carga y error, ni de los diálogos de QR, borrado y carpeta.

---

## Seguimiento (revisión de `a97ff95`, botón "Disconnect RomM" con color de error)
Diff revisado: `4741f00..e378802` (`ConfigScreen.kt` en Android y `DesktopUi.kt` en escritorio).
- **P1-5 ("Disconnect RomM" con el color de error): hecho en código, pendiente de verificación visual.** En las dos plataformas, el `OutlinedButton` usa ahora `colorScheme.error` para el texto y para el borde de 1 dp. El color sale de la paleta compartida (`p.error`) tanto en `Theme.kt` como en `DesktopTheme.kt`, así que se mantiene la paridad. El botón nunca se deshabilita, de modo que el borde fijo no deja un estado deshabilitado incoherente. No hay regresiones ni hacen falta tests (es solo estilo). El commit lo etiqueta como "P1-4", pero corresponde a **P1-5** (jerarquía de botones).
- **Sin verificar:** las capturas del manifiesto (16:22–16:25) son anteriores a `a97ff95` (16:38). `08-settings-romm` todavía muestra el botón sin el color de error, y sigue sin haber capturas de escritorio. → Repetir `08` con el APK actual y una captura de Ajustes › RomM en escritorio.
- **Pendiente (P1-5):** en Android, "Disconnect RomM" sigue ocupando todo el ancho, mientras que en escritorio va en línea junto a "Connect with QR". "Sync now" sigue siendo ámbar a todo el ancho (`07`).
- No hay cambios en la sync, en las descargas ni en el diseño en horizontal en este rango. El resto de puntos sigue como en la sección anterior.

---

## Seguimiento (revisión de `6b7fc09`, nombre de plataforma en la hoja de detalle)
Diff revisado: `f1ad6f4..21f0dd9` (solo `LibraryScreen.kt`).
- **P2 "slug en la hoja de detalle": el arreglo estaba incompleto. Corregido en `feat/opus-review-4`.** `6b7fc09` solo pasaba `platformName` desde la Biblioteca de Android. La misma `RomDetailSheet` abierta desde **Play** seguía mostrando "ACPC", porque el parámetro tiene `null` por defecto y el compilador no avisaba. El detalle de **escritorio** (`GameDetail.kt`) también seguía con `platformSlug.uppercase()`, así que se rompía la paridad. Ahora Play pasa `item.platformLabel` (nombre, o el slug si no hay plataforma) y escritorio usa `displayName ?: name` de `state.platforms`, igual que `launchFile`. Si la plataforma no está cargada, se sigue mostrando el slug.
- Sin tests nuevos: es una etiqueta de UI sin lógica que se pueda probar de forma aislada. Pasan `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug`.
- **Sin verificar visualmente:** las capturas del manifiesto (16:22–16:25) son anteriores a `6b7fc09` y siguen sin existir capturas de escritorio. → Repetir `16-library-detail`, el detalle abierto desde Play y el detalle en escritorio.
- No hay cambios en la sync, en las descargas ni en el diseño en horizontal en este rango. **Pendiente (P1, verificar):** en horizontal, la acción principal de la hoja de detalle sigue fuera de la vista inicial (`16`). El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `b0b8595`, ancho de "Sync now" / "Cancelar")
Diff revisado: `c47e19f..d7c1aa8` (`SyncScreen.kt` en Android y `SavesScreen` en `DesktopUi.kt`).
- **P1-5 (botones a todo el ancho en Sync/Saves): hecho en código, pendiente de verificación visual.** "Sync now" pasa de `fillMaxWidth()` a `widthIn(min = 240.dp)` en ambas plataformas, y el "Cancelar" de escritorio igual. El "Cancelar" de Android (dentro de `StatusCard`) ya se ajustaba al contenido, así que hay paridad. La altura (52/48 dp), el estado `enabled` y el texto de "no configurado" siguen igual. No hay regresiones de lógica ni en la sync, y no hacen falta tests (es solo layout).
- **Sin verificar:** las capturas del manifiesto (16:22–16:25) son anteriores a `b0b8595` (16:44). `07-saves` todavía muestra "Sync now" en ámbar a todo el ancho (x 252–2358). Sigue sin haber capturas de escritorio. → Repetir `07` y una captura de Saves en escritorio.
- **Nuevo (P2), alineación en Sync de Android:** el botón queda ahora alineado a la izquierda (inicio del `LazyColumn`), justo bajo una `StatusCard` con el contenido centrado (`07`). → Al verificar, decidir si centrarlo (`Modifier.align` dentro de un `Box`/`Row` a todo el ancho) o dejarlo al inicio, como en escritorio. **Pendiente.** No se cambia ahora porque no hay ninguna captura que lo confirme.
- **Pendiente (P1-5):** "Disconnect RomM" en Android sigue ocupando todo el ancho (`08`). El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `f5530b7`, token de selección en el cajón de Play)
Diff revisado: `7fbc276..4f608b1` (solo `LauncherComponents.kt`, `LauncherMenuOverlay`).
- **P1-4 (token de selección): hecho en código, pendiente de verificación visual.** El elemento seleccionado del cajón de Play pasa del ámbar al 14 % con texto e icono ámbar a `secondaryContainer` con `onSecondaryContainer`, igual que el indicador y el icono seleccionado del rail (`RomMSyncApp.kt`). Usar `onSecondaryContainer` también para el texto es lo correcto aquí, porque el texto va dentro de la pastilla, cosa que no pasa en el rail. La pareja está en la comprobación de contraste de `AppTheme` (`core`) y sale del tema, así que funciona con todas las paletas. Escritorio no tiene cajón (usa rail), de modo que no hay problema de paridad. No hay regresiones ni hacen falta tests (es solo color). Con esto P1-4 queda completo en Android.
- **Sin verificar:** `02-play-menu` (16:24) es anterior a `f5530b7` (16:47) y todavía muestra "Play" en marrón/ámbar. → Repetir `02` con el APK actual.
- No hay cambios en la sync, en las descargas ni en el diseño en horizontal en este rango. **Pendiente:** P2-3 (cajón translúcido y marca triplicada, visible en `02`), P1-1 (hamburguesa frente a rail) y el resto de puntos, como en las secciones anteriores. Sigue sin haber capturas de escritorio.

---

## Seguimiento (revisión de `a0f3d6c`, altura del desplegable de plataforma)
Diff revisado: `f25c258..d22923a` (solo `LibraryScreen.kt`, una línea).
- **P1-6 (alturas de la barra de la Biblioteca): hecho en código, pendiente de verificación visual.** El desplegable de plataforma pasa de `if (compact) 46.dp else 56.dp` a `56.dp` fijo, igual que `LibrarySearchField` (que ya era 56 dp en cualquier caso). Esto explica el desfase de `05`: en el emulador horizontal (ventana compacta) el desplegable medía unos 119 px (46 dp) y el buscador unos 145 px (56 dp). Además, 56 dp es la altura mínima de un `OutlinedTextField` de M3, así que con 46 dp el texto quedaba apretado. El commit lo etiqueta como "P1-5", pero corresponde a **P1-6**.
- Efecto secundario aceptable: en vertical compacto el header colapsable crece 10 dp. `CollapsingHeader` mide su contenido, así que el colapso sigue funcionando. No hay cambios de lógica, ni en la sync ni en las descargas, y no hacen falta tests (es solo layout).
- **Sin verificar:** `05-library-acpc` (16:22) es anterior a `a0f3d6c` (18:48). → Repetir `03` y `05` con el APK actual y comprobar en escritorio que la barra equivalente también alinea desplegable y buscador.
- **Pendiente (P1-6):** en compacto, el botón batch sigue midiendo 46 dp junto a campos de 56 dp (`05`, x 2248–2366). Siguen pendientes el dado aleatorio relleno entre iconos de línea y el recorte del buscador cuando aparece el botón batch. El resto de puntos sigue como en las secciones anteriores. Siguen sin existir capturas de escritorio (el manifiesto indica que `grim` se cuelga).

---

## Seguimiento (revisión de `13c8e50`, ancho máximo de Ajustes y Saves)
Diff revisado: `4333e2f..c103d4b` (`DesktopComponents.kt` y `DesktopUi.kt`, solo escritorio).
- **P0-2 (ancho máximo): el arreglo seguía sin tener efecto. Corregido en `feat/opus-review-6`.** El diagnóstico de `13c8e50` era correcto (`widthIn` después de `fillMaxSize` no limita nada), pero el cambio tampoco limitaba nada. Las dos pantallas se dibujan dentro del `Surface` de material3 de `DesktopAppContent`. Ese `Surface` usa `Box(propagateMinConstraints = true)`, comprobado en el bytecode de `material3-desktop-1.7.3`, así que el `Column` recibe `minWidth = ancho completo`. Con eso, `widthIn(max = 1100.dp)` se ajusta al mínimo de entrada y la página sigue ocupando todo el ancho. Ahora `MaxWidthPage` (en `DesktopComponents.kt`) envuelve Saves y Ajustes en un `Box(fillMaxSize, TopCenter)` que deja `minWidth = 0`, y el `Column` usa `widthIn(max).fillMaxWidth()`: en ventanas anchas queda en 1100 dp centrado y en ventanas estrechas ocupa todo el ancho. Subir el límite de 560 a 1100 dp es razonable porque Ajustes tiene filas con dos botones y la barra de categorías.
- Sin test nuevo: `desktop` no tiene `compose-ui-test`, y añadirlo excede el alcance. Pasan `:core:test`, `:desktop:test` y `testDebugUnitTest`.
- **Sin verificar visualmente:** desde esta sesión no hay acceso al socket de sway (`swaymsg`/`grim` fallan con "Unable to retrieve socket path"). El manifiesto tampoco tiene capturas de escritorio. → Capturar Ajustes y Saves a pantalla completa (1440p) y en la ventana en mosaico estrecha.
- Las capturas de Android del manifiesto (16:22–16:25) no cubren este cambio, que es solo de escritorio. No hay hallazgos visuales nuevos. No hay cambios en la sync, en las descargas ni en Android horizontal en este rango. El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `c38ab0f`, título de Saves)
Diff revisado: `deef521..f3e9a3b` (solo `messages*.properties`).
- **P1-2 (etiquetas): hecho en código, pendiente de verificación visual.** `android.sync.title` pasa a "Saves" en inglés y en español. Coincide con `nav.sync`/`nav.saves` del rail y con el título de escritorio (`DesktopUi.kt`, que usa `nav.saves`), así que hay paridad. La clave solo se usa en `SyncScreen.kt`. No hay tests que dependan del texto, y la clave no cambia. La captura `07` (16:22) es anterior al commit (19:14) y todavía muestra "Sync". → Repetir `07`.
- **Corregido en `feat/opus-review-8`:** el texto de ayuda del interruptor "Sync saves" (`android.config.sync_saves_hint`) seguía diciendo "hide the **Sync** tab" / "la pestaña **Sync**" (visible en `11`). Ahora dice "Saves" en los dos idiomas.
- **Pendiente (P1-2):** la pestaña de Ajustes se sigue llamando "Sync & saves" (`settings.cat.sync`, `11`). Es aceptable porque agrupa ajustes de sync y de saves, pero conviene decidirlo de forma explícita. El "Ctrl+1…5" ya está corregido a "Ctrl+1…4" en `settings.about.shortcuts`.
- No hay cambios en la sync, en las descargas ni en el diseño en horizontal en este rango. Siguen sin existir capturas de escritorio (`screens/desktop/` está vacío). El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `fd9c06e`, formato de fecha en Saves de Android)
Diff revisado: `60142ce..8f0ca20` (solo `formatTimestamp` en `SyncScreen.kt`).
- **Fechas de Saves: hecho.** Android pasa de `dd/MM/yyyy HH:mm` con `Locale.getDefault()` a `d MMM yyyy HH:mm` con `I18n.textLocale`, igual que `formatTimestamp` de escritorio (`DesktopComponents.kt`). Así, si la UI está en inglés pero el sistema en español, ya no salen meses en otro idioma. `textLocale` ya tiene test en `I18nTest`. No hay regresiones.
- **Corregido en `feat/opus-review-9` (bug de sync):** `formatServerTimestamp` (hora del servidor en el diálogo de conflictos de Android) leía el ISO de RomM, que viene en UTC, como hora local. En España la hora salía desplazada 1–2 h, justo el dato que se usa para decidir entre "Mantener local" y "Mantener servidor". Ahora se parsea con zona UTC y se muestra con el mismo formato y locale que el resto.
- **Corregido en `feat/opus-review-9` (paridad):** la lista de versiones de save en el detalle del juego de Android (`LibraryScreen.kt`) seguía usando `d MMM HH:mm` con `Locale.getDefault()`. Escritorio (`GameDetail.kt`) usa `formatTimestamp`. Ahora usa el mismo formato y el mismo locale.
- **Pendiente (P2, paridad):** en escritorio, la hora del servidor en conflictos (`DesktopUi.kt`, `serverUpdatedAt`) se muestra como ISO en bruto (`2026-08-17T14:23:00Z`). Habría que formatearla igual que en Android, idealmente con un helper compartido en `core`.
- **Sin verificar visualmente:** las capturas del manifiesto (16:22–16:25) son anteriores a `fd9c06e` (19:17). `07-saves` no tiene saves con fecha visible y `16-library-detail` no muestra versiones de save. Sigue sin haber capturas de escritorio (`screens/desktop/` vacío; según el manifiesto, `grim` se cuelga). No hay hallazgos visuales nuevos. El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `cc3dbab`, icono del dado aleatorio)
Diff revisado: `28ae0bf..1c9c6d0` (solo `LibraryScreen.kt`: `Icons.Filled.Casino` → `Icons.Outlined.Casino`).
- **P1-6 (dado relleno entre iconos de línea): hecho en código en Android, pendiente de verificación visual.** El botón aleatorio de la barra de la Biblioteca pasa a la variante de contorno, como sus vecinos `Outlined.Download` (cola) y `Outlined.Upload`. Compila (`:app:compileDebugKotlin`) y `material-icons-extended` ya incluye la variante. No cambia la lógica (`romsWithStatus.random()`), ni la sync, ni las descargas, ni el diseño en horizontal. No hacen falta tests (es solo el icono). El commit lo etiqueta como "P1-5", pero corresponde a **P1-6**.
- **Sin verificar:** `05-library-acpc` (16:22) es anterior a `cc3dbab` (19:19). → Repetir `05` con el APK actual.
- **Pendiente (P2, paridad):** en escritorio, `AppIcons.Casino` (`AppIcons.kt`) sigue siendo la variante rellena (cuadrado con los puntos recortados en par-impar). Ahora Android y escritorio usan estilos distintos para el mismo botón. Además, en las dos plataformas la fila mezcla todavía iconos rellenos (`Public`, `Sort`) con iconos de contorno. Conviene decidir un único estilo de icono para la barra y aplicarlo en los dos lados.
- No hay hallazgos visuales nuevos: las capturas del manifiesto son anteriores al cambio y `screens/desktop/` sigue vacío (según el manifiesto, `grim` se cuelga). El resto de puntos sigue como en las secciones anteriores.
