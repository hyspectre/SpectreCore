# Plan de pruebas en Paper 1.21.10 — SpectreCore 0.4.0-alpha

Antes de producción: respaldar `plugins/`, probar sin los plugins o scripts de cocina antiguos y con MMOItems instalado (los doce IDs de platos deben existir). El objetivo de este documento es especificar las pruebas pendientes, **no certificar que se han realizado**.

1. **Integración**: arranca Paper 1.21.10 con Java 21; `/spectrecore modulos`, `/cocina ayuda`, `/cocina libro` deben responder. Comprueba que las cinco habilidades funcionen igual que en v0.3.1.
2. **Creación**: agua en caldero sobre fogata encendida, clic con `FLINT_AND_STEEL`. Comprueba que aparece el marco, texto y líquido azul animado; el mechero no pierde durabilidad. No permitir duplicar registro.
3. **Líquido / ingredientes**: inicia Guiso Guardián; observa color verde y representación flotante de patata, carne y seta conforme se aportan. Prueba recarga de chunk, `/cocina reparar`, recarga de plugin y reinicio de Paper: no aparecen displays duplicados ni residuos.
4. **Nivel 1**: caldero con agua nivel 1; tira una unidad de cada ingrediente, revuelve con pala de madera y sirve con 1 cuenco. Se obtiene exactamente 1 plato.
5. **Nivel 2**: caldero nivel 2; tira una pila de 64 patatas: deben consumirse 2 y devolverse 62 al inventario del jugador (o a sus pies). Repite con 2 carnes y 2 setas. Durante el servicio usa 2 cuencos: obtienes 2 platos y no más.
6. **Nivel 3**: prepara 3 de cada ingrediente en orden; no aceptar el siguiente tipo antes de completar la cuota actual. Tras servir 1 plato, apaga la fogata y reinicia servidor: deben quedar 2 raciones listas de la misma calidad, sin continuar quemándose. Sirve ambas.
7. **Cancelación**: con materiales añadidos pero sin platos servidos, retira con esponja y verifica devolución correcta (sin más de los materiales consumidos). Repite retirando después del primer plato de una tanda: exige Shift y no devuelve materiales ni entrega cuencos gratis.
8. **Quemados**: deja la mezcla después de `quemado` antes del primer servicio: no admite cuencos; la esponja la limpia (sin desinstalar estación). Comprueba que no se limpia automáticamente.
9. **Precisión**: revuelve en medio de la ventana y sirve antes de `listo-hasta` para exquisito. Se debe ver título dorado, destellos y sonido especial solo al servir primer plato. Sin revolver, debe entregar calidad normal; tarde, calidad pasada.
10. **Libro**: sin haber cocinado, `/cocina libro` muestra cuatro siluetas y pistas. Completa una receta: se desbloquea solo para el jugador que sirvió. Otro jugador mantiene su libro independiente. Tras reinicio se conserva en `discoveries.yml`. Abrirlo con BOOK sobre caldero, comprobar que no es posible introducir/sacar ítems del inventario del libro.
11. **Inspección**: mano principal vacía sobre el caldero: el chat debe mostrar progreso y acción requerida en un único aviso; Shift+clic debe mostrar el mismo estado; `/cocina estado` debe coincidir.
12. **MMOItems faltantes y seguridad**: deshabilita temporalmente MMOItems en un servidor de pruebas; la recompensa debe fallar sin consumir cuenco ni reiniciar el lote. Prueba ingredientes personalizados con metadata; no deben consumirse.
13. **Mundos, regiones y rendimiento**: cargar/descargar chunks y mundos; proteger zonas con tus plugins de claims; vigilar TPS y recuento de displays con varias estaciones simultáneas. Confirmar que no crean displays en chunks descargados.

## Asterión · Furia e Ímpetu (dev09)

- Con el conjunto completo, golpea o recibe ataques hostiles: la barra de Furia indica progreso y disponibilidad real (sin actionbar).
- Con Furia completa y sin recarga, pulsa Shift dos veces: la barra cambia a Ímpetu y muestra el tiempo restante. El primer golpe fija al enemigo; los siguientes acumulan cargas y hasta +12% de daño con la configuración por defecto.
- Cambia de enemigo o deja pasar más de 2,5 s entre golpes: verifica que la cadena se reinicia. Al terminar, comprueba que la barra vuelve a Furia/recarga y se oculta al quitar el conjunto, morir o desconectarte.
- Comprueba que la Linterna y el portal muestran su estado temporal en barras nativas, y que cocina, geodas y trabajos no escriben en la actionbar.

## Indicadores de armaduras y herramientas (dev09)

- Botania: golpea tres veces y espera a que expire la ventana; vuelve a encadenar los cuatro golpes y comprueba que se muestran las rosas activas, no una carga simultánea.
- Corona de Ánimas: recibe un ataque hostil; observa disparos restantes y espera con y sin enemigos cercanos. Quita una pieza para limpiar la barra.
- Último Juramento: activa con doble Shift, golpea para prolongar y usa la espada afín para comprobar el Eco pendiente, su consumo y expiración.
- Sangre del Primer Dragón: activa con golpes o daño recibido; compara duración con y sin Dracoreliquia, incluso al cambiar de arma. La animación puede terminar antes de la bendición.
- Golpea un enemigo con un multiplicador directo y verifica que solo el atacante ve el float; un golpe normal o cancelado no produce ese indicador. Los golpes rápidos no apilan floats.
- Usa una herramienta de Mitrilo sin carga, en recarga y sin un sendero guardado; comprueba cada aviso y la consulta de materiales en El Mago.


## Linterna de Mitrilo (dev09)
- Minería 25: clic derecho con la Linterna en mano principal da Visión nocturna (60 s); repetir renueva sin reemplazar un efecto mayor.
- Agáchate + clic derecho: verifica contornos de menas tras roca durante 3 s, visibles solo para quien activa, sin cambiar bloques. Otro jugador no debe verlos.
- Comprueba radio 10, máximo 24 menas, recarga 140 s incluso al cambiar de Linterna, relog o pulso sin menas.
- Menas colocadas o protegidas no se revelan; sin WorldGuard se rechaza el pulso. Al salir, morir, teletransportarse o recargar, se limpian los contornos.
- Bobina de Cobre: +4 bloques de alcance, duración y recarga intactas. Los Faroles existentes adquieren la nueva habilidad con su ID original.
