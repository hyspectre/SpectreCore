# Taller del Minero — Mineralurgia nativa (SpectreCore 0.9.0-dev6)

El nuevo taller **no utiliza estaciones ni recetas de MMOItems**. SpectreCore gestiona la interfaz,
validación de nivel, inventario y economía. MMOItems solamente proporciona los objetos encantados
ya existentes, sin cambiar sus tipos ni IDs. AuraSkills es la fuente de verdad del nivel de Minería.

## Flujo del jugador

1. Al llegar a **Minería nivel 3 en AuraSkills**, se habilita el taller.
2. Habla con el **NPC Minero** o usa `/spectrecore minero`. El Compactador Portátil existente de Cygnus
   (`MATERIAL:COMPACTADOR`) también abre el taller con clic derecho.
3. Elige entre **Compactar** y **Pulverizar** y selecciona Cobre, Hierro, Oro, Lapislázuli,
   Redstone o Diamante.
4. Elige **1, 4 o todos los lotes** (máximo 8) y consulta el resultado exacto.
5. Confirma. Solo tras entregar realmente los materiales se anuncian el resultado, el
   sonido y las partículas. Las acciones se realizan sobre el inventario del jugador,
   nunca desde elementos del menú.

### Compactar

| 64 ingredientes normales | Recompensa MMOItems |
| --- | --- |
| 64 lingotes de cobre | `MATERIAL:COBRE` |
| 64 lingotes de hierro | `MATERIAL:HIERRO` |
| 64 lingotes de oro | `MATERIAL:ORO` |
| 64 lapislázuli | `MATERIAL:LAPIS` |
| 64 redstone | `MATERIAL:REDSTONE` |
| 64 diamantes | `MATERIAL:DIAMANTE` |

No se aceptan objetos renombrados o personalizados con el mismo material base.
Los objetos hechizados se crean con la integración real de MMOItems; si faltan,
no se consumen ingredientes ni se simula la entrega.

### Pulverizar

Consume 1 mineral hechizado auténtico (`MATERIAL:<ID>`) por lote y entrega
una cantidad **fija, conocida antes de confirmar** de monedas:

| Material | Monedas por unidad |
| --- | ---: |
| Cobre | 12 |
| Hierro | 30 |
| Oro | 55 |
| Lapislázuli | 40 |
| Redstone | 35 |
| Diamante | 135 |

Estos pagos se escogieron dentro de los rangos anteriormente usados por los comandos
de pulverización. Son valores iniciales de balance; se pueden cambiar sin modificar código.
La operación requiere Vault funcional; no se usa un comando de consola cuyo éxito no
pueda comprobarse.

## Conectar el NPC Minero

Configura la interacción pública del NPC existente para ejecutar **como jugador**
`/spectrecore minero`. No hace falta crear una estación de MMOItems, ni menús externos.

**El repositorio no contiene la definición del NPC Minero**. Por tanto, la conexión
de su botón de interacción debe hacerse en la configuración instalada de FancyNPCs.
Si el NPC aún abre `minero1`, quita aquella acción para evitar dos interfaces.

Los antiguos comandos administrativos `/spectrecore polvos <material>` ya no
entregan dinero: muestran el acceso al taller para cerrar el exploit de pagos
sin consumo de ítems. Desactiva cualquier referencia residual de la antigua
estación a esos comandos.

## Configuración y fiabilidad

- `modules/trabajos/config.yml`: nivel mínimo, receta 64:1, límite de lotes,
  materiales de entrada, IDs de recompensa, precios fijos.
- `ui/minero.yml`: iconos, organización, textos y colores MiniMessage.
- `modules/trabajos/mensajes.yml`: resultados y errores.
- `modules/trabajos/ajustes.yml`: sonidos y partículas.

**Seguridad:** en cada confirmación SpectreCore recalcula el nivel AuraSkills y
las existencias; ignora materiales en mano secundaria, armaduras y menús,
y opera solamente en el hilo principal. Antes de tocar el inventario crea un
registro duradero en el mismo `ClaimJournal` que los contratos y encargos.
Si el servidor cae entre retirar el material y entregar/confirmar la recompensa,
queda una operación pendiente para conciliación manual con
`/spectrecore trabajos transacciones` y
`/spectrecore trabajos resolver <uuid>`. Nunca se reembolsa o entrega
automáticamente cuando el resultado es ambiguo, para evitar duplicación.

**Pendiente de servidor:** comprobar la acción real del NPC, los IDs existentes
de `MATERIAL` en MMOItems, la lectura del nivel de AuraSkills, la economía
Vault y casos de desconexión y reinicio. La compilación y las pruebas estáticas
no sustituyen las pruebas dentro de Paper.
