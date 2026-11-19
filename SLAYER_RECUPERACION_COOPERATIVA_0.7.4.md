# Slayer 0.7.4 · Recuperación de encuentros y crédito cooperativo

## Comportamiento acordado

- El contrato se paga una sola vez; las bajas invocan un único jefe por UUID.
- Al invocarse, SpectreCore escribe `slayer_contract` en el PersistentDataContainer
  de la entidad y guarda el UUID, mundo, última posición y daño del contratante
  en `slayer-data.yml`.
- **Una descarga de chunk no es una desaparición.** Paper `EntityRemoveEvent.Cause.UNLOAD`
  conserva el UUID: no se devuelve el contrato a estado invocable.
- Al regresar, `EntitiesLoadEvent` asocia nuevamente el mismo jefe. Un jefe
  con la marca SpectreCore que ya no pertenezca a un contrato se elimina como
  entidad antigua; esto cubre cancelaciones mientras el chunk estaba descargado.
- Una eliminación permanente confirmada (despawn, plugin removal, etc.),
  distinta de muerte o descarga, devuelve el contrato a **cuota completada**.
  El propietario puede reinvocar gratis con
  `/spectrecore slayer invocar revenant`; la experiencia permanece intacta.
- Desconectarse o morir **no cancela la cacería**. Al reconectar, si el jefe no
  está cargado, se informa su última posición conocida en lugar de generar otro.

## Crédito cooperativo

- El último golpe puede realizarlo cualquier jugador.
- El **contratante** debe causar, de forma directa o con sus proyectiles,
  al menos un porcentaje configurable de la **vida máxima** del jefe y
  permanecer conectado a no más de un radio configurable cuando muere.
- Valores iniciales: **10%** y **48 bloques**.
- Daño causado por otros, torretas, mascotas, efectos ajenos y esbirros
  no computa como daño del contratante.
- Solo el contratante recibe la XP Slayer (25/100/250 en Revenant I/II/III).
  El resto de jugadores no obtiene XP Slayer de ese contrato, sin afectar
  los drops configurados en MythicMobs/FancyDrops.
- No concede XP al invocar ni por kills vanilla. La victoria se registra una
  única vez en la muerte del jefe de UUID asignado.
- Si el contratante no cumple daño y presencia, el encuentro acaba sin XP
  Slayer; esto es una derrota de contrato, **no un error del servidor**.

Configuración editable:
```yaml
participacion:
  minimo-porcentaje-vida: 10
  radio-bloques: 48
```

## Integridad y límites

- El registro de daño se vacía periódicamente en `slayer-data.yml` y al
  salir el propietario. Al morir el jefe se guarda de forma síncrona y se
  reconcilian los permisos de LuckPerms solo después del guardado exitoso.
- No forzar la carga de chunks. Si un jefe consta registrado pero su chunk
  sigue descargado, **no se debe reinvocar** a ciegas.
- Un cierre abrupto antes de que el mundo guarde la entidad puede dejar un
  UUID huérfano: requerirá revisión administrativa. No se ha implementado
  una limpieza destructiva automática para evitar jefes duplicados.
- Los mobs anteriores a esta migración no tendrán la marca PDC hasta
  recargarse: se reconocen por el UUID registrado.
- Solo se evalúa la presencia en el momento de muerte; no se implementó
  reparto de XP entre los miembros del equipo.
- Continúa pendiente validar la ejecución real en Paper 1.21.10 y
  MythicMobs 5.13.0 Free. Compilar no prueba la lógica ingame.

## Pruebas en servidor de staging

1. Contratar Revenant I, matar 20 zombis vanilla, verificar un jefe y 0 XP inicial.
2. Contratante hace 160 de 1.600 de daño al Príncipe, compañero da último
   golpe, contratante a 20 bloques: +25 XP Slayer una vez.
3. Repetir con 159 de daño o propietario a más de 48 bloques: sin XP Slayer.
4. Golpes de compañero, mascota, torreta o esbirro: no aumentan daño del dueño.
5. Salir, morir o descargar el chunk durante el combate: contrato conservado,
   sin doble invocación, sin XP anticipada; al cargar, el mismo UUID.
6. Forzar una eliminación por plugin (NO muerte ni unload): el contrato conserva
   la cuota y permite reinvocar sin nuevo cobro.
7. Cancelar con un jefe descargado y después cargar ese chunk: no debe quedar
   otro jefe de ese contrato.
8. Reiniciar en pleno encuentro y regresar al lugar: comprobar última posición,
   UUID, daño acumulado y permisos de ganador.
9. Desactivar MythicMobs durante prueba: no contar mobs ajenos como cuota.
10. Probar FancyDrops sin duplicación de botín ni XP Slayer por admin spawns.
