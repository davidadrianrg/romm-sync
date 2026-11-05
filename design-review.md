# Revisión de diseño de Atril: coherencia visual (Android 2400×1080 + escritorio 2560×1440)

Fuente: `/tmp/atril-screens/manifest.md`, con 18 capturas de Android y 12 de escritorio. Se abrieron todas.
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
