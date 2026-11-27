# Cocina: instalación y reparación de consumibles antiguos

SpectreCore entrega las 45 variantes exclusivamente mediante el tipo personalizado
`COCINA`. Fusiona `ITEM_TYPE_COCINA.yml` con `item-types.yml` si falta el tipo;
instala `COCINA.yml` en `MMOItems/item/` conservando las personalizaciones e IDs.

Versiones anteriores distribuían los platos también en `CONSUMABLE`. Si durante
la carga de estas plantillas antiguas aparece `Config must not be null`, comprueba
el campo del efecto: MMOItems utiliza `amplifier`, no `level`. La corrección
conserva los objetos legados y no los añade al sistema de cocina.

Con el servidor detenido, el reparador requiere Python 3 y PyYAML. Primero revisa
los cambios, luego aplica si corresponden. Solo renombra esa clave en los IDs HC
conocidos, conserva el YAML restante y crea un respaldo. No elimina plantillas.

```bash
python3 reparar_cocina_legacy.py /ruta/plugins/MMOItems/item/consumable.yml
python3 reparar_cocina_legacy.py /ruta/plugins/MMOItems/item/consumable.yml --aplicar
```

Vuelve a arrancar y verifica la carga de ambas categorías. Si no detecta cambios,
inspecciona el YAML instalado y la versión de MMOItems; el mismo mensaje puede
tener otras causas.
