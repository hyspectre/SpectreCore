# Integración del Mitrilo — Hydev / SpectreCore 0.9.0-dev3

## Identidad y progresión

**Mitrilo** es un recurso único de MMOItems: `MATERIAL:MITRILO`. Se obtiene como hallazgo de minería, sin generar bloques adicionales ni sustituir Paladio.

- Menas T2 (Redstone y Lapislázuli): **1 %** de hallazgo por bloque elegible.
- Menas T3 (Diamante, Esmeralda y otras clasificadas): **2 %** de hallazgo.
- Requiere contrato de **Minería Experimentado** o superior; el botín funciona desde rango 2.
- Obtienes **1 o 2 unidades**, compartiendo el tiempo de espera de tesoros mineros.
- El Pico de Mitrilo multiplica estas probabilidades por **1,25**; el Pico de Mitrilo Templado, por **1,60**.
- El Mitrilo se entrega antes de evaluar el tesoro minero habitual: nunca se dan dos recompensas del mismo bloque.

Las probabilidades y cantidades están en `modules/tesoros/mineria.yml`. Los rangos, precios y recompensas se regulan en `modules/trabajos/config.yml`. Si necesitas ajustar balance, edita YAML y utiliza el mecanismo habitual de recarga de SpectreCore.

## Objetos MMOItems

Los archivos `integracion-mmoitems/MATERIAL_MITRILO.yml` y `TOOL_MITRILO.yml` **son fragmentos para fusionar con tipos existentes**:

1. Respalda `plugins/MMOItems/item/MATERIAL.yml` y `plugins/MMOItems/item/TOOL.yml`.
2. Añade la entrada `MITRILO:` del primer fragmento a `MATERIAL.yml` **sin borrar el resto de minerales**.
3. Añade `PICO_MITRILO:` y `PICO_MITRILO_TEMPLADO:` a `TOOL.yml`.
4. Recarga MMOItems con el comando administrativo apropiado y verifica que se creen los tres objetos.
5. **No crees tipos MMOItems nuevos:** se reutilizan `MATERIAL` y `TOOL`.

Los picos poseen materiales base DIAMOND_PICKAXE y NETHERITE_PICKAXE. Los multiplicadores especiales los aplica SpectreCore al verificar los IDs reales de MMOItems, nunca por el nombre o lore del objeto.

## Fabricación mediante la Forja existente

`RECETAS_FORJA_MITRILO.yml` contiene dos entradas compatibles con la sección `recipes:` de una estación de fabricación MMOItems:

- **Pico de Mitrilo:** 12 Mitrilo, 6 lingotes de hierro y 2 palos.
- **Pico de Mitrilo Templado:** 24 Mitrilo, 8 diamantes y 1 Pico de Mitrilo.

**Importante:** el archivo de la estación que abre el NPC Forja no forma parte de este repositorio. Este fragmento no se instalará automáticamente. Localiza el YAML de la estación real, fusiona ambas recetas con su sección `recipes:` y conserva los demás productos. Revisa el formato según la versión concreta de MMOItems. No crees una segunda Forja.

## Pedido del Herrero

Desde `/spectrecore trabajos abastecimiento` se puede entregar **4 Mitrilo por 180 monedas** de manera repetible, con contrato de Minería Experimentado. El sistema identifica objetos por tipo/ID MMOItems y retira exactamente la cantidad solicitada del inventario principal. No se puede entregar material renombrado.

- Antes de retirar materiales, SpectreCore guarda la operación en `data/jobs/purchases.yml`.
- Si se confirma el pago Vault, cierra la transacción. **No se usa consola para simular pagos exitosos.**
- Si algo falla entre el retiro y el pago, **no** reembolsa automáticamente una operación ambigua; queda pendiente para administración, evitando pago duplicado.
- `/spectrecore trabajos transacciones` permite consultar los casos, y `/spectrecore trabajos resolver <uuid>` cerrarlos **solo tras verificar inventario, monedas y permisos**.

## Herramientas de exploración y energía (0.9.0-dev4)

Esta versión mantiene `MATERIAL:MITRILO` y agrega tres nuevos objetos al archivo `TOOL_MITRILO.yml`, así como dos materiales a `MATERIAL_MITRILO.yml`:

| Objeto | Tipo/ID MMOItems | Uso |
|---|---|---|
| Linterna de Mitrilo | `TOOL:FAROL_MITRILO` (ID conservado) | Visión nocturna y pulso de menas |
| Barrena de Mitrilo | `TOOL:BARRENA_MITRILO` | Túnel de 1×2, máximo 8 tramos y 16 bloques |
| Lingote de Mitrilo | `MATERIAL:LINGOTE_MITRILO` | Refinamiento intermedio a partir de Mitrilo |
| Cristal de Mitrilo | `MATERIAL:CRISTAL_MITRILO` | Restaura 50 puntos a la herramienta sostenida en la mano secundaria |

### Energía compartida en concepto, independiente por objeto

Cada herramienta de Mitrilo guarda su propia carga en los datos persistentes del objeto, hasta **100 puntos**. Se obtienen **4 puntos** por mena natural minada a mano con pico; no cuentan bloques colocados, movidos por pistones ni la extracción automática del Pico/Barrena. Se conserva la energía al transferir, desconectar o almacenar el objeto. No se mezcla con la Resonancia del Pico Perdido.

Para recargar, sostén **Cristal de Mitrilo en la mano principal** y una herramienta de Mitrilo en la secundaria, luego haz clic derecho. La Linterna no consume ni almacena resonancia.

### Controles y balance

- **Pico de Mitrilo (ambos modelos):** agáchate y haz clic derecho mirando una mena natural para marcar hasta 8 minerales conectados dentro de radio 5, durante 5 s. Activación de 25 puntos, duración de 15 s y recarga de 20 s. Durante ese periodo, romper manualmente una mena puede extraer hasta 3 bloques conectados adicionales, pagando 5 puntos y 1 de durabilidad por bloque; no concede cobros ni recompensas de minería manual por esos bloques. El aumento pasivo a la probabilidad de hallar Mitrilo se mantiene.
- **Linterna de Mitrilo:** clic derecho proporciona Visión nocturna durante 60 s, renovable. Agáchate y haz clic derecho para revelar hasta 24 menas naturales de la clasificación minera en radio 10, con contorno brillante visible solo para el jugador durante 3 s. Recarga de 140 s por jugador, incluso si no hay menas; no se carga ningún chunk ni se cambian bloques. La Bobina de Cobre aumenta el alcance en 4 bloques (máximo 16) y no altera la duración.
- **Barrena de Mitrilo:** agachado + clic derecho para perforar roca ordinaria. Cuesta 50 puntos, recarga 45 s, excava un bloque cada 0,25 s hasta 16 bloques en un túnel de 8×1×2. Usa el botín normal sin multiplicadores, consume 1 de durabilidad por bloque y se detiene ante menas, líquidos, contenedores, zonas protegidas, chunks no cargados o si el jugador se aleja/cambia la herramienta.

Los valores se configuran en `modules/tesoros/mineria.yml`, sección `herramientas-mitrilo`. Los nombres y mensajes se configuran mediante los archivos MMOItems y `modules/tesoros/mensajes.yml`.

### Instalación y pruebas necesarias

1. Fusiona las entradas nuevas en los **archivos existentes** `plugins/MMOItems/item/MATERIAL.yml` y `TOOL.yml`, conservando todos los IDs previos.
2. Retira las recetas antiguas `brujula-geologica` y `farol-mitrilo`. Añade los fragmentos de recetas de linterna, barrena y carga resonante a la estación **real** de MMOItems que utiliza el NPC Forja. Su archivo no está en este repositorio, por lo que **no se han instalado ni verificado en el servidor**. Revisa el formato de recetas con tu versión exacta de la estación.
3. Conserva el ID `FAROL_MITRILO` para actualizar los Faroles existentes con revisión 7. La Brújula anterior deja de tener habilidad y receta; no se borran objetos de inventarios. El instalador v2 retira las recetas conocidas con respaldo. Verifica que WorldGuard esté presente y que las funciones de prospección y excavación respeten las regiones; si la API no está disponible, dichas habilidades se bloquean.
4. Prueba en Paper el guardado de PDC al guardar/transferir los ítems, desconexiones, compatibilidad de durabilidad con MMOItems, tiempos, drops y configuración de ItemsAdder antes de desplegar.

La carga de energía y la minería automática no alteran el progreso de AuraSkills por diseño ni generan pagos de trabajo adicionales; no se prometen comportamientos externos de terceros sin prueba ingame.

## Pico de Amatista — resonancia amplificada (0.9.0-dev5)

El Pico de Amatista es un objeto de `TOOL`, ID estable **`PICO_AMATISTA`**, que comparte la energía persistente de las herramientas de Mitrilo, pero se especializa en **extraer gemas** en lugar de vetas o túneles.

- **Receta en la Forja:** 20 Mitrilo (`MATERIAL:MITRILO`), 12 Paladio (`MATERIAL:PALADIO`) y 16 fragmentos de amatista. Los tres ingredientes son obligatorios.
- **Carga:** minar una mena natural con el Pico suma 4 puntos a su resonancia, hasta 100. Los bloques colocados y las habilidades automáticas no generan carga.
- **Activación:** agacharse y hacer clic derecho con el Pico. Consume **75 de resonancia para cinco bloques** (60 para cuatro, 45 para tres), tiene recarga de 120 segundos e invoca **hasta cinco bloques hechizados** durante 30 segundos.
- **Extracción:** golpea cada bloque tres veces con ese mismo Pico para romperlo. Cada bloque deja una gema MMOItems (`GEM_STONE`) y desaparece. Solo quien lo invocó puede obtener sus recompensas.
- **Probabilidades por bloque:** 75 % gema básica, 22 % gema refinada y 3 % gema arcana. Los cristales no sueltan bloques de amatista del mundo.
- **Ubicación:** cristales visibles mediante entidades `BlockDisplay` con hitbox `Interaction`. Aparecen cerca del jugador, únicamente donde existe espacio y el jugador tiene permiso; no sustituyen bloques de construcciones ni se pueden llevar como objetos. Si no hay tres posiciones seguras, no se consume resonancia.
- **Seguridad:** límite global de ocho campos simultáneos, máximo cinco cristales por campo. Desaparecen al terminar el tiempo, cerrar el servidor, desconectarse o teletransportarse; tras un reinicio se limpian las entidades huérfanas al cargar chunks.

### Archivos MMOItems necesarios

1. Fusiona la entrada `PICO_AMATISTA` del archivo `integracion-mmoitems/TOOL_MITRILO.yml` con el archivo `TOOL.yml` de la instalación.
2. Fusiona las tres entradas de `integracion-mmoitems/GEM_STONE_AMATISTA.yml` con el archivo `GEM_STONE.yml`, **sin reemplazar otras gemas**.
3. Fusiona la receta `pico-amatista` de `RECETAS_FORJA_MITRILO.yml` con la estación de la Forja ya instalada y verifica la sintaxis de esa versión de MMOItems. No se implementó una Forja duplicada.
4. Revisa la obtención de **Paladio**. En la configuración base sus probabilidades de hallazgo siguen vacías: no se activaron automáticamente para evitar alterar su economía sin acuerdo. Puede obtenerse mediante fuentes existentes del servidor o al definir tasas en `tesoros/mineria.yml`.
5. Verifica los tres IDs `GEM_STONE` y la integración WorldGuard en Paper antes de probar la habilidad. Si faltan gemas reales o un espacio seguro, se bloquea la invocación sin gastar energía.

Los ajustes de energía, duración, número de cristales, golpes y calidades están en `modules/tesoros/mineria.yml` bajo `herramientas-mitrilo.pico-amatista`; los mensajes del jugador están en `modules/tesoros/mensajes.yml`.

**Alcance:** los bloques son manifestaciones mágicas rompibles mediante golpes a sus hitboxes, no bloques reales colocados. La mecánica de golpeo depende de eventos Paper y requiere una prueba ingame que todavía no se ha realizado.

## Estado de validación

Esta entrega se prueba mediante GitHub Actions (sintaxis, Java, YAML, reglas y código de transacciones). Permanecen aplazadas las pruebas completas dentro de Paper, fallos simulados Vault/LuckPerms y daño mágico MythicMobs/MMOItems, según el acuerdo para cerrar 0.9.

**No instalar directamente en producción ni fusionar sobre archivos existentes sin respaldo.**

Las plantillas de herramientas usan ranuras reales de color `'1'` y el formato `herramienta-minera`. Usa el instalador v2 para actualizar las herramientas existentes conservando sus ranuras, y el archivo `mineria-v2/herramienta-minera.yml` en `MMOItems/language/lore-formats/`. Consulta `mineria-v2/LEEME_INSTALACION.md` antes de revisar objetos con gemas.
