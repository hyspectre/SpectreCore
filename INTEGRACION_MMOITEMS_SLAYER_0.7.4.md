# Slayer · Permisos de recetas en la Forja

## Regla funcional (Hydev)

La progresión de Monarquía Renacida **no restringe equipar, comerciar, sostener,
ni usar armas o armaduras**. Al alcanzar un nivel, SpectreCore **concede permisos
de progresión** mediante LuckPerms. La futura interfaz de la **Forja** consultará
estos permisos para permitir **fabricar** las recetas correspondientes.

No añadir restricciones de uso a `plugins/MMOItems/item/*.yml`, ni
`base.permission` a los objetos. El antiguo instalador de restricciones se
retiró de esta rama, junto con la auditoría de equipamiento.

## Contrato con la Forja

Se mantienen los nodos existentes, para no invalidar permisos ya concedidos.
A pesar de contener `item` en su identificador, su **única finalidad desde
ahora es autorizar una receta**:

| Nivel Slayer | Nodos otorgados |
|---|---|
| 1 | `hydev.slayer.item.sword.filopodrido` |
| 2 | `hydev.slayer.item.armor.zombiearmor_1`, `zombiearmor_2` (con prefijo completo) |
| 3 | `hydev.slayer.item.armor.zombiearmor_3`, `zombiearmor_4` y acceso al Liche I futuro |
| 5 | `hydev.slayer.item.sword.colmillo_putrefacto` y Revenant II |
| 6 | `hydev.slayer.item.armor.renacido_1`, `renacido_2` |
| 7 | `hydev.slayer.item.armor.renacido_3`, `renacido_4` |
| 8 | `hydev.slayer.item.sword.sable_necrotico` |
| 9 | `hydev.slayer.item.armor.necrotico_1`, `necrotico_2` |
| 10 | `hydev.slayer.item.armor.necrotico_3`, `necrotico_4` y Revenant III |
| 11 | `hydev.slayer.item.sword.necroespada` |

La tabla resume 16 permisos de objeto, más permisos de contratos. La autoridad
exacta es `SlayerProgressionRules.earnedNodes(level)`.

## Pendiente de integrar

Aún **no existe en esta rama la comprobación de esos nodos dentro de una Forja
propia**. Tener el nodo asignado **no desbloquea automáticamente** una receta
en otro menú que no conozca ese requisito. Cuando se implemente la Forja,
cada receta deberá consultar el nodo correspondiente antes de permitir el
crafteo; el objeto resultante debe poder usarse incluso sin ese nodo.

## Pruebas

1. Confirmar que subir el nivel concede los nodos en LuckPerms.
2. Confirmar que el objeto puede equiparse y usarse aunque el jugador no tenga
   la receta desbloqueada.
3. Confirmar la persistencia del nodo tras reiniciar.
4. Cuando exista la Forja integrada, comprobar que la receta aparece/desaparece
   según el permiso y que la fabricación queda protegida en servidor.

**Si aplicaste el instalador antiguo a `SWORD.yml` o `ARMOR.yml`, restaura
los respaldos hechos antes de aquella instalación** y comprueba que no hay
requisitos `base.permission` añadidos por Slayer. El cambio en GitHub no
revierte modificaciones locales del servidor.
