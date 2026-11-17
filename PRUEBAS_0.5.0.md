# Prueba ingame de SpectreCore 0.5.0-alpha

1. Desactiva los Skripts migrados, sustituye el JAR y arranca. Confirma importación de config, /spectrecore modulos, /armaduras y /cocina ayuda.
2. Como OP, ejecuta /spectrecore mineria tesoro 1, 2 y 3, y /spectrecore mineria gema basica, intermedia y superior. Observa rotación, altura, fragmentos, partículas y desaparición. Ninguna demo debe cambiar inventario, carga ni botín.
3. /debugresonancia sosteniendo el Pico Perdido debe mostrar herramienta válida. Prueba un pico incorrecto: no carga. En supervivencia rompe minerales naturales T1/T2/T3: sube 1/2/4. Tesoros debe seguir funcionando independientemente de llevar el pico.
4. Para probar rápidamente, en una copia del servidor usa temporalmente gemas.carga-maxima: 4, gemas.ventana-segundos: 10 y 100% en un tier de tesoro. Configura IDs reales y recarga con /spectrecore reload. No cambies probabilidades del servidor principal solo para ver la animación: usa las demos.
5. Completa la carga. Minar sin Shift no extrae. Minar con Shift otro mineral extrae una gema, reinicia carga y muestra el modelo. Una configuración de gema inválida no consume la carga ni entrega un sustituto vanilla; el reloj sigue contando.
6. Espera a que venza y usa /resonancia. Desconecta y reconecta durante la ventana; no debe prolongarse. Reinicia y comprueba carga y vencimiento persistidos.
7. Coloca minerales de cada tier, reinicia y rómpelos: no deben dar resonancia ni tesoros. Repite con pistón normal y pegajoso, en extensión y retracción. Prueba con una región protegida: los eventos cancelados no dan premios ni consumen marcas.
8. Concede explícitamente tesoros.mineria.tier1/2/3 al jugador de prueba. A 100% de probabilidad, una rotura natural válida entrega 2-8 PALADIO. Varias roturas rápidas no entregan otro tesoro dentro de los 5 s. Prueba inventario lleno.
9. Durante varias animaciones, aléjate, descarga el chunk y reinicia. No deben quedar displays, afectar TPS ni perderse premios que ya se entregaron. Prueba el límite de animaciones: los premios siguen funcionando.
10. En un mob de prueba con mucha vida, aplica cada scoreboard tag del controlador y verifica multiplicadores y combinaciones. HYDEV_SPECTRE_ETEREO impide todo daño. Quita los tags al terminar.
11. Con HYDEV_CABA_CANAL + HYDEV_CABA_RESET, acumula menos de 200 HP finales: sin BREAK. Llega a 200: aparece HYDEV_CABA_BREAK. Daño cancelado no suma. Retira y vuelve a aplicar RESET para iniciar otra canalización; RESET debe desaparecer al procesar el siguiente daño válido.
12. Con un Minotauro de 15.000 HP, LOCK70 frena en 10.500 HP y LOCK35 en 5.250 HP, incluso con un golpe letal. Prueba también explosiones. Comprueba que las skills existentes detectan el umbral y retiran su lock; el módulo no cambia las skills de MythicMobs.
13. Prueba /nombreitem con nombres que contengan comillas y caracteres especiales: copiar debe funcionar sin ejecutar comandos. Un jugador sin permiso no puede usar diagnósticos/demos.
14. Verifica otra vez cocina, habilidades y las recetas. Restaura carga, ventana y probabilidades reales tras las pruebas.
