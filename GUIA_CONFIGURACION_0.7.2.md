# SpectreCore 0.7.2-alpha — mensajes y valores configurables

## Dónde editar

Conserva los ocho módulos de 0.7.1. config.yml activa o desactiva categorías. Cada carpeta modules/<módulo>/ contiene:

| Archivo | Contenido |
|---|---|
| config.yml y archivos de subsistemas | Reglas de juego, habilitación, tiempos, probabilidades, IDs, recetas y recompensas |
| mensajes.yml | Textos de chat, consola, títulos, barras de acción, libros, menús, pistas, nombres y etiquetas |
| ajustes.yml | Sonidos, volumen, tono, colores de texto, partículas, cantidad, dispersión, RGB, tamaño, materiales decorativos y geometría |

modules/nucleo/ guarda textos administrativos, el comando económico alternativo y las animaciones compartidas de tesoros, gemas, almas y cofres. No agrega una categoría de juego ni necesita interruptor.

Los nombres de las secciones de presentación identifican el sistema; busca la frase actual en mensajes.yml para localizarla. El catálogo enumera cada clave, su archivo y valor predeterminado. Algunos textos construidos por partes tienen varias etiquetas editables.

## Mensajes y marcadores

Usa colores &a, &6, &l, etc., caracteres Unicode y saltos de línea YAML. Las claves mensaje-XXX y etiqueta-XXX son estables: modifica sus valores, conserva sus nombres.

Los marcadores entre llaves insertan los valores reales; puedes moverlos o eliminarlos al redactar. No cambies su nombre. Los marcadores no son PlaceholderAPI. No se ejecuta código de los textos. Por ejemplo:

    AsterionSkill:
      barra-furia: '&cFuria: &f{furia}/{maximo}{aviso}'
      furia-lista: ' &e¡Lista!'

El libro de armaduras lee los valores actuales de habilidades y los nuevos bonos parciales. Los bonos configurados en MMOItems continúan leyéndose de MMOItems; no se modifican sus archivos.

## Parámetros añadidos

| Ruta | Parámetros |
|---|---|
| equipamiento/config.yml | pasivos, furia-maxima, libro.conjuntos y libro.colores |
| resonancia/config.yml | reglas.carga-por-tier y reglas.calidad (cada tier suma 100) |
| tesoros/config.yml | clasificacion de minerales, entidades y cultivos; clasificación de minería de trabajos independiente para conservar sus reglas anteriores |
| tesoros/combate.yml | cantidad |
| tesoros/agricultura.yml | tipo y cantidad |
| cocina/config.yml | herramientas.crear, revolver, servir y limpiar |
| dungeons/jefes.yml | bloqueos de vida del Minotauro |
| dungeons/portal.yml | zona-extraccion |
| trabajos/config.yml | avisos.intervalo-cooldown-milisegundos |
| slayer/config.yml | avisos.cada-bajas; nivel de mob y espera entre reintentos de invocación |
| actividades/config.yml | cofre.nombres y cofre.raros, además de ofertas, premios y comandos existentes |
| nucleo/ajustes.yml | EconomyBridge.comando-deposito: acepta {jugador} y {cantidad}; se usa si se permite el fallback y no hay proveedor Vault |

Los materiales y entidades se escriben con nombres Bukkit en mayúsculas. Los sonidos de tipo Sound usan el nombre en mayúsculas. Los sonidos escritos como minecraft:... pueden utilizar un identificador de sonido personalizado. Los colores NamedTextColor usan su nombre de la paleta (GOLD, LIGHT_PURPLE, etc.); los colores RGB se editan por canales 0–255. Las partículas sustitutas deben aceptar el mismo tipo de datos que la original.

No hay probabilidades ni salarios inventados: se mantienen los existentes o sin configurar. Las distribuciones de calidad de gemas predeterminadas siguen siendo 100/0/0, 75/25/0 y 50/40/10.

## Instalar y recargar

1. Detén el servidor y respalda plugins/SpectreCore/.
2. Sustituye el JAR anterior por SpectreCore-0.7.2-alpha.jar; conserva tus configuraciones.
3. Inicia el servidor. Se crean los archivos nuevos y se añaden solamente las claves que falten.
4. Edita los archivos y usa /spectrecore reload. Consulta /spectrecore modulos para los estados.

Los mensajes se actualizan al recargar. Los parámetros se aplican al siguiente uso, activación o creación del efecto: no se reinician lotes, misiones o animaciones que ya estén en curso. Los materiales de displays ya creados pueden necesitar recrearse. Los interruptores enabled siguen requiriendo reiniciar. No uses /reload de Bukkit.

Un YAML inválido, número fuera de rango, distribución incorrecta o efecto incompatible rechaza la recarga y conserva la última configuración válida en memoria. Las nuevas claves se pueden añadir al archivo antes de esa validación; corrige el error indicado y recarga otra vez.

Al ampliar configuraciones modulares existentes, se conserva su primera copia en backups/configuration-v2/modules/. La migración de formatos anteriores mantiene backups/configuration-v1/. Los datos de jugadores, estaciones y premios no cambian de ruta. No copies CONFIGURACION_EJEMPLO encima de tus ajustes propios.

## Alcance y comprobaciones

Se externalizaron los textos y parámetros de presentación y se ampliaron los valores de juego editables. Siguen fijos los contratos técnicos: IDs de comandos y permisos, claves de almacenamiento, integración de tags, estructura de ranuras, coreografías de entidades, 20 ticks por segundo, conversiones de unidades y límites de seguridad. Esos elementos no son parámetros de balance.

La compilación incluye pruebas de reglas, migración y cambios reales de configuración: textos, volumen decimal, calidad de gemas, herramientas y rechazo de recargas inválidas. Las comprobaciones de compatibilidad de materiales con el registro Bukkit se completan en el servidor; todavía se necesita la prueba de integración en Paper. Verifica también el comando de salida de Dungeons+ en tu instalación.

También puedes editar las descripciones y los ejemplos de uso de comandos en modules/nucleo/comandos.yml. Los nombres de comandos permanecen estables.
