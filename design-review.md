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
- **Nuevo (P1, verificar), hoja de detalle en horizontal (`16`):** sin desplazarse no se ve ninguna acción principal (Descargar o Jugar). La hoja muestra la carátula, la descripción y "Media", y la acción, si existe, queda debajo. → Poner la acción principal junto al título, a la derecha de la carátula. **"Descargar": hecho y verificado; "Jugar": ver el seguimiento de `5d4f629`.**
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
- **Pendiente (P2, paridad):** en escritorio, `AppIcons.Casino` (`AppIcons.kt`) sigue siendo la variante rellena (cuadrado con los puntos recortados en par-impar). Ahora Android y escritorio usan estilos distintos para el mismo botón. Además, en Android la fila mezcla todavía iconos rellenos (`Filled.Public`, `Filled.Sort`) con iconos de contorno. Conviene decidir un único estilo de icono para la barra y aplicarlo en los dos lados.
- No hay hallazgos visuales nuevos: las capturas del manifiesto son anteriores al cambio y `screens/desktop/` sigue vacío (según el manifiesto, `grim` se cuelga). El resto de puntos sigue como en las secciones anteriores.

---

## Seguimiento (revisión de `6b36ee2`, error localizado al cargar toda la plataforma)
Diff revisado: `b980a13..d356a74` (una línea en `LibraryViewModel.kt`). Pasan `:core:test`, `:desktop:test` y `testDebugUnitTest`. Sin cambios de código en esta revisión.
- **Mensaje de error localizado en `loadAllPlatformRoms`: hecho, correcto, pendiente de verificación visual.** Usa `result.kind.toUserMessage()`, igual que `loadRoms`, y las cinco claves existen en inglés y en español. `fetchAllRomsForPlatform` conserva el `kind` de la página que falla. Ya no queda ningún `ApiResult.Error` de Android que muestre `message` en bruto. El commit lo etiqueta como P1-11, pero **P1-11 sigue pendiente**: este cambio solo arregla el texto de un snackbar, no los estados de error ni de carga.
- **Sin test nuevo.** Ningún test instancia `LibraryViewModel` (solo existe `LibrarySearchGuardTest`, sobre funciones sueltas). Es aceptable para esta línea, pero el flujo de abajo tampoco tiene cobertura.
- **Pendiente (P1, sin reproducir, por lectura de código): el batch puede usar ROMs de otra plataforma.** `loadAllPlatformRoms` termina sin hacer nada si ya hay una carga en curso (`LibraryViewModel.kt:382`) y no comprueba la plataforma al recibir la respuesta. Si se cambia de A a B mientras A carga, B no se pide y `_platformAllRoms` recibe las ROMs de A (`:386`). "Descargar faltantes" ofrecería entonces las ROMs de A estando en B (`LibraryScreen.kt`, `missingRoms`). Con un fallo, el error de A también aparece en B. Es anterior a este diff, así que no se corrige aquí. → Aplicar el mismo guard de generación que `loadRoms`, con test. **Hecho en `feat/opus-review-3`.**
- **Pendiente (P2, sin reproducir, por lectura de código): error duplicado con el servidor caído.** Al elegir plataforma fallan `loadRoms` y `loadAllPlatformRoms`. Ambos emiten `LibraryEvent.Error` y, desde este cambio, con el mismo texto. Los snackbars son `Long` y se muestran en serie, así que el mismo mensaje sale dos veces seguidas. → No emitir el error del batch si la carga principal ya ha informado del mismo fallo. **Hecho en `feat/opus-review-3`.**
- **Pendiente (P2): "Error del servidor (500)" para cualquier 5xx.** `android.library.error_server` fija el 500, aunque `fetchRoms` agrupa `500..599`. Un 502 o un 503 ahora se muestra como 500 también en esta ruta, cuando antes salía el código real. → Quitar el número del texto.
- **Pendiente (P2, paridad):** en escritorio, un fallo al cargar ROMs sigue mostrando `e.message` sin traducir (`DesktopAppState.kt:348`, `app.snackbar.load_roms_error`). Android y Linux tratan de forma distinta el mismo error.
- **Capturas:** revisadas las 16 del manifiesto. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio y a varios arreglos ya integrados, y ninguna muestra un estado de error, así que **este cambio queda sin verificar visualmente**. `screens/desktop/` sigue vacío. Siguen vigentes P1-1, P1-3, P1-11, P2-3, P2-4, P2-5 y P2-10. Hay que repetir `02`, `05`, `07`, `08` y `16` con el APK actual: todavía muestran el estado anterior a los arreglos de P1-4, P1-7/8, P1-5, P1-2 y el nombre de plataforma.
- **Nuevo (P2), Acerca de en Android (`14`):** la tarjeta Controles muestra "On Linux the gamepad needs read access to /dev/input (group "input")", que no aplica en Android. → Mostrar esa frase solo en escritorio.
- **Nuevo (privacidad), `08`:** la captura muestra la URL real del servidor RomM. No hay tokens visibles, pero conviene ocultarla antes de publicarla, igual que las rutas personales de `desktop/12` y `desktop/15`.

---

## Seguimiento (revisión de `5d4f629`, hoja de detalle ancha desde la Biblioteca)
Diff revisado: `3eaa5bd..cbd960e` (solo `LibraryScreen.kt`). Sin cambios de código en esta revisión. `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug` terminan bien, aunque Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar, pero las fuentes no han cambiado desde su última ejecución correcta.
- **P1 (acción principal fuera de la vista en horizontal), "Descargar": hecho y verificado en vivo.** Emulador a 2400×1080 con el APK actual, Amstrad CPC, "1943": el botón aparece junto al título, en y≈292–418, sin desplazar. Se muestra una sola vez, porque la condición `wide && !isDownloaded && !isDownloading` coincide con la rama `else` de abajo. En vertical no cambia nada. Play abre siempre con `isDownloaded = true`, así que no le afecta.
- **D-pad: sin regresión, verificado.** Desde la hoja abierta, abajo lleva a Descargar, luego a "Show more" y después a Media, y la hoja se desplaza con el foco.
- **"Jugar" en la Biblioteca en horizontal: visible por cálculo, sin verificar.** Sin el botón de descarga, "Jugar" quedaría en y≈796–932, dentro del área visible (hasta y=1017). Con un título de 3 líneas pasaría a y≈924–1060 y quedaría cortado, aunque se podría pulsar. No se ha probado con un juego descargado: habría que marcar uno y no merecía la pena cambiar el estado del emulador para esto. **Pendiente:** capturarlo con un juego descargado y un título largo.
- **Sin test nuevo:** es solo layout y `app` no tiene tests de UI de Compose. Es aceptable. No hay cambios en la sync: la sección de saves y sus condiciones siguen igual. No afecta a escritorio, que no usa `RomDetailSheet`.
- **Nuevo (P2), hoja de la Biblioteca distinta de la de Play (pendiente):** la Biblioteca recibe ahora `wide`, pero su `ModalBottomSheet` conserva el ancho máximo por defecto de 640 dp (x 360–2040 en la captura), mientras que Play usa `sheetMaxWidth = 1600.dp`. Tampoco tiene el `contentWindowInsets` de Play, así que el contenido se corta en y=1017 y deja la franja vacía sobre la barra de gestos que Play ya evita. → Usar los mismos parámetros en las dos hojas. No se cambia ahora porque es estético y la acción ya se ve.
- **Nuevo (P2), estado "Descargando" alejado de la acción (pendiente):** en horizontal, "Descargar" está arriba, pero el estado "Descargando" sigue al final de la hoja, debajo de Media y de los metadatos (`LibraryScreen.kt:1631`). Al reabrir un juego en descarga no se ve ni acción ni estado sin desplazar. "Marcar como descargado" también queda separado de "Descargar", mientras que en escritorio van juntos (`GameDetail.kt:175-183`). → Mostrar el estado en el mismo sitio que el botón.
- **Nuevo (P2), paridad con escritorio (sin verificar):** en `GameDetail.kt:151`, "Descargar" va después de la sinopsis y de los metadatos. No hay capturas de escritorio para saber si queda fuera de la vista en una ventana baja.
- **Nuevo (P2), botón de cerrar (pendiente):** en la hoja, la X está centrada en y≈211 y la primera línea del título en y≈180. Es el mismo tipo de desajuste que la flecha atrás de `06` y `17`.
- **Verificado en la misma captura en vivo:** nombre de plataforma ("Amstrad CPC") en la hoja abierta desde la Biblioteca, insignia ámbar e icono de descarga en el botón batch (P1-7) e insignias de tarjeta translúcidas de unos 33 dp (P1-8). Abierto desde Play y en escritorio, el nombre de plataforma sigue sin verificar.
- **Capturas:** revisadas las 16 del manifiesto. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio, y `screens/desktop/` sigue vacío. Por eso este cambio se ha comprobado con una captura en vivo que no se ha añadido al manifiesto. No aportan hallazgos nuevos. Siguen vigentes P1-1, P1-3, P1-11, P2-3, P2-4, P2-5 y P2-10. En `14` sigue la frase de Linux en Android y en `11` sigue "Sync tab", ya corregido en `feat/opus-review-8`, porque es anterior. → Repetir el manifiesto con el APK actual.

---

## Seguimiento (revisión de `ba6a7d8`, estado de error de la Biblioteca con "Reintentar")
Diff revisado: `bd94f0e..10f0f7c` (`EmptyState.kt`, `LibraryScreen.kt` e i18n). Reproducido en vivo en el emulador a 2400×1080, cortando la red con `svc wifi/data disable` (ya restaurada). Pasan `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug`.

**Corregido en `feat/opus-review-3`** (`LibraryViewModel.kt`, con 6 tests nuevos en `LibraryViewModelLoadErrorTest`; 4 fallan sin el arreglo):
- **Juegos de otra plataforma al fallar el cambio (P1, reproducido).** Con Amstrad CPC cargada y sin red, al elegir 3DO seguían los juegos de Amstrad bajo el título "3DO", con "Download missing (50)", y el estado de error no aparecía. Por eso solo se veía en la primera carga. `selectPlatform` no vaciaba `_roms`. Ahora sí: se ve el estado de error y, al cambiar con red, el spinner en lugar de la lista anterior.
- **El snackbar tapaba "Reintentar" (P1, reproducido).** En horizontal cubría el botón y repetía el texto de la tarjeta dos veces seguidas, una por `loadRoms` y otra por `loadAllPlatformRoms`. Con la lista vacía ya no hay snackbar, porque el estado de error lo explica. Con lista visible se avisa una sola vez, lo que cierra el duplicado anotado en la revisión de `6b36ee2`.
- **"Reintentar" dejaba el batch incompleto (P1, reproducido).** Tras reintentar con red, Amstrad CPC ofrecía "Download missing (50)" en vez de 207, porque `refresh` no recargaba la plataforma completa. Ahora sí lo hace, y eso incluye el gesto de deslizar para refrescar. Verificado: 3DO pasa a 8 y Amstrad CPC vuelve a 207.
- **Batch con ROMs de otra plataforma (P1, cubierto con test, no reproducido en vivo).** Era el pendiente de la revisión de `6b36ee2`: `loadAllPlatformRoms` ignoraba la petición nueva si había otra en curso. Ahora la nueva sustituye a la anterior y se descartan las respuestas obsoletas.

**Estado:**
- **P1-11, error de carga de la Biblioteca en Android: hecho y verificado en vivo.** Probado en inglés, en horizontal, con el texto al 100 % y al 130 %: tarjeta en y 387–1000, "Reintentar" dentro y sin recortes, también a 130 %. La reducción de `EmptyState` (icono de 96 a 72 dp) es lo que hace que quepa, así que está justificada. **Sin verificar:** vertical, español y otros errores que no sean de red (autenticación, 5xx).
- **P1-11, resto: pendiente.** Faltan los estados de carga con skeleton, la sync fallida y la descarga fallida. Siguen las diferencias entre Play, Sync y la cola (`01`, `06`, `07`), y la tarjeta de la cola sigue de borde a borde (`06`).
- **Pendiente (P1, paridad, por lectura de código):** en escritorio, un fallo de carga muestra `e.message` sin traducir en un snackbar (`DesktopAppState.kt:348`) y `LibraryEmptyState` (`DesktopUi.kt:867`) no tiene rama de error: con el servidor caído dice que no hay juegos y no ofrece "Reintentar". El commit solo cubre Android. → Llevar el mismo estado a escritorio; es trabajo nuevo, así que no se hace en esta revisión.
- **Pendiente (P1, reproducido, anterior a este diff):** el D-pad se queda atrapado en el desplegable de plataforma. Tras elegir plataforma, las flechas no mueven el foco, con y sin red (rejilla o "Reintentar"), así que por este camino no se llega a ninguno de los dos. Probado solo con `adb shell input keyevent` tras elegir la plataforma por toque; falta confirmarlo con un mando real y buscar la causa. Mi hipótesis, sin comprobar, es que el `TextField` consume las flechas.
- **Pendiente (P2, reproducido, anterior a este diff):** en Play sin juegos, con el texto al 130 % en horizontal, "Add local game" queda recortado por abajo (y≈890). No se ha mirado el código de ese estado vacío. → Comprobar por qué se recorta y hacer que quepa o se pueda desplazar.
- **Pendiente (P2, por lectura de código):** si falla una búsqueda con lista ya cargada, siguen los resultados anteriores bajo el texto nuevo; solo lo advierte el snackbar. → Decidir si se vacía la lista también en ese caso.
- **Pendiente (P2, sin test de UI):** la condición de `LibraryScreen` no se prueba de forma aislada; los tests cubren el `ViewModel`, y la pantalla se comprobó a mano.

**Capturas:** revisadas las 16 del manifiesto. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio y a varios arreglos ya integrados, y `screens/desktop/` sigue vacío. No aportan hallazgos nuevos y siguen vigentes P1-1, P1-3, P2-3, P2-4, P2-5 y P2-10. Este cambio se comprobó con capturas en vivo que no se han añadido al manifiesto. **Escritorio sin ejecutar en esta revisión:** los cambios solo afectan a `app`, y `:desktop:test` pasa. → Repetir el manifiesto con el APK actual y añadir el estado de error.

---

## Seguimiento (revisión de `6536ba1`, chips de intervalo de sincronización automática)
Diff revisado: `805d92d..55b5143` (`SyncScreen.kt`, `DesktopUi.kt` e i18n). Sin cambios de código en esta revisión: no hay bugs ni regresiones. `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug` terminan bien, aunque Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar, pero las fuentes no han cambiado desde su última ejecución correcta.

- **Etiquetas de los chips: hecho y verificado en vivo en las dos plataformas.** Android a 2400×1080, en inglés: "Off", "1 m", "5 m", "15 m". Linux a 853×1380, en español: "No", "1 m", "5 m", "15 m". No se ha pulsado ningún chip, así que los ajustes no han cambiado.
- **Tests: no hace falta añadir ninguno.** `I18nTest` ya comprueba que la clave exista en los dos idiomas, que use los mismos marcadores y que toda clave usada en las fuentes esté definida. `I18n.format` usa `toString()`, así que el número no depende del locale.
- **Sync y Android horizontal: sin impacto.** Solo cambia el texto; `options`, `selected` y `setSyncInterval` siguen igual. En horizontal la fila cabe de sobra (x 294–957 de 2400).
- **Etiqueta del commit:** dice "P1-11" (estados vacíos), pero esta paridad de chips corresponde a **P1-12**. P1-11 sigue pendiente.

**Pendiente (nuevo, anterior a este diff; no se corrige por alcance):**
- **P2, plural, por lectura de código:** con el chip "1 m", el texto de encima sería "Every 1 minutes" / "Cada 1 minutos" (`saves.auto_sync.every_minutes`, en las dos plataformas). → Añadir una clave para el singular.
- **P2, unidad:** "m" se puede leer como metros; el símbolo de minuto es "min". Ahora basta con cambiar `saves.auto_sync.chip_minutes`.
- **P2, Android vertical, sin comprobar:** la fila de chips mide unos 255 dp y no se ajusta ni se desplaza. Por cálculo cabe en 360 dp al 100 % de texto, pero queda muy justa al 130 %. → Comprobarlo en vertical.

**Capturas del manifiesto:** revisadas las 16. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio y a varios arreglos ya integrados, y `screens/desktop/` sigue vacío. No aportan hallazgos nuevos. Según esas capturas siguen vigentes P1-1, P1-3, P1-11, P2-3, P2-4, P2-5 y P2-10. `02`, `05`, `07`, `08`, `11` y `16` muestran estados ya corregidos. → Repetir el manifiesto con el APK actual.

**Capturas de escritorio en vivo (primeras desde el 2026-11-05, a 853×1380, en español; no añadidas al manifiesto).** Se revisaron Jugar, Biblioteca, Saves y Ajustes › RomM. La franja clara vertical del centro de las capturas es la barra flotante del emulador, no la app.
- **Verificado:**
  - **P0-1 (iconos), en lo visto:** el rail, los encabezados de Ajustes › RomM y el dado se dibujan bien. Faltan las demás pestañas de Ajustes.
  - **P1-5:** "Desconectar RomM" va en contorno con el color de error, y "Sincronizar ahora" ya no ocupa todo el ancho.
  - **P1-8:** en las tarjetas de la Biblioteca, la insignia es pequeña y los títulos se leen bien.
  - **P1-2:** el título "Saves" también coincide en Android (captura en vivo).
  - **Play:** llena la ventana y la información queda abajo, sin solapes. **P0-3 sigue sin verificar** en ventana ancha.
- **Sigue pendiente:**
  - **P1-5:** "Conectar con QR" sigue en ámbar aunque ya hay conexión. En Android, "Select folder" sigue a todo el ancho (en vivo).
  - **P1-7:** en escritorio, la insignia de descargas sigue suelta a la izquierda del "+".
  - **P1-12:** "Cambios pendientes" y "Sincronización automática" siguen sin tarjeta; solo se ha resuelto el "No".
  - **P2-2:** Servidor RomM y Plataformas comparten icono.
  - **P2-6:** el chip verde "Medio físico" sigue pareciendo un botón.
  - **P2-10:** "Avanzado: pegar un token de API" sigue con otra sangría (x≈126 frente a 114).
- **Sin verificar:** el ancho máximo de P0-2, porque la ventana es más estrecha que el límite. Hay que capturarlo a pantalla completa.
- **Nuevo (P2), Biblioteca en ventana estrecha:** "Descargar faltantes (318)" se parte en 3 líneas y mide unos 75 px de alto, frente a unos 40 px del resto de la fila. → Acortar la etiqueta o pasar las acciones a una segunda fila.
- **Nuevo (P2), Ajustes en ventana estrecha:** la última pestaña se corta en el borde derecho y solo se ve la "A", sin indicar que hay más. No se ha comprobado si se puede desplazar.
- **Nuevo (P2), radios distintos en Saves de escritorio:** "Sincronizar ahora" tiene esquinas de unos 12 px y "Comprobar cambios" es una pastilla, uno encima del otro.
- **Nuevo (P2, paridad), iconos del rail:** en escritorio, "Biblioteca" usa casi el mismo icono que el logo y "Jugar" un triángulo. En Android son una rejilla y un mando.
- **Sin confirmar:** `Ctrl+3` enviado con `wtype` no cambió de sección, ni con la ventana enfocada. El código de `Main.kt` parece correcto y puede ser un problema de `wtype` con Xwayland. → Probarlo con un teclado real antes de darlo por bug.

**Notas para repetir las capturas de escritorio:**
- **El bloqueo de `grim` y `swaymsg` era del entorno.** Funcionan si se exportan `SWAYSOCK`, `XDG_RUNTIME_DIR` y `WAYLAND_DISPLAY`. La nota del manifiesto ya no aplica.
- **`DISPLAY=:0 ./gradlew :desktop:run` no basta desde una sesión de agente.** Sin `SWAYSOCK`, `TilingWmWorkaround` no relanza la JVM y la UI se queda en unos 1280×832: sale recortada a la derecha, con una zona negra debajo. Con `SWAYSOCK` llena la ventana. No es un bug de la app, pero puede dar falsos positivos en las pruebas.
- **Para pulsar en la ventana:** `swaymsg "seat seat0 cursor set X Y"` y luego `cursor press button1` / `cursor release button1`.

---

## Seguimiento (revisión de `a3f61ad`, versión en el rail de Android)
Diff revisado: `55b5143..450ffe3` (solo `AppNavRail` en `RomMSyncApp.kt`). Sin cambios de código en esta revisión: el diff no tiene bugs ni regresiones. `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug` terminan bien, aunque Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar, pero las fuentes no han cambiado desde su última ejecución correcta. El APK del emulador se instaló a las 19:12:56, un segundo después de generarse el actual (19:12:55).

- **P1-2 (versión en el rail): hecho y verificado en vivo en Android.** A 2400×1080, "v0.9.2" queda al pie del rail (y 940–974), sin solapes. Con el texto al 130 % también cabe (Ajustes acaba en y=887; la versión va en y 930–974). El patrón es el mismo que el de escritorio (`DesktopUi.kt:198-204`: `Spacer(weight)`, `labelSmall`, `onSurfaceVariant`, 12 dp abajo).
- **Tests: no hace falta añadir ninguno.** Es solo layout, sin lógica. `BuildConfig` ya estaba activo y CI lee `versionName` sin sufijos, así que la etiqueta no puede crecer de forma inesperada en las builds de develop.
- **Sync y escritorio: sin impacto.** El cambio no toca ninguno de los dos. **Escritorio sin ejecutar en esta revisión;** la paridad se ha comprobado leyendo el código.
- **Pendiente (P1-2):** sigue la diferencia de marca, con el logotipo "ATRIL" en Android y el avatar "Atril" en escritorio. **Hecho en `227e931`** (ver el seguimiento siguiente).
- **Nuevo (P2, reproducido, de este diff), la versión no aparece con poca altura:** con densidad 480 (360 dp de alto, simulando un móvil de 360 dp de ancho) no hay sitio bajo Ajustes y "v0.9.2" no se dibuja. No se recorta ni descoloca nada, así que no es una regresión, pero en esos móviles el rail no coincide con el de escritorio. La versión sigue disponible en Ajustes (`android.config.installed_version`, `ConfigScreen.kt:950`), así que no se pierde el dato. → Aceptable; decidir si merece reducir el espacio del rail. **Pendiente.**

**Nuevo, anterior a este diff; no se corrige por alcance (el encargo se limita a los cambios desde `55b5143`):**
- **P1, reproducido, Android horizontal: el botón de menú de Play no responde en parte de su superficie.** `EdgeSwipeZone` (`PlayScreen.kt:462-471`) se dibuja encima de la cabecera y se traga los toques. Empieza tras el inset de gestos (x≈78 px), mide 20 dp de ancho y ocupa el 70 % central de la altura (desde y≈162 px en un alto de 1080).
  - **Densidad 420:** el botón ocupa y 121–184. Con x=95, abre el menú en y=130, 150 y 158, y no lo abre en y=166, 175 y 182. Con x=70, fuera de la zona, abre también en y=175.
  - **Densidad 480:** el centro del botón (108, 174) no abre el menú; (108, 145) y (80, 174) sí. En densidad 560 tampoco lo abrió el toque en el centro.
  - **Impacto:** cuanto mayor es la densidad, más fácil es fallar el botón, y un toque en el centro ya no funciona. Solo se ha probado con `adb shell input tap`, sin mando. El origen es `1acd0ad` o anterior; `git log` no sigue más atrás porque ahí se movió el paquete.
  - → Limitar la zona para que no cubra la cabecera, por ejemplo colocándola bajo `LauncherHeader`, y comprobarlo en estas tres densidades. **Pendiente.**
- **P1, reproducido, Android horizontal: el estado vacío de Play pierde sus acciones con poca altura.** `EmptyLauncher` (`PlayScreen.kt:722`) está centrado, sin desplazamiento, dentro de una columna limitada. Con densidad 480 (360 dp) desaparece "Add local game", y con densidad 560 (308 dp) también "Open Library": ninguno de los dos está en la captura ni en el árbol de `uiautomator`. Amplía el P2 anotado en la revisión de `ba6a7d8`, que solo lo veía con el texto al 130 %; se puede subir a P1 porque ocurre al 100 %. → Permitir el desplazamiento o compactar el contenido en horizontal. **Pendiente.**
- **P2, reproducido, causa sin confirmar: al recrearse la actividad se vuelve a Play.** Estando en la Biblioteca, cambiar la escala de texto del sistema devuelve a Play (`fontScale` y `density` no están en `configChanges`; girar la pantalla no recrea la actividad). Puede estar relacionado con el `startDestination` condicional de `AppNavHost` (`RomMSyncApp.kt:289`), pero no se ha comprobado. **Pendiente.**

**Capturas del manifiesto:** revisadas las 16. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio y a varios arreglos ya integrados, y `screens/desktop/` sigue vacío, así que no muestran la versión del rail. No aportan más hallazgos que los ya anotados. Según esas capturas siguen vigentes P1-1, P1-3, P1-11, P2-1, P2-3, P2-4, P2-5 y P2-10. `02`, `05`, `07`, `08`, `11` y `16` muestran estados ya corregidos. → Repetir el manifiesto con el APK actual. Las capturas en vivo de esta revisión eran temporales y no se han añadido al manifiesto.

**Estado del emulador:** se probaron `font_scale` 1,3 y `wm density` 480/560, y se restauraron (420 y 1,0). La app se forzó a cerrar una vez y se volvió a abrir, sin cambiar ningún ajuste de la app.

---

## Seguimiento (revisión de `227e931`, wordmark "ATRIL" en el rail de escritorio)
Diff revisado: `450ffe3..c849a43` (solo `AppNavigationRail` en `DesktopUi.kt`). Sin cambios de código en esta revisión: el diff no tiene bugs ni regresiones. `:desktop:test` se ha vuelto a ejecutar (31 tests, 0 fallos). `:core:test`, `testDebugUnitTest` y `:app:assembleDebug` terminan bien, aunque Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar.

- **P1-2 (marca en el rail): hecho y verificado en vivo en Linux.** El texto, el estilo, el color, el espaciado de letras y los márgenes son los mismos que en `AppNavRail` de Android (`RomMSyncApp.kt:197-206`). El wordmark queda a la misma altura: centro a unos 21 dp del borde superior del rail en las dos plataformas (`03` en Android; ventana de 853×1380 en escritorio). A pantalla completa (escala 1,5×) se lee bien y no se solapa con nada. Android no se ha ejecutado porque no cambia.
- **Limpieza del diff: correcta.** `AppIcons.Gamepad`, `CircleShape` y `Modifier.size` siguen en uso en otras partes del archivo, y el import de `sp` es necesario.
- **Tests: no hace falta añadir ninguno.** Es solo layout, sin lógica. "ATRIL" sin traducir es correcto porque es la marca, y Android lo hace igual.
- **Sync y Android horizontal: sin impacto.** El diff no toca ninguno de los dos.
- **P2 "Biblioteca casi igual que el logo" (seguimiento de `6536ba1`): resuelto,** porque ya no hay icono de logo. **Sigue pendiente** la otra parte: el mismo mando significa Biblioteca en escritorio y Play en Android; Play es un triángulo en escritorio y Biblioteca una rejilla en Android.

**Nuevo (P2), pendiente; no se corrige porque es estético:**
- **De este diff, "ATRIL" repetido en Play de escritorio:** el rail y la cabecera de Play muestran la misma palabra, en ámbar y a la misma altura, a unos 140 px de distancia (ventana de 853×1380). Ya se repetía la marca con el avatar, pero ahora se nota más. En Android no pasa, porque Play no tiene rail. Además, Play de escritorio tiene a la vez rail y hamburguesa. → Quitar el antetítulo de la cabecera cuando haya rail; encaja con P1-1.
- **Anterior a este diff, restos de paridad en el rail:** el fondo es `surfaceContainerLow` en escritorio y `surfaceContainer` en Android. El primer destino queda unos 17 dp más abajo en escritorio (centro del icono a 91 dp frente a unos 74 dp). 12 dp vienen del `Spacer` de `DesktopUi.kt:184`; el resto no se ha investigado.
- **Anterior a este diff, campos de texto:** tienen esquinas casi rectas en escritorio (buscador, URL del servidor) y muy redondeadas en Android (`03`, `08`).
- **Anterior a este diff, Biblioteca de escritorio mientras carga "Todas las plataformas":** el contador de "Descargar faltantes" crece (318 y 584 a los pocos segundos, 6647 tras unos 25 s) y la rejilla se reordena mientras tanto. Es posible que pulsar el botón a mitad de carga solo cubra lo cargado hasta entonces: no se ha pulsado ni se ha leído esa ruta de código. → Comprobarlo. El marcador con iniciales de plataforma de los primeros segundos parece solo la carga de la imagen: "3D Grand Prix" y "A.M.C." (58 KB) salían con él y en otra apertura ya tenían carátula.
- **Anterior a este diff, orden del selector de plataformas en escritorio:** no es alfabético según el nombre visible: tras "Android" van "Mame", "FBNeo", "Naomi" y "Naomi 2", y luego "Atari 2600". En Android (`04`) a "Android" le sigue directamente "Atari 2600". No se ha comprobado con el APK actual. Agrava P2-5.
- **Por captura, `01` (2026-11-05), sin comprobar con el APK actual:** el subtítulo del estado vacío de Play es mucho más tenue que el de la Biblioteca (`03`) y la cola (`06`).

**Capturas de escritorio en vivo (2560×1440 a pantalla completa y 853×1380, en español; temporales, no añadidas al manifiesto).** Se revisaron Jugar, Biblioteca, Saves y Ajustes › RomM.
- **P0-2 (ancho máximo): hecho y verificado.** Saves y Ajustes quedan centrados, en x≈538–2140 de 2560; en la ventana estrecha ocupan todo el ancho.
- **P0-3 (panel de Play): solape resuelto en lo visto.** El cartucho vecino se difumina antes del panel (x≈1620) en lugar de cortarse en seco. El título sigue a unos 380 px del cartucho enfocado, con el vecino en medio. Solo se ha visto con este par de cartuchos (GBA y N64).
- **P1-5 confirmado:** "Desconectar RomM" va en contorno con el color de error y "Sincronizar ahora" ya no ocupa todo el ancho.
- **Siguen pendientes, confirmados en vivo:** P1-5 ("Conectar con QR" en ámbar con la conexión activa), P1-7 (insignia suelta a la izquierda del "+"), P1-12 (Saves sin tarjetas), P2-2, P2-6, P2-10, los radios distintos de Saves y "Descargar faltantes" en 3 líneas en ventana estrecha. La etiqueta "m" de los chips y P0-1 (solo en lo visto) tampoco cambian.
- **Sin verificar:** las demás pestañas de Ajustes y `Ctrl+3` con teclado real.

**Capturas del manifiesto:** revisadas las 16. Son del 2026-11-05 (16:22–16:25), anteriores a este cambio y a varios arreglos ya integrados, y `screens/desktop/` sigue vacío, así que no muestran este diff. Salvo la nota sobre `01`, no aportan hallazgos nuevos. Según esas capturas siguen vigentes P1-1, P1-3, P1-11, P2-1, P2-3, P2-4, P2-5 y P2-10. `02`, `05`, `07`, `08`, `11` y `16` muestran estados ya corregidos. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio; la nota de que `grim` se cuelga ya no aplica.

**Estado del entorno:** la app de escritorio se abrió y se cerró dos veces; solo se navegó entre secciones, sin cambiar ajustes ni iniciar descargas ni sincronizaciones. La ventana volvió a su tamaño en mosaico. El emulador no se ha tocado.

---

## Seguimiento (revisión de `6694965`, "Reintentar" en el error de carga de Plataformas)
Diff revisado: `365db68..6329f97` (solo `PlatformsScreen.kt`). **Sin cambios de código en esta revisión:** el diff no tiene bugs ni regresiones. `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug` terminan bien; Gradle dio las tareas de test por `UP-TO-DATE`, así que no se han vuelto a ejecutar, pero sus entradas no han cambiado desde la última ejecución correcta.

- **P1-11, error de carga de Plataformas en Android: hecho y verificado en vivo.** Sonnet no lo había visto en pantalla. Probado a 2400×1080, en inglés, cortando la red con `svc wifi/data disable` (ya restaurada):
  - Sale "No connection to the server" con "Retry" a la derecha, sin recortes, con el texto al 100 % y al 130 %.
  - Sin red, "Retry" vuelve a mostrar el error. Un doble toque no da fallos ni deja el botón bloqueado.
  - Con red y texto al 130 %, "Retry" hace desaparecer el error.
  - **Sin verificar:** vertical, español y errores que no sean de red (autenticación, 5xx). Las claves de "Reintentar" y de los cinco errores existen en español.
- **Tests: no hace falta añadir ninguno para este diff.** Es layout más una llamada a `refreshPlatforms`, que ya usaba "Refresh". **Pendiente (P2):** `PlatformsViewModel` no tiene ningún test.
- **Sync: sin impacto.** El cambio no la toca.
- **Pendiente (P2, reproducido, de este diff), detalles visuales; no justifican cambiar código por ahora:**
  - La aparición del error desplaza la lista 72 px hacia abajo, y vuelve a subir al reintentar.
  - El mensaje empieza en x=24, en el borde de la tarjeta, mientras el texto de la tarjeta empieza en x=48.
  - "Retry" queda justo debajo de "Refresh" y hace lo mismo. Se mantiene por coherencia con los demás errores con "Reintentar".
- **Pendiente (P1, paridad, por lectura de código; escritorio sin ejecutar):** el commit solo cubre Android, y en escritorio Plataformas no ofrece forma de reintentar:
  - El botón de refrescar se desactiva con `enabled = connected` (`DesktopUi.kt:252`), y un fallo pone `_connected` a `false` (`DesktopAppState.kt:290`).
  - Sin plataformas en memoria, la pantalla dice "no hay servidor configurado" aunque lo haya (`DesktopUi.kt`, rama `!connected && platforms.isEmpty()`).
  - `refreshCurrentView` usa `silent = true`, así que ese fallo tampoco muestra snackbar (`DesktopAppState.kt:303`).
  - → Llevar el mismo estado a escritorio; es trabajo nuevo, así que no se hace en esta revisión.

**Nuevo, anterior a este diff; no se corrige por alcance:**
- **P2, reproducido dos veces en Android: cambiar el tamaño de texto del sistema devuelve la app a Play** desde Plataformas y se pierde la navegación. `fontScale` no está en `configChanges` (`AndroidManifest.xml:69`), así que la actividad se recrea. Falta buscar por qué no se restaura la pila de navegación, y comprobar si pasa con otras recreaciones.
- **P2, en vivo: la versión ya no aparece en el menú de Android.** Tras `92649a1` (secciones inmersivas), el cajón solo muestra marca, lema y las cuatro secciones, y Play, Ajustes y Plataformas ya no tienen rail. Hay que revisar el estado de **P1-1 y P1-2**, escritos cuando había rail.

**Cambios sin commitear en el árbol (fuera de `365db68..HEAD`; revisados, no integrados):** `SkeletonGrid.kt`, `LibraryScreen.kt` y `DesktopUi.kt`. Sonnet dice que no son suyos, y por las fechas parecen de las revisiones de Opus que acabaron en `error_max_turns`, aunque no hay prueba directa. Por lectura de código parecen correctos y compilan, pero **no se han visto en pantalla** (ni el esqueleto ni la barra de escritorio) y `SkeletonGrid` no tiene tests. Aportan:
- Mismo ratio y espaciado que la rejilla real en el esqueleto, para que no salte.
- Etiqueta de TalkBack y pulso en la capa gráfica.
- En escritorio, el botón de faltantes ya no se recorta. **Decisión pendiente:** integrarlos o descartarlos.

**Capturas del manifiesto:** revisadas las 16. Son del 2026-11-05, anteriores a este cambio y a `92649a1`: de la `03` a la `17` aún muestran el rail lateral, ya retirado en las pantallas comprobadas en vivo. `screens/desktop/` sigue vacío y ninguna muestra un estado de error. No aportan hallazgos nuevos. Según esas capturas siguen abiertos P1-3, P1-11 (la tarjeta de la cola de `06` va de borde a borde, de x 210 a 2400), P2-3, P2-4, P2-5 y P2-10; falta comprobarlos con el APK actual. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio y de los estados de error. Las capturas en vivo de esta revisión eran temporales, y el emulador las hizo con menos densidad que el manifiesto, así que las medidas en píxeles no son comparables.

**Estado del entorno:** el APK actual, que incluye los cambios sin commitear, quedó instalado en el emulador. Red y tamaño de texto restaurados. Escritorio sin ejecutar: el diff solo afecta a `app`.

---

## Seguimiento (revisión de `4df422b`, esqueleto de carga en la Biblioteca de escritorio)
Diff revisado: `ec8cb4f..0127bc7` (solo `DesktopUi.kt`). **Sin cambios de código en esta revisión:** el diff no tiene bugs ni regresiones. `:desktop:test` se ha vuelto a ejecutar (35 tests, 0 fallos). `:core:test` y `testDebugUnitTest` terminan bien, pero Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar, y este diff no toca sus fuentes.

- **P1-11, esqueleto de carga de la Biblioteca en escritorio: hecho y verificado en vivo.** Probado en español, entrando en la Biblioteca y pulsando refrescar, a 854×1380 (4 columnas) y a pantalla completa 2560×1440 (9 columnas): sustituye al spinner, la barra sigue visible y la rejilla deja paso a las tarjetas reales sin quedarse atascada. Las columnas, los huecos y los radios coinciden con la rejilla real. Las 60 tarjetas sobran en ambos tamaños (se ven unas 36 a pantalla completa). Android ya lo tenía, así que hay paridad. **Sin verificar:** el anuncio de `contentDescription` con un lector de pantalla y la carga con el servidor caído.
- **P1-11, resto: pendiente.** Faltan el esqueleto de los cartuchos de Play, la sync fallida y la descarga fallida.
- **Decisión pendiente de la revisión de `6694965` ("cambios sin commitear"): cerrada.** Se integraron en `3345c51`.
- **Tests: no hace falta añadir ninguno.** Es solo layout, y `desktop` no tiene `compose-ui-test`. `I18nTest` ya cubre la clave `library.loading`.
- **Sync y Android horizontal: sin impacto.** El diff solo toca la Biblioteca de escritorio. Android no se ha ejecutado porque no cambia.

**Pendiente (P1, reproducido, anterior a este diff; no se corrige por alcance): las tarjetas cambian de tamaño varias veces justo después del esqueleto.**
- **Medidas** (altura de la primera tarjeta en "Todas las plataformas", en la ventana de 854×1380; se repite igual al refrescar): 260 px (esqueleto) → 254 → 145 → 214 → 218, todo en unos 1,5 s. Antes de refrescar, tras varios segundos más, ya medía 231. A pantalla completa se ve lo mismo: 368 → 359 → 205 → unos 319.
- **Causa, por lectura de código** (`rememberMedianCoverRatio`, `Covers.kt:131`; no se ha comprobado con un arreglo):
  - Con `roms` vacío no hay URLs y el ratio pasa de `null` a `DEFAULT` antes de que llegue ningún juego. Por eso la rama `games.isNotEmpty() && ratio == null` (`DesktopUi.kt:766`) casi nunca se cumple y las tarjetas se pintan con 2:3, justo lo que el comentario de `Covers.kt:132` dice querer evitar.
  - Cada página nueva cambia `roms` y lanza otra medición sobre las 24 primeras tarjetas, que cambian al reordenarse. Las primeras pueden ser apaisadas (145 px).
- **Impacto:** la rejilla salta y se reordena mientras el usuario empieza a mirarla. El esqueleto no lo causa, pero lo hace más evidente, porque promete unas medidas que luego cambian.
- → Mantener el esqueleto hasta tener un ratio medido y no volver a medir hasta que acabe la carga, con el tope de 4 s que ya existe. Android ya sigue este criterio, según su código: mide una vez por plataforma y espera con `ratioPending` y un tope de 4 s (`LibraryScreen.kt:376-394`), así que también es un problema de paridad. **Pendiente.**

**Nuevo (P2), pendiente; no justifica cambiar código por ahora:**
- **Contraste del pulso:** en su punto más tenue, la tarjeta es (19, 17, 14) sobre un fondo (11, 10, 9), medido en la captura, y cuesta verla en un monitor con poco brillo. Android usa los mismos valores, así que cualquier ajuste debe hacerse en los dos lados.
- **Inset de 2 dp:** `GameCardItem` lleva `padding(2.dp)` y el esqueleto no, así que el borde de la tarjeta se mueve unos 2 dp al cargar (y=213 frente a 215). El comentario "mismas medidas" es cierto para la rejilla, no para la tarjeta.
- **Barra que cambia al cargar:** aparecen el dado, el globo y "Faltantes", y esto último recorta "Todas las plataformas" a "Todas las plataform…" en la ventana estrecha. Ya no hay salto vertical, pero el selector cambia de ancho.
- **Cantidad fija de 60 tarjetas:** por cálculo, una ventana muy ancha y baja (3840×1080, unas 22 columnas × 4 filas) mostraría filas incompletas durante la carga. No se ha probado y es un caso raro.

**Capturas del manifiesto:** revisadas las 16 (Android, 2400×1080, 2026-11-05). Son anteriores a este cambio y a varios arreglos ya integrados, y `screens/desktop/` sigue vacío, así que **no muestran este diff**. No aportan hallazgos nuevos. Según esas capturas siguen abiertos P1-3, P2-3, P2-4, P2-5 y P2-10, y la tarjeta de la cola de borde a borde de P1-11 (`06`); falta comprobarlos con el APK actual. `02`, `05`, `07`, `08`, `11` y `16` muestran estados ya corregidos, y de la `03` a la `17` aún se ve el rail retirado en `92649a1`. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio, incluido este esqueleto. Las capturas en vivo de esta revisión eran temporales y no se han añadido al manifiesto.

**Estado del entorno:** la app de escritorio se abrió una vez y se cerró matando sus dos JVM, por lo que `:desktop:run` terminó con `BUILD FAILED`; no es un fallo de la app. Solo se navegó a la Biblioteca y se pulsó refrescar, sin cambiar ajustes ni iniciar descargas ni sincronizaciones. La ventana salió de pantalla completa antes de cerrarse. El emulador no se ha tocado.

---

## Seguimiento (revisión de `20e9f89`, "Reintentar" en la tarjeta de fallos de Saves en escritorio)
Diff revisado: `0127bc7..00abb6b` (solo `SavesScreen` en `DesktopUi.kt`). **Corregido en `feat/opus-review-2`** (`DesktopAppState.kt`, `DesktopUi.kt`, con 2 tests nuevos en `SyncFailureStateTest`; los 2 fallan sin el arreglo). Pasan `:core:test` (236), `:desktop:test` (37), `testDebugUnitTest` y `:app:assembleDebug`. Gradle dio `:core:test`, `testDebugUnitTest` y `assembleDebug` por `UP-TO-DATE`: este arreglo no toca sus fuentes.

- **Bug (P1, de este diff), "Reintentar" no aparecía cuando falla toda la sync: corregido.** El botón solo estaba en la tarjeta de fallos por archivo (`failed.isNotEmpty()`). Si falla la sync entera (servidor caído, registro del dispositivo, negociación), `failedDetails` viene vacío y no había ni tarjeta ni botón. Además, `SyncStatusCard` lo pintaba con el check verde de "Última sincronización", y si saltaba una excepción el error no se mostraba en ningún sitio. El comentario "como en Android" era inexacto: Android ofrece "Reintentar" justo en el fallo total (`SyncState.Failed`), y un fallo parcial cuenta allí como éxito con avisos. Ahora `syncError` lleva a `SyncStatusCard` un estado de error con "Reintentar", igual que `StatusCard` en Android. La excepción también limpia la lista de fallos anterior.
- **P1-11, sync fallida: hecho y verificado en vivo en Linux** (854×1380, en español, con un HOME temporal). Sin servidor configurado, "Sincronizar ahora" muestra "Error / Servidor no configurado / Reintentar", y "Reintentar" repite el ciclo sin bloquear nada. **Sin verificar:**
  - en pantalla, un servidor caído real (cubierto por test, que comprueba el error del registro del dispositivo con un puerto sin servicio);
  - la rama de excepción (sin test: no se ha encontrado cómo provocarla sin cambiar código);
  - la tarjeta de fallos por archivo, que necesita un save real y un fallo parcial.
- **Android: no se ha ejecutado,** porque el diff y el arreglo solo tocan escritorio. La sync y Android horizontal no cambian.
- **P1-11, resto: pendiente.** Faltan el esqueleto de los cartuchos de Play y la descarga fallida, y siguen las diferencias entre estados vacíos.

**Pendiente (no se corrige por alcance):**
- **P1, reproducido en Linux, por lectura de código en Android: tras reiniciar, una sync fallida parece correcta.** El error se guarda como última sincronización. Al reabrir la app sale el check verde con "Última sincronización · Resultado: Servidor no configurado", sin "Reintentar", y se pierde la fecha de la última sync buena. `syncError` solo vive en memoria. Android guarda igual el fallo (`setLastSync`, `SyncScreen.kt:148`) y en `Idle` pinta `CheckCircle`. → Guardar si el último ciclo falló, en los dos lados.
- **P2, por lectura de código, cancelar y reintentar enseguida:** `cancelSync` pone `_syncing` a `false` antes de que termine el trabajo anterior. Si se relanza entonces, el `finally` del trabajo cancelado pone `_syncing = false` y `syncJob = null` sobre el nuevo: el botón se reactiva con una sync en marcha y esa sync ya no se puede cancelar. Es anterior a este diff ("Sincronizar ahora" ya lo permitía). → Comprobar en el `finally` que el trabajo sigue siendo el actual.
- **P2, por lectura de código, aviso de fallo total:** escritorio solo notifica conflictos y fallos por archivo, así que una sync automática que falla entera no avisa si la ventana no está en Saves.
- **P2, paridad, fallo parcial:** escritorio tiene ahora "Reintentar" en la tarjeta de fallos y Android no (`FailedOpsSection`), aunque su texto dice "Try again". → Decidir uno de los dos.

**Nuevo (P2, en vivo en Linux), pendiente; no justifica cambiar código por ahora:**
- **Error repetido con dos estilos:** sin servidor, "Servidor no configurado" sale en la tarjeta (`bodySmall`, neutro) y otra vez bajo "Cambios pendientes" (texto grande en rojo).
- **Dos botones para lo mismo:** "Reintentar" queda justo encima de "Sincronizar ahora". Se mantiene por coherencia con Android.
- **"Reintentar" sin salida:** sin servidor configurado no puede funcionar. Sería más útil llevar a Ajustes › RomM.
- **Saves sin tarjetas (P1-12):** sigue igual, también con el nuevo estado de error.

**Capturas del manifiesto:** revisadas las 16 (Android, 2400×1080, 2026-11-05). Son anteriores a este cambio y solo muestran Android, y `screens/desktop/` sigue vacío, así que **no muestran este diff**. No aportan hallazgos nuevos. Según esas capturas siguen abiertos P1-3, P2-1, P2-3, P2-4, P2-5 y P2-10, y la tarjeta de la cola de borde a borde de P1-11 (`06`); falta comprobarlos con el APK actual. `02`, `05`, `07`, `08`, `11` y `16` muestran estados ya corregidos, y de la `03` a la `17` aún se ve el rail retirado en `92649a1`. `08` sigue mostrando la URL real del servidor. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio, incluido este estado de error. Las capturas en vivo de esta revisión eran temporales y no se han añadido al manifiesto.

**Estado del entorno:** la app de escritorio se abrió dos veces con un HOME y unas `Preferences` temporales, ya borrados, así que no se tocó la configuración real ni el servidor. Se cerró matando sus JVM, por lo que `:desktop:run` terminó con `BUILD FAILED`; no es un fallo de la app. El cursor de sway se movió para pulsar en la ventana. El emulador no se ha tocado.

---

## Seguimiento (revisión de `08de4ce`, "Conectar con QR" secundaria con credenciales)
Diff revisado: `852a419..51c0efd` (solo `SettingsScreen` en `DesktopUi.kt`). **Sin cambios de código en esta revisión:** el diff no tiene bugs ni regresiones.

- **P1-5, "Conectar con QR" en escritorio: hecho y verificado en vivo en Linux** (854×1380, en español, con un HOME y unas `Preferences` temporales y un servidor falso local). El commit lo etiqueta como P1-4, pero corresponde a **P1-5**. Sustituye a lo anotado como pendiente en las revisiones de `6536ba1` y `227e931`.

  | Estado | "Conectar con QR" | Otros |
  |---|---|---|
  | Sin URL ni token | Deshabilitado | |
  | URL sin token | Ámbar | |
  | Token, servidor caído | Verde tonal | "Desconectar RomM"; sin "Conectado" |
  | Token, servidor activo | Verde tonal | "Desconectar RomM" y "Conectado" |
  | Tras pulsar "Desconectar RomM" | Vuelve a ámbar | Se conserva la URL |

- **Sin verificar en vivo:**
  - Pasar a tonal tras un login por QR real. Por lectura de código es correcto: `runQrLogin` guarda el token antes de emitir `Success`, y el `LaunchedEffect(qrState)` refresca `hasCredentials`.
  - Pasar a tonal tras pegar un token. Por lectura de código, el formulario también refresca `hasCredentials`.
- **Tests: no hace falta añadir ninguno.** Solo cambia el estilo del botón, y `desktop` no tiene `compose-ui-test`. `:core:test` (236), `:desktop:test` (37) y `testDebugUnitTest` terminan bien, aunque Gradle los da por `UP-TO-DATE`: no se han vuelto a ejecutar, pero las fuentes no han cambiado desde su última ejecución correcta.
- **Sync y Android horizontal: sin impacto.** El diff solo toca Ajustes de escritorio. Android no se ha ejecutado y el emulador no se ha tocado.

**Nuevo (P2), pendiente; no justifica cambiar código por ahora:**
- **Credenciales frente a conexión.** La condición es `hasCredentials`, aunque el comentario dice "Ya conectado". Con el servidor caído, la tarjeta no tiene acción principal y no dice que no hay conexión; solo falta "Conectado". No se ha probado con un token caducado, que es el caso en el que el QR sería la salida. → Decidir si el QR vuelve a ámbar cuando hay credenciales sin conexión, y mostrar un estado "Sin conexión".
- **Paridad con Android, QR con credenciales.** Android oculta el QR si ya está configurado (`ConfigScreen.kt`, rama `!settings.isConfigured`), y escritorio lo mantiene. → Decidir uno de los dos.
- **Paridad con Android, URL al desconectar.** Android vacía la URL (`serverUrl = ""`), y escritorio la conserva (verificado en vivo). → Decidir uno de los dos.
- **Texto fuera de lugar en escritorio (en vivo).** Sin credenciales se muestra `romm.local_only_hint`: "No hace falta servidor. Puedes conectar RomM más tarde desde Ajustes". Ya se está en Ajustes, y escritorio no tiene el botón `romm.local_only` que ese texto acompaña en Android. → Usar otro texto u ofrecer el botón.
- **Última pestaña de Ajustes cortada (en vivo, a 854 px).** "Acerca de" se ve como "A", sin indicar que hay más. No se ha comprobado si la fila se desplaza. → Comprobarlo y, si hace falta, añadir un indicador o repartir las pestañas en dos filas.
- **Dos acciones ámbar (por lectura de código, no abierto en vivo).** Sin credenciales y con el formulario de token abierto, el QR y el botón del formulario son ambos `Button`. → Dejar una sola acción principal.

**Estado de otros puntos:**
- **P1-5, "Disconnect RomM" en Android: el texto de las revisiones anteriores está desactualizado.** El código ya usa `widthIn(min = 240.dp)`, no todo el ancho. Falta verificarlo visualmente, porque `08` es anterior.
- **P1-5, resto: pendiente.** Según `10`, "Select folder" sigue a todo el ancho; falta comprobarlo con el APK actual.

**Capturas del manifiesto:** revisadas las 16 (Android, 2400×1080, 2026-11-05). Son anteriores a este cambio y solo muestran Android, y `screens/desktop/` sigue vacío, así que **no muestran este diff**. No aportan hallazgos nuevos. Según esas capturas siguen abiertos P1-3, P2-1, P2-3, P2-4, P2-5 y P2-10, y la tarjeta de la cola de borde a borde de P1-11 (`06`); falta comprobarlos con el APK actual. `08` sigue mostrando la URL real del servidor. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio. Las capturas en vivo de esta revisión eran temporales y no se han añadido al manifiesto.

**El manifiesto ya no es exacto sobre `grim`:** dice que se cuelga, pero en esta revisión ha funcionado en seis capturas con `WAYLAND_DISPLAY=wayland-1` y `XDG_RUNTIME_DIR=/run/user/1000`. `swaymsg` funciona pasándole el socket con `-s /run/user/1000/sway-ipc.*.sock`. Así que las capturas de escritorio se pueden hacer.

**Estado del entorno:** la app de escritorio se abrió cuatro veces, siempre con un HOME y unas `Preferences` temporales, ya borrados, así que no se tocó la configuración real ni el servidor RomM. Se cerró matando su JVM y también se cerró el servidor falso. El cursor de sway se movió para pulsar en la ventana.

---

## Seguimiento (revisión de `bdd664d`, rejilla uniforme 3:4 en la Biblioteca de Android, P1-9)
Diff revisado: `1f80346..2676679` (solo `LibraryScreen.kt`). **Corregido en `feat/opus-review-4`** (`LibraryScreen.kt` y `LibraryViewModel.kt`). Pasan `:core:test`, `:desktop:test`, `testDebugUnitTest` y `:app:assembleDebug`; Gradle dio `:core:test` y `:desktop:test` por `UP-TO-DATE`, porque el arreglo no toca sus fuentes.

**Corregido:**
- **Filas desordenadas (P1, de este diff, reproducido).** El commit fijaba la proporción, pero seguía usando `LazyVerticalStaggeredGrid`. A 2400×1080 las columnas miden 260 o 259 px por redondeo, y sus tarjetas 347 o 345 px de alto. La escalonada coloca cada tarjeta en la columna más corta, así que la segunda fila de Amstrad CPC se pintaba como "Archon, Arkanoid, … Auf Wiedersehen, After Burner, After the War, Angel Nieto": las tres últimas columnas reciben las tarjetas que van primero. Además, las columnas quedaban desalineadas 2 px (y=785 frente a y=783). Ahora es un `LazyVerticalGrid`, igual que `SkeletonGrid` y que escritorio. Verificado en DOS: orden correcto y todas las filas en y=785.
- **Espera inútil de hasta 4 s (P1, de este diff, reproducido).** `ratioPending` seguía reteniendo la rejilla hasta medir el ratio de la plataforma, y ese ratio ya no se usa para pintar. Además se descargaban hasta 12 carátulas para medirlo. Se han quitado la espera, la medición y su código sin uso. En la primera visita, el esqueleto seguía visible en la última muestra, a los 2,4 s (Commodore C64, con el código anterior); con el arreglo, las tarjetas aparecen a los 0,96 s (DOS). Es una sola muestra por caso y en plataformas distintas, así que es orientativo.
- **Comentarios falsos.** Dos seguían describiendo la mampostería y otro hablaba solo de franjas laterales, cuando las carátulas anchas las llevan arriba y abajo.

**Verificado en vivo con el arreglo (Android):** a 2400×1080, DOS y MSX. El scroll infinito llega al final de MSX (209 juegos, de "Undeadline" a "Zombie Incident") y "Back to top" funciona. En vertical (1080×2400, restaurado después), 2 columnas con filas alineadas. **P1-9 en Android: hecho.**

**Tests:** ninguno nuevo. Es un cambio de layout y `app` no tiene tests de UI de Compose. Sin cambios en la sync ni en las descargas.

**Pendiente:**
- **P1-9, paridad con escritorio (por lectura de código; escritorio no ejecutado).** Android usa ahora 3:4 con `Fit`. Escritorio usa la mediana de la vista con `Crop` (`DesktopUi.kt:801`, `Covers.kt:41`), así que recorta lo que Android muestra entero y las tarjetas tienen otra proporción. P1-9 pedía un único comportamiento. → Decidirlo y aplicarlo en los dos lados.
- **P1, D-pad en la rejilla (reproducido con `adb shell input keyevent`, sin mando real).** El comentario del commit decía que la rejilla uniforme era mejor para D-pad, pero bajar a una fila fuera de la vista no conserva la columna. Con el arreglo, Abajo desde la columna 3 va a la columna 1 ("Another World" → "Army Moves"). En `develop` ya fallaba, y salta a columnas más alejadas (de la 3 a la 6). El arreglo no lo empeora, pero tampoco lo resuelve.
- **P1, D-pad atrapado en el desplegable: sigue igual.** Reproducido de nuevo en Amiga: tras elegir plataforma, las flechas no salen del desplegable. Para llegar a la rejilla hizo falta usar Tab.
- **P2, código sin uso.** `CoverAspectRatioCalculator` con su test, `updatePlatformMeasuredAspectRatio` y la columna `measuredAspectRatio` ya no se usan. Se han dejado porque harían falta si la paridad se resuelve llevando Android a la mediana. → Borrarlos cuando se decida. La columna requiere migración de Room.
- **P2, carátulas anchas (en vivo).** Dejan una franja oscura arriba y abajo, y la insignia de descarga queda medio sobre la franja y medio sobre la imagen ("Archon", "Arkanoid"). Es el efecto esperado de `Fit`; no justifica cambiar código.

**Capturas del manifiesto:** revisadas las 16 (Android, 2400×1080, 2026-11-05). Son anteriores a este cambio y `screens/desktop/` sigue vacío, así que **no muestran este diff** ni aportan hallazgos nuevos. Según esas capturas siguen abiertos P1-3, P2-1, P2-3, P2-4, P2-5 y P2-10, y la tarjeta de la cola de borde a borde de P1-11 (`06`); falta comprobarlos con el APK actual. `05` aún muestra la mampostería con huecos. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio. Las capturas en vivo de esta revisión eran temporales y no se han añadido al manifiesto.

**Verificado de paso en vivo:** dado de contorno (P1-6), pastilla ámbar con icono de descarga en el botón batch (P1-7) e insignias de tarjeta translúcidas (P1-8).

**Estado del entorno:**
- El emulador se reinició solo a mitad de la revisión (hacia las 05:32). No se ha averiguado la causa. Esa pasada se descartó y se repitió.
- La pantalla se puso en 1080×2400 para probar el modo vertical y se restauró a 2400×1080.
- El emulador tiene instalado el APK de `feat/opus-review-4`, y se ha visitado DOS, MSX, Amiga, Atari 2600 y Commodore C64.
- Escritorio no se ha ejecutado, porque el diff y el arreglo solo tocan `app`.
- `feat/opus-review-4` ya existía en local, ya integrada y sin commits propios (`365db68`): se borró con `git branch -d` para recrearla. `feat/opus-review-1` sigue ahí en el mismo estado (`55b5143`), sin tocar.

---

## Seguimiento (revisión de `bc196a8`, orden de las secciones de Ajustes en escritorio, P1-10)
Diff revisado: `881f82b..e3aa2b2` (solo `SettingsScreen` en `DesktopUi.kt`). **Sin cambios de código en esta revisión:** el diff no tiene bugs ni regresiones. Comparando las líneas sin sangría, solo se mueven bloques: desaparecen dos `if (settingsCat == …)` con sus llaves, al unir bloques contiguos de la misma categoría, y sobra una línea en blanco.

**P1-10, orden de las secciones: hecho y verificado en vivo en Linux** (854×1380, en español, con un `user.home` y unas `Preferences` temporales, sin servidor). El orden se comparó con el código de `ConfigScreen.kt`:

| Categoría | Android | Escritorio |
|---|---|---|
| Apariencia | Launcher, Idioma, Tema | Igual |
| Juegos locales | Directorio de ROMs, Escanear, Carátulas | Igual |
| Descargas | Media de juegos, Descargas simultáneas | Igual |
| Acerca de | Controles, Actualizaciones | Igual, más "Acerca de" al final (solo escritorio) |
| Avanzado | ES-DE | Igual, más "Sistema" al final (solo escritorio) |

**Tests:** ninguno nuevo; solo cambia el orden y `desktop` no tiene `compose-ui-test`. Nada impide que el orden vuelva a divergir, porque cada plataforma lo define en su propio archivo. `:desktop:test` se ha vuelto a ejecutar con `--rerun`: 37 tests, sin fallos. `:core:test` y `testDebugUnitTest` terminan bien, aunque Gradle los da por `UP-TO-DATE`: este diff no toca sus fuentes.

**Sync y Android horizontal: sin impacto.** El diff solo reordena la vista de Ajustes de escritorio. Android no se ha ejecutado.

**P1-10, resto: pendiente.** El punto pedía las mismas secciones con el mismo nombre y texto, no solo el mismo orden. Ninguno de estos puntos es una regresión; cerrarlos requiere decidir el comportamiento y, en algunos casos, trabajo nuevo.
- **Launcher, interruptores distintos.** Android tiene "Mostrar medio físico por defecto" y solo enseña "Obtener el medio físico automáticamente" si ese modo está activo y RomM configurado (`ConfigScreen.kt:695`). Escritorio no tiene el primero, y siempre enseña el segundo, deshabilitado sin credenciales. En escritorio el medio físico se elige por juego (`physicalIds`, `DesktopAppState.kt:660`). → Decidir si escritorio tiene un valor global. **Hecho en `ecc83cd`** (ver la revisión de `ecc83cd`).
- **Sync, nombre y contenido.** "Sincronización de partidas" con interruptor general, Wi-Fi y carga en Android, frente a "Saves y conflictos" solo con la política de conflictos en escritorio. No hay interruptor general en escritorio. → Unificar el título y decidir sobre el interruptor; Wi-Fi y carga son exclusivos de Android.
- **Nuevo, ES-DE en Avanzado.** Android tiene el interruptor "Usar ES-DE", que oculta esos ajustes (`13`). Escritorio no lo tiene y enseña siempre la carpeta y "Exportar metadata a ES-DE" en ámbar. → Decidir uno de los dos.
- **Nuevo (P2), versión instalada.** Android la muestra en Actualizaciones (`ConfigScreen.kt:950`), y escritorio en "Acerca de", no en Actualizaciones.

**Nuevo (P2, en vivo en Linux), pendiente; no justifica cambiar código por ahora:**
- **Iconos que no coinciden.** Biblioteca usa en el rail el mismo mando que Launcher, Sistema, Controles y Acerca de, mientras que Android usa una rejilla para Biblioteca y el mando para Jugar. Idioma y Tema siguen con el icono de lista genérico (P2-2). Media de juegos y ES-DE usan una imagen en escritorio y una carpeta en Android (`12`, `13`). Los iconos se ven como un rectángulo con una cruz, sin llegar al rectángulo en blanco de P0-1, aunque a este tamaño apenas se reconoce el mando.
- **"Guardar" sin cambios.** En Juegos locales, "Guardar" está activo y en ámbar sin haber cambiado la carpeta, mientras que "Guardar claves", en la misma pestaña, se deshabilita hasta que hay cambios.
- **"Escanear ahora" deshabilitado sin explicación.** Sin servidor aparece gris y no dice por qué. No se ha mirado la condición.
- **Ventana sin rellenar si falta el entorno de sway (reproducido).** Sin `SWAYSOCK` ni `XDG_CURRENT_DESKTOP`, `TilingWmWorkaround.needsRelaunch` no detecta sway: el contenido se queda en unos 800 px de alto y recortado por la derecha, con el resto de la ventana en negro y una línea blanca. Con esas variables se ve bien. Solo pasa con un entorno incompleto, como el de esta sesión; no se ha probado desde un lanzador real.

**Estado de otros puntos:**
- **Última pestaña de Ajustes cortada (de la revisión de `08de4ce`): comprobado.** La fila se desplaza al pulsar y "Acerca de" queda entera. Sigue sin haber más indicador que la pestaña cortada ("A").
- **"Ctrl+1…4" (P1-2): verificado en vivo** en "Acerca de".
- **P2-10 (sangría del enlace): sigue igual en escritorio.** "Avanzado: pegar un token de API" empieza en x≈126 y el resto de la tarjeta en x≈114.
- **Texto `romm.local_only_hint`: sigue fuera de lugar.** Dice "desde Ajustes" estando ya en Ajustes.
- **Sin verificar:** con credenciales y con RomM conectado, a pantalla completa (ancho máximo de P0-2) y en inglés.

**Capturas del manifiesto:** revisadas las 16 (Android, 2400×1080, 2026-11-05). Son anteriores a este cambio y `screens/desktop/` sigue vacío, así que **no muestran este diff**. Sirven de referencia del orden de Android (`09`, `10`, `12`, `14`), que coincide con el código en lo que se ve. Hallazgos nuevos a partir de ellas: solo el interruptor de ES-DE (`13`) y los iconos de `12` y `13`. Según esas capturas siguen abiertos P1-3, P2-1, P2-3, P2-4, P2-5 y P2-10, y la tarjeta de la cola de borde a borde de P1-11 (`06`); falta comprobarlos con el APK actual. `08` sigue mostrando la URL real del servidor. → Repetir el manifiesto con el APK actual y añadir capturas de escritorio. Las capturas en vivo de esta revisión eran temporales, se han borrado y no se han añadido al manifiesto.

**Estado del entorno:**
- La app de escritorio se abrió dos veces, siempre con configuración temporal (ya borrada), así que no se tocó la configuración real ni el servidor RomM. Se cerró matando sus JVM, por lo que `:desktop:run` terminó con `BUILD FAILED`; no es un fallo de la app. Solo se cambió de pestaña, sin modificar ajustes. El cursor de sway se movió para pulsar en la ventana.
- Al limpiar se ejecutó por error `pkill -P 0`, que no hacía falta. Solo apuntaba a `systemd` y `kthreadd`, procesos de root que un usuario normal no puede señalar. Después se comprobó que `systemd`, sway, Hermes y el emulador seguían activos.
- El emulador no se ha usado en esta revisión.

## Seguimiento (revisión de `6aaeb4e`, motivo de "Escanear ahora" deshabilitado, P1-10)
Diff revisado: `b315394..c84229f`. Cambia `ConfigScreen.kt`, `DesktopUi.kt` y las dos traducciones. La clave `android.config.scan_needs_server` pasa a llamarse `settings.scan.needs_server`, y no queda ningún uso de la clave antigua. Las dos traducciones están actualizadas.

- **"Escanear ahora" deshabilitado sin explicación (P2 de la revisión de `bc196a8`): hecho.** Escritorio muestra el motivo junto al botón cuando está en reposo, y Android lo muestra debajo. En las dos plataformas el texto y el color (`onSurfaceVariant`) son los mismos. En Android el texto pasa del rojo de error a `onSurfaceVariant`, lo cual es correcto: es una indicación, no un error.
- **Bug corregido en esta revisión (escritorio): el texto pedía configurar un servidor que ya estaba guardado.** Escritorio decide con `connected`, que también es `false` si el servidor está guardado pero `refreshPlatforms` falla (`DesktopAppState.kt:290`), y mientras cargan las plataformas al arrancar. En esos casos salía "Configura y guarda el servidor RomM antes de escanear". Android decide con `isConfigured` y no tiene este problema. **Arreglo:** con credenciales guardadas (`hasCredentials`) se muestra `connection.required` ("Conecta el servidor primero"); sin credenciales, el mismo texto que en Android. El botón sigue habilitándose con la misma condición de antes. Commit `fix(settings)` en `feat/opus-review-1`.
- **Tests:** ninguno nuevo. Es una condición de UI y `desktop` no tiene `compose-ui-test`. Se ejecutaron `:core:test`, `:desktop:test` y `testDebugUnitTest` con `--rerun-tasks`: terminan bien, y `:desktop:test` pasa sus 37 tests sin fallos.
- **Sin verificar en vivo:** no se ha abierto la app de escritorio ni el emulador en esta revisión. Los tres estados (sin servidor, servidor caído y conectado) solo se han comprobado leyendo el código.
- **Sync y Android horizontal: sin impacto.** El diff solo cambia un texto de Ajustes y su color. No toca ni la sync ni el layout.

**Capturas del manifiesto:** revisadas. Son las mismas 16 de Android del 2026-11-05, anteriores a este cambio y también a `b315394` (botones a ancho completo solo en vertical). En `10` todavía sale "Seleccionar carpeta" a ancho completo en horizontal, y la sección de escaneo queda fuera de la captura. `screens/desktop/` sigue vacío. No hay hallazgos visuales nuevos. Siguen pendientes los de la revisión de `bc196a8`. → Repetir el manifiesto con el APK actual, con al menos `10` desplazada hasta "Escanear biblioteca" y sin servidor, y añadir capturas de escritorio.

## Seguimiento (revisión de `c9c6164`, interruptor "Usar ES-DE" en escritorio, P1-10)
Diff revisado: `f553eb8..e262e77`. Cambia `ConfigScreen.kt`, `DesktopConfig.kt`, `DesktopUi.kt` y las dos traducciones. La clave `android.config.use_esde(_hint)` pasa a `settings.esde.use(_hint)` y no queda ningún uso de la antigua.

- **P1-10, ES-DE en Avanzado (de la revisión de `bc196a8`): hecho.** Escritorio tiene el mismo interruptor que Android, con el mismo texto, la misma clave de preferencia (`esde_export_enabled`) y la misma disposición (título `bodyLarge`, indicación `bodySmall` en `onSurfaceVariant`, `Switch` a la derecha). Con el interruptor apagado se ocultan la carpeta, "Exportar metadata a ES-DE" y su estado, igual que en Android (`13`). En escritorio la exportación solo se lanza a mano desde ese botón, así que ocultarlo basta: no hay ninguna exportación automática que se salte el interruptor.
- **Bug corregido en esta revisión (escritorio, regresión al actualizar):** antes de este cambio la exportación se veía siempre en escritorio. Con el valor por defecto `false`, quien ya la usaba dejaba de verla tras actualizar sin haber tocado nada. **Arreglo:** si no hay valor guardado, el interruptor parte encendido cuando `esde_data_dir` ya está guardado (solo se guarda al pulsar "Exportar"). Una instalación nueva sigue empezando apagada, como Android. Commit `fix(settings)` en `feat/opus-review-2`.
- **Tests:** nuevo `EsdeSwitchDefaultTest` (instalación nueva apagada, instalación que ya exportó encendida, la elección explícita manda sobre la migración). `:core:test`, `:desktop:test` y `testDebugUnitTest` terminan bien, y `:app:compileDebugKotlin` compila.
- **P2 (estilo de código, sin efecto visual):** el contenido del `if (esdeOn)` en `DesktopUi.kt` no está sangrado. No se toca.
- **Sync y Android horizontal: sin impacto.** En Android solo cambian las claves de texto; el texto mostrado es idéntico.
- **Sin verificar en vivo:** no se ha abierto la app de escritorio ni el emulador. El estado oculto/visible solo se ha comprobado leyendo el código.

**Capturas del manifiesto:** revisadas. Siguen siendo las 16 de Android del 2026-11-05 y `screens/desktop/` sigue vacío, así que no muestran el lado de escritorio de este diff. `13` sirve de referencia del interruptor de Android, que coincide con la implementación nueva de escritorio. No hay hallazgos visuales nuevos en el manifiesto.
- **Fuera del manifiesto:** en el directorio hay además `check-settings.png` (2026-11-07 05:45, Android horizontal, Ajustes › RomM). Muestra Ajustes con hamburguesa y **sin rail**, mientras que `08`–`14` usan rail. Esto refuerza P1-1 (dos modelos de navegación): **pendiente**, sin reproducir con el APK actual y sin saber qué condición lo provoca. También muestra de nuevo la URL real del servidor y "Disconnect RomM" ya con ancho ajustado y color de error (P1-5, hecho y verificado aquí). Sigue la sangría de "Advanced: paste an API token" (x≈116 frente a x≈84; P2-10, pendiente).

## Seguimiento (revisión de `ecc83cd`, "Mostrar medio físico por defecto" en escritorio, P1-10)
Diff revisado: `4e65872..81240a2`. Cambia `DesktopAppState.kt`, `DesktopConfig.kt` y `DesktopUi.kt`. Las claves `settings.launcher.physical_default(_desc)` ya existían y se comparten con Android, en los dos idiomas.

- **P1-10, interruptores del Launcher (de la revisión de `bc196a8`): hecho.** Escritorio tiene ahora "Mostrar medio físico por defecto", y solo enseña "Obtener el medio físico automáticamente" con ese modo activo y credenciales guardadas, igual que Android (`ConfigScreen.kt:695`). El orden, los textos y la disposición (título `bodyLarge`, indicación `bodySmall` en `onSurfaceVariant`, `Switch` a la derecha y 16 dp entre filas) coinciden con Android (`09`).
- **Lógica: correcta y con paridad.** La elección por juego manda sobre el valor global en los dos sentidos: `physicalIds` fuerza el medio físico y el nuevo `cardIds` fuerza la carta. Un mismo juego nunca queda en las dos listas. La petición automática a RomM exige ahora `autoPhysicalMedia && launcherDefaultPhysical`, la misma condición que `AutoPhysicalMediaWorker` en Android. El launcher solo muestra el medio físico si hay archivo (`physicalUrl != null`), así que activar el valor global no deja juegos en blanco. Con el valor por defecto (`false`) y sin `cardIds`, quien ya tenía juegos marcados a mano los ve igual que antes.
- **P2, pendiente (sin cambio de código):** quien activó "Obtener el medio físico automáticamente" en una build de `develop` deja de recibirlo tras actualizar, porque el nuevo interruptor arranca apagado y el segundo queda oculto. Esa función (`929eaca`) solo ha salido en builds de `develop` y Android se comporta igual, así que no se migra. Si se quisiera migrar, habría que partir de `launcher_default_physical = auto_physical_media`, pero eso cambia lo que se ve en el launcher sin que el usuario lo haya pedido.
- **Tests: pendiente.** No hay ninguno para la precedencia por juego frente al valor global (`isPhysicalMedia`/`togglePhysicalMedia`). Viven en `DesktopAppState` y leen `DesktopConfig` al construirse, así que no se pueden probar de forma aislada sin extraerlos. `:core:test`, `:desktop:test` y `testDebugUnitTest` pasan.
- **Sync y Android horizontal: sin impacto.** No toca la sync ni el código de `app`.
- **Sin verificar en vivo:** no se ha abierto la app de escritorio ni el emulador. Los estados del interruptor solo se han comprobado leyendo el código.

**Capturas del manifiesto:** revisadas. Siguen siendo las 16 de Android del 2026-11-05 y `screens/desktop/` sigue vacío, así que no se ve el lado de escritorio de este diff. `09` sirve de referencia: con el interruptor apagado, Android oculta la fila automática, igual que la implementación nueva de escritorio. No hay hallazgos visuales nuevos. Siguen pendientes P1-1, P1-3, P2-1, P2-3, P2-4, P2-5 y P2-10, y el resto de P1-10 (nombre y contenido de Sync, versión en Actualizaciones). → Repetir el manifiesto con el APK actual y añadir capturas de escritorio.

## Seguimiento (revisión de `f0f7c1d`, interruptor general de sync de saves en escritorio, P1-10)
Diff revisado: `7bfbca1..b82d9b8`. Cambia `DesktopAppState.kt`, `DesktopConfig.kt`, `DesktopLauncher.kt`, `DesktopUi.kt` y `Main.kt`. Las claves (`common.save_sync`, `window.tray.sync_saves`, `android.config.sync_saves_hint`) ya existían y se comparten con Android.

- **Correcto y con paridad:** el valor por defecto es "activado", como Android. Al apagarlo se ocultan Saves en el rail, el menú de Play y los ajustes de conflictos/intervalo, se para el sync automático y, si estabas en Saves, te lleva a Ajustes (Android hace lo mismo en `RomMSyncApp.kt:89`). El atajo Ctrl+1…4 salta Saves, así que Ctrl+3 abre Ajustes y Ctrl+4 no hace nada. Como sigue el orden del rail, se da por bueno.
- **P1, hecho en `feat/opus-review-4`: la bandeja seguía sincronizando.** La bandeja seguía mostrando "Sincronizar saves", que lanzaba `syncSaves()` aunque el interruptor estuviera apagado. Ahora el elemento se oculta con la sync apagada (`Main.kt`) y `syncSaves()` no hace nada en ese estado (`DesktopAppState.kt`). Android no tiene bandeja.
- **P2, hecho en `feat/opus-review-4`: el detalle del juego seguía mostrando la configuración de sync.** "Excluir de la sincronización" y la ruta de saves por juego seguían visibles en el detalle de escritorio (`GameDetail.kt`). Android los oculta con la sync apagada (`LibraryScreen.kt:1625-1628`). Ahora también se ocultan en escritorio. El historial de copias locales se sigue mostrando, como en Android. Si no hay copias, desaparece toda la sección, incluido el divisor.
- **P3, pendiente (sin cambio):** si se apaga el interruptor con un sync en curso, ese ciclo no se cancela y termina normalmente. Android tampoco cancela el trabajo en curso.
- **Tests: hecho en parte.** Nuevo `SaveSyncSwitchDefaultTest`: comprueba que arranca activado y que el valor se guarda. La parte de UI (rail, bandeja, detalle) no tiene tests porque `DesktopAppState` lee `DesktopConfig` al construirse. `:core:test`, `:desktop:test` y `testDebugUnitTest` pasan.
- **Android horizontal: sin impacto.** El diff no toca `app`.
- **Sin verificar en vivo:** no se han abierto ni la app de escritorio ni el emulador. Los estados se han comprobado leyendo el código.

**Capturas del manifiesto:** revisadas. Son las mismas 16 de Android del 2026-11-05 y `screens/desktop/` sigue vacío, así que no se ve nada de escritorio de este diff. `11-settings-Sync___saves.png` sirve de referencia para el orden interruptor → opciones, que escritorio ya respeta. Fuera del manifiesto hay una `check-settings.png` (Android, Ajustes › RomM, 2026-11-07) sin problemas nuevos: las pestañas, la tarjeta y el botón destructivo son coherentes con `08`. No hay hallazgos visuales nuevos. Siguen pendientes P1-1, P1-3, P2-1, P2-3, P2-4, P2-5, P2-10 y el resto de P1-10. → Repetir el manifiesto con capturas de escritorio.

## Seguimiento (revisión de `39a2ab1`, versión en "Acerca de" en las dos plataformas, P1-10)
Diff revisado: `c6e8958..aa85de2`. Cambia `ConfigScreen.kt`, `DesktopUi.kt` y las dos traducciones (`settings.about.version` pasa de "Atril desktop v{0}" a "Atril v{0}"). **Sin cambios de código en esta revisión:** no hay bugs ni regresiones.

- **P1-10, versión instalada (de la revisión de `bc196a8`): hecho.** Android añade la tarjeta "Acerca de" (icono `Info`) al final de su categoría, con `BuildConfig.VERSION_NAME`. Escritorio añade "Versión instalada: {0}" en Actualizaciones, con el mismo estilo que Android (`bodyMedium`, `onSurfaceVariant`, 12 dp antes del botón). Ahora la categoría tiene en las dos plataformas Controles → Actualizaciones → Acerca de. Las dos versiones vienen sin "v" (`versionName = "0.9.2"`, `DesktopConfig.appVersion = "0.9.2"`), así que no sale "vv". `settings.about.version` solo se usa en estas dos tarjetas, y el cambio de texto no afecta a nada más.
- **P2, pendiente (sin cambio de código): la versión aparece tres veces en la misma vista.** En las dos plataformas se ve en el rail (`a3f61ad`/`227e931`), en Actualizaciones ("Versión instalada: 0.9.2") y en "Acerca de" ("Atril v0.9.2"). Es redundante, pero coherente entre plataformas, y la tarjeta "Acerca de" de Android solo tiene esa línea. → Si se quiere aligerar, quitar la línea de Actualizaciones en las dos plataformas o dar más contenido a "Acerca de" (en escritorio ya tiene los atajos).
- **P2, pendiente: el icono de "Acerca de" no coincide.** Es `Info` en Android y el mando (`AppIcons.Gamepad`) en escritorio. Se suma al punto "Iconos que no coinciden" de la revisión de `bc196a8`.
- **Tests:** ninguno nuevo; solo es UI y `desktop` no tiene `compose-ui-test`. `:core:test`, `:desktop:test` y `:app:compileDebugKotlin` terminan bien con `--rerun-tasks`.
- **Sync y Android horizontal: sin impacto.** No toca la sync. En Android solo añade una tarjeta al final de una columna que ya se desplaza.
- **Sin verificar en vivo:** no se han abierto ni la app de escritorio ni el emulador.

**Capturas del manifiesto:** revisadas. Son las mismas 16 de Android del 2026-11-05, anteriores a este cambio. `14-settings-About.png` solo muestra Controles: la tarjeta nueva y Actualizaciones quedan fuera de la captura, así que el resultado no se ve. `screens/desktop/` sigue vacío. Hallazgo que sigue abierto en `14`: la nota de Controles menciona "/dev/input" en Android (P2 de la revisión de `6b36ee2`, **pendiente**). No hay hallazgos visuales nuevos. Siguen pendientes P1-1, P1-3, P2-1, P2-3, P2-4, P2-5, P2-10 y el nombre y contenido de Sync (P1-10). → Repetir el manifiesto con el APK actual, con `14` desplazada hasta el final, y añadir capturas de escritorio.

## Seguimiento (revisión de `64c2399`, iconos del rail en escritorio, P1-10)
Diff revisado: `e8ae0e3..1990484` (dos líneas en `DesktopUi.kt`). **Sin cambios de código en esta revisión:** no hay bugs ni regresiones.

- **P1-10, iconos de navegación (punto "Iconos que no coinciden" de la revisión de `bc196a8`): hecho para el rail.** Jugar usa ahora el mando (`AppIcons.Gamepad`) y Biblioteca la cuadrícula de 2×2 (`AppIcons.Shelf`). Es lo mismo que Android: `SportsEsports` y `GridView` en `Screen.kt`, como se ve en el rail (`03`) y en el cajón (`02`). `AppIcons.PlayArrow` se sigue usando en los botones "Jugar" de `DesktopLauncher.kt:985` y `GameDetail.kt:216`, así que no queda código muerto.
- **P3, pendiente (sin cambio): el estado vacío de Biblioteca en escritorio sigue con el mando.** `LibraryEmptyState` (`DesktopUi.kt:903`) usa `AppIcons.Gamepad`; Android muestra la cuadrícula (`03`). → Cambiarlo a `AppIcons.Shelf` si se retoma la paridad de iconos.
- **Siguen pendientes los demás iconos de ese punto:** el mando en Launcher, Sistema, Controles y Acerca de (`Info` en Android); Idioma y Tema (P2-2); Media de juegos y ES-DE (imagen en escritorio, carpeta en Android).
- **Tests:** ninguno nuevo; es un cambio de icono y `desktop` no tiene `compose-ui-test`. `AppIconsTest` no incluye `Shelf`, pero el cambio no lo justifica. `:desktop:test` pasa.
- **Sync y Android horizontal: sin impacto.** No toca la sync ni `app`.
- **Sin verificar en vivo:** no se ha abierto la app de escritorio.

**Capturas del manifiesto:** revisadas. Son las mismas 16 de Android del 2026-11-05 y `screens/desktop/` sigue vacío, así que no se ve el rail nuevo de escritorio. `02` y `03` confirman la referencia de Android: mando para Jugar y cuadrícula para Biblioteca, en el rail y en el cajón. No hay hallazgos visuales nuevos aparte del estado vacío de arriba. Siguen pendientes P1-1, P1-3, P2-1, P2-3, P2-4, P2-5, P2-10 y el nombre y contenido de Sync (P1-10). → Repetir el manifiesto con capturas de escritorio.

## Seguimiento (revisión de `0f7cf42`, iconos de sección de Ajustes en las dos plataformas, P1-10)
Diff revisado: `f997c7b..6465c24`. Cambia `ConfigScreen.kt`, `AppIcons.kt` (nuevo `AppIcons.Info`), `DesktopUi.kt` y `AppIconsTest.kt`. **Sin cambios de código en esta revisión:** no hay bugs ni regresiones.

- **P1-10, iconos de sección (punto "Iconos que no coinciden" de la revisión de `bc196a8`): hecho.** Se comparó sección a sección: Servidor → nube de descarga, ROMs → carpeta, Plataformas → almacenamiento, Escanear → lupa, ES-DE / Media al descargar / Carátulas locales → imagen, Descargas → descarga, Launcher y Controles → mando, Sync → sync, Idioma → globo (`Public`), Tema → estrella, Actualizaciones → refrescar, Acerca de → `Info`. Coinciden en las dos plataformas. También se cierra P2-2 (Idioma y Tema con el icono genérico) y el P3 del estado vacío de Biblioteca (ahora `AppIcons.Shelf`, como la cuadrícula de Android en `03`). "Sistema" (solo escritorio) sigue con el mando; no tiene equivalente en Android.
- **`AppIcons.Info`: correcto.** Círculo + "i" en un único path `EvenOdd`, así que la "i" queda hueca y no sale el rectángulo sólido de P0-1. `AppIconsTest` lo cubre.
- **P3, pendiente (sin cambio): el servidor RomM ha perdido su icono más claro.** Android usaba `Dns` (pila de servidores, ver `check-settings.png`) y ahora usa la nube de descarga para igualar a escritorio. Escritorio ya tiene `AppIcons.Storage`, casi el mismo glifo, pero ese se usa en Plataformas. Es coherente entre plataformas, solo menos descriptivo. Tres secciones comparten la imagen (ES-DE, Media al descargar y Carátulas locales), aunque estén en pestañas distintas.
- **P3, pendiente (estilo de código): imports sin usar** en `ConfigScreen.kt` (`Icons.Outlined.Dns` e `Icons.Outlined.Language`). Solo generan avisos del compilador. No se tocan.
- **Tests:** `:core:test`, `:desktop:test` (42 tests, sin fallos), `testDebugUnitTest` y `:app:compileDebugKotlin` terminan bien con `--rerun-tasks`.
- **Sync y Android horizontal: sin impacto.** Solo cambian los `ImageVector` de las cabeceras, que miden lo mismo, así que el layout no cambia.
- **Sin verificar en vivo:** no se han abierto ni la app de escritorio ni el emulador.

**Capturas del manifiesto:** revisadas. Siguen siendo las 16 de Android del 2026-11-05, anteriores a este cambio, y `screens/desktop/` sigue vacío, así que **no muestran este diff**. `09` y `check-settings.png` enseñan todavía los iconos antiguos (globo `Language` en Idioma, `Dns` en Servidor), que sirven de referencia para el P3 de arriba. No hay hallazgos visuales nuevos. Siguen pendientes P1-1 (hamburguesa sin rail en `check-settings.png`), P1-3, P2-1, P2-3, P2-4, P2-5, P2-10 (sangría de "Advanced: paste an API token", que sigue en `check-settings.png`) y el nombre y contenido de Sync (P1-10). → Repetir el manifiesto con el APK actual y añadir capturas de escritorio.
