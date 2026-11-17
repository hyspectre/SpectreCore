# SpectreCore 0.4.1-alpha: libro de armaduras

## Instalar

1. Detén el servidor. Sustituye el JAR de SpectreCore 0.4.0 por SpectreCore-0.4.1-alpha.jar. No dejes ambos JAR.
2. En plugins/MMOItems/item/ARMOR.yml reemplaza únicamente los 20 IDs del archivo ARMOR_20_PIEZAS_LORE_COMPACTO_v4.yml. Conserva las demás armaduras.
3. Conserva plugins/SpectreCore/config.yml y todos los datos de cocina. Esta actualización no cambia recetas, estadísticas, habilidades ni bonos activos.
4. Arranca el servidor. Abre /armaduras o /libroarmaduras como jugador. No requiere OP.
5. Obtén piezas nuevas de MMOItems para comprobar el lore actualizado. Los objetos ya entregados pueden conservar su lore hasta que MMOItems los regenere mediante su sistema habitual de actualización.

## Libro

Es un libro virtual: se abre sin ocupar ranuras ni entregar objetos. El índice enlaza a cinco conjuntos; cada página ofrece regreso al índice y las flechas normales permiten recorrer el detalle. El texto se divide en líneas de hasta 18 caracteres y páginas de diez líneas de contenido, más encabezado y enlace.

Los valores de habilidades se consultan en la configuración de SpectreCore. Los bonos de MMOItems se leen de su item-sets.yml al abrir el libro; no se sustituyen por los antiguos bonos previstos. Si no existe la entrada, el libro lo informa. Una habilidad adicional heredada en MMOItems sigue siendo una habilidad adicional: el libro no la elimina ni modifica.

El libro diferencia los efectos del núcleo, los bonos MMOItems y las sinergias con armas. Un bonus que MMOItems publique como cinco piezas exige que el arma cuente para su conjunto; SpectreCore verifica directamente la espada para sus sinergias.

## Pruebas pendientes dentro de Paper

- Abrir el libro con y sin OP, y con el inventario lleno. No debe añadir ni quitar objetos.
- Pulsar los cinco enlaces del índice y volver con el enlace de cada página; recorrer todo el texto sin recortes.
- Confirmar que las cinco habilidades conservan su funcionamiento y la cocina responde.
- Comparar los bonos mostrados con item-sets.yml, especialmente cualquier entrada antigua que siga activa.
- Verificar piezas recién generadas: solo conjunto, habilidad y comando de consulta en el lore personalizado.

La compilación Gradle terminó correctamente con Java 21 y Paper API 1.21.10. No se ha ejecutado en un servidor real. El formato global de MMOItems todavía controla espacios, estadísticas, sockets y traducciones: este paquete no corrige la traducción ausente de max-mana ni altera el número de sockets.
