# SpectreCore 0.7.4 — Migración nativa de contratos de Trabajos

**Estado:** implementado en rama experimental; sin cambios en el servidor activo. Requiere ventana de mantenimiento con nuevo JAR. Los menús zMenu recibidos se usaron exclusivamente como referencia de diseño, estructura, cabezas y precios.

## Menús Paper, sin zMenu

Abrir con:
- `/spectrecore trabajos` — índice principal con Minería, Agricultura y Caza.
- `/spectrecore trabajos mineria` — Minero.
- `/spectrecore trabajos agricultura` — Granjero.
- `/spectrecore trabajos caza` — Cazador.
- `/spectrecore trabajos estado` — conserva el estado anterior en chat.

Se implementaron **4 inventarios nativos** de 54 slots con cabezas originales Base64 (9 niveles + guías y navegación), mismas posiciones de los contratos (19/22/25), información en slot 49, volver slot 48 y cerrar slot 50. Los ítems y sus colores/títulos/lore se configuran en `plugins/SpectreCore/ui/trabajos.yml`. La navegación usa eventos Paper con clic y arrastre cancelados y transiciones diferidas al siguiente tick.

Ningún elemento del YAML de presentación puede ejecutar un comando: la asociación entre botón y acción está codificada en Java. `/spectrecore reload` actualiza el YAML después de instalar el JAR.

## Fuente de verdad de los datos

**Pagos, tiempos y precios:** `plugins/SpectreCore/modules/trabajos/config.yml`.
**Lista de materiales/entidades de cada rango:** `plugins/SpectreCore/modules/tesoros/config.yml`, bajo `clasificacion.trabajos-mineria`, `clasificacion.agricultura`, `clasificacion.combate`.
**Traducciones de nombres solamente:** `plugins/SpectreCore/ui/trabajos.yml` → `material-names`. Nunca cambia qué actividad paga; cambiar la clasificación del módulo sí.

| Trabajo | Novato | Experimentado | Profesional |
|---|---|---|---|
| Minero | 1–4 ↾, 10 s | 4–6 ↾, 7 s | 8–12 ↾, 4 s |
| Granjero | 1–3 ↾, 8 s | 3–6 ↾, 5 s | 6–10 ↾, 3 s |
| Cazador | 2–6 ↾, 12 s | 6–12 ↾, 8 s | 12–24 ↾, 5 s |

Contratos por rango (todos los oficios): **Novato 50 ↾, Experimentado 1.500 ↾, Profesional 3.500 ↾**. Los nueve valores provienen exactamente de los datos suministrados por el usuario.

**Semántica importante del código de pagos:** la clasificación del mineral/cultivo/mob determina qué nivel y tarifa de recompensa se usa. Tener el rango Profesional **autoriza** todos los niveles inferiores, pero no convierte automáticamente cada carbón en una recompensa profesional; el pago de carbón permanece en la tabla Novato. El cooldown se comparte por profesión, por jugador, aunque la duración concreta depende del nivel de la actividad remunerada.

## Contratación sin operadores ni comandos de consola

La acción de botón va a `JobsModule.purchase(player, job, rank)`, que:
1. Comprueba profesión válida, rango válido, `contratos.habilitados`, módulo activo, ausencia de compra pendiente, rango anterior y que no esté comprado.
2. Comprueba precio íntegro y Vault + LuckPerms disponibles. Si falta cualquiera, **no cobra**.
3. Retira dinero mediante Vault con confirmación, sin comandos `eco take` / zMenu.
4. Añade y guarda `trabajo.<oficio>.<rango>` mediante API **LuckPerms**; no se usa `lp user ...` por consola ni permisos OP temporales.
5. Si guardar falla, intenta revertir el nodo y devolver el dinero. Registra el UUID/importe en consola si falta compensación. Bloquea clics duplicados mientras la compra está en proceso.

**Limitación contable:** Vault y LuckPerms no comparten transacción ACID. Un crash justo después del débito y antes de que el permiso se guarde puede requerir reconciliación administrativa. Un CI exitoso NO demuestra robustez ante apagones; guardar backup de LuckPerms y economía y revisar la consola antes de abrir al público. Para garantía estricta haría falta un journal persistente de transacciones.

## Preparación antes del único reinicio de mantenimiento

1. Respaldar JAR, `plugins/SpectreCore/`, configuración de zMenu y almacenamiento de Vault/LuckPerms.
2. **Retirar o deshabilitar los menús zMenu antiguos** de Minería, Agricultura y Caza o desactivar sus botones, incluidos NPCs y accesos remotos. No dejar el antiguo flujo `withdraw` + `lp user...` accesible a jugadores mientras funcione el nativo.
3. Instalar un único JAR acumulativo de SpectreCore y reiniciar durante la ventana pactada. `/spectrecore reload` no sustituye un JAR Java.
4. Abrir los 4 inventarios y comprobar skins, títulos, colores, datos, volver/cerrar, que no se puedan extraer los ítems y que los rangos superiores aparezcan bloqueados.
5. Probar contratos con balance insuficiente, LuckPerms desactivado, permiso ya otorgado, compra doble, rango saltado y compra correcta. Confirmar que el saldo baja **una sola vez** y que la nueva permission node persiste tras reconexión y reinicio.
6. Cambiar una cifra en `modules/trabajos/config.yml` y ejecutar `/spectrecore reload`; verificar que el menú muestra la cifra nueva. Cambiar clasificación de un material y comprobar que cambia la lista del lore.
7. Comprobar que los antiguos pagos automáticos de Trabajos siguen obedeciendo el cooldown por actividad y no pagan por bloques colocados por jugadores.
8. Revisar consola tras fallos, en particular `LUCKPERMS SAVE FAILED`, `REEMBOLSO DE TRABAJO FALLIDO` y `TRABAJO PENDIENTE DE RECONCILIACION`.

## QA automático

- `tests/validate_native_jobs.py`: 4 inventarios, 9 texturas, 9 tablas exactas, listado por clasificación, acciones sin comandos.
- `tests/JobsContractSmoke.java`: progresión en 3 oficios, prohibición de saltos, compras repetidas, compras concurrentes y precios inválidos.
- `tests/validate_release.py`: 9 salarios y 9 cooldowns.
- CI GitHub Actions: Java 21, Gradle, YAML, regresiones y empaquetado.
