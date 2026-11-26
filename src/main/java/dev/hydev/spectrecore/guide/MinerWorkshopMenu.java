package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.systems.JobsModule;
import dev.hydev.spectrecore.systems.MinerWorkshop;
import dev.hydev.spectrecore.systems.MinerWorkshopRules;
import dev.hydev.spectrecore.text.TextService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native, read-only preview and bounded two-mode player interaction. */
public final class MinerWorkshopMenu implements Listener {
    private static final List<String> CONTROLS = List.of("informacion", "compactar", "pulverizar",
            "vista-previa", "una", "cuatro", "maximo", "confirmar", "volver", "cerrar");
    private static final class Holder implements InventoryHolder {
        final MinerWorkshop.Mode mode;
        final String mineral;
        final int wanted;
        final Map<Integer,String> slots = new HashMap<>();
        Inventory inventory;
        Holder(MinerWorkshop.Mode mode, String mineral, int wanted) {
            this.mode=mode; this.mineral=mineral; this.wanted=wanted;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final JavaPlugin plugin;
    private final JobsModule jobs;
    private final MinerWorkshop workshop;
    private final MmoBridge mmo = new MmoBridge();
    private YamlConfiguration layout;
    private boolean listening;

    public MinerWorkshopMenu(JavaPlugin plugin, JobsModule jobs, MinerWorkshop workshop) {
        this.plugin = plugin; this.jobs=jobs; this.workshop=workshop;
    }
    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening=true;
    }
    public void reload() {
        File file = new File(plugin.getDataFolder(), "ui/minero.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/minero.yml", false);
            if (!file.isFile() || file.length()>32_768) throw new IOException("UI Minero no encontrada");
            var candidate = new YamlConfiguration();
            candidate.load(file);
            if (candidate.getInt("size") != 54 || !candidate.isString("title")
                    || candidate.getString("title","").isBlank()) throw new IOException("Cabecera inválida");
            Set<Integer> taken = new HashSet<>();
            for (String key : CONTROLS) validate(candidate, "items."+key, taken);
            for (String key : MinerWorkshop.KEYS) validate(candidate, "minerales."+key, taken);
            layout = candidate;
        } catch (Exception failure) {
            plugin.getLogger().warning("Taller Minero: diseño inválido, se conserva el último válido: " + failure.getMessage());
        }
    }
    private void validate(YamlConfiguration yaml, String base, Set<Integer> slots) throws IOException {
        int slot=yaml.getInt(base+".slot", -1);
        Material mat=Material.matchMaterial(yaml.getString(base+".material",""));
        if (slot<0 || slot>=54 || !slots.add(slot) || mat==null || !mat.isItem()
                || !yaml.isString(base+".nombre") || !yaml.isList(base+".lore")
                || yaml.getString(base+".nombre","").length()>256
                || yaml.getStringList(base+".lore").size()>12)
            throw new IOException("Entrada inválida: "+base);
        for(String line:yaml.getStringList(base+".lore"))
            if(line.length()>256)throw new IOException("Texto demasiado largo: "+base);
    }

    public boolean open(Player player) { return open(player, MinerWorkshop.Mode.COMPACTAR, "hierro", 1); }
    private Component styled(String template, Map<String,?> data) {
        return TextService.render(template,data).decoration(TextDecoration.ITALIC,false);
    }
    private ItemStack icon(String path, ItemStack base, Map<String,?> data) {
        ItemStack item=base.clone();
        ItemMeta meta=item.getItemMeta();
        if(meta==null)return item;
        meta.displayName(styled(layout.getString(path+".nombre"), data));
        List<Component> lore=new ArrayList<>();
        for(String line:layout.getStringList(path+".lore")) lore.add(styled(line,data));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean open(Player player, MinerWorkshop.Mode mode, String mineral, int wanted) {
        if (layout==null || !MinerWorkshop.KEYS.contains(mineral)) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.no-disponible",
                    "<red>El taller no puede procesar esta operación. Avisa a administración."));
            return false;
        }
        Holder holder=new Holder(mode,mineral,wanted);
        Inventory inventory=Bukkit.createInventory(holder,54,styled(layout.getString("title"),Map.of()));
        holder.inventory=inventory;
        int level=workshop.level(player), required=workshop.minimumLevel();
        boolean unlocked=workshop.availableFor(player);
        int have=workshop.available(player,mineral,mode);
        int perBatch=mode==MinerWorkshop.Mode.COMPACTAR?workshop.perBatch():1;
        int maximum=workshop.possible(player,mineral,mode);
        int amount=MinerWorkshopRules.selected(have,perBatch,workshop.cap(),wanted);
        long total=mode==MinerWorkshop.Mode.PULVERIZAR?Math.max(0,amount*workshop.payment(mineral)):amount;
        Map<String,Object> data=new HashMap<>();
        data.put("nivel",level<0?"—":level);
        data.put("requisito",required);
        data.put("modo",mode==MinerWorkshop.Mode.COMPACTAR?"Compactar":"Pulverizar");
        data.put("mineral",workshop.display(mineral));
        data.put("necesarios",workshop.perBatch());
        data.put("disponibles",have);
        data.put("lotes",amount);
        data.put("maximo",maximum);
        data.put("entrada",(long)amount*perBatch);
        data.put("salida",total);
        data.put("monedas",Math.max(0,amount*workshop.payment(mineral)));
        String state=unlocked?(amount>0?"<green>Listo para transformar":"<yellow>Faltan materiales")
                :level<0?"<red>Progreso no disponible":"<yellow>Minería "+required+" requerida";
        data.put("estado",TextService.component(state));
        for(String control:CONTROLS) {
            String path="items."+control;
            Material mat=Material.matchMaterial(layout.getString(path+".material","BOOK"));
            ItemStack base=new ItemStack(mat==null?Material.BOOK:mat);
            if(control.equals("vista-previa") && mode==MinerWorkshop.Mode.COMPACTAR) {
                ItemStack enchanted=mmo.build("MATERIAL",workshop.id(mineral),1);
                if(enchanted!=null)base=enchanted;
            }
            if(control.equals("vista-previa") && mode==MinerWorkshop.Mode.PULVERIZAR)
                base=new ItemStack(Material.GOLD_NUGGET);
            inventory.setItem(layout.getInt(path+".slot"),icon(path,base,data));
            holder.slots.put(layout.getInt(path+".slot"),control);
        }
        for(String key:MinerWorkshop.KEYS) {
            String path="minerales."+key;
            int count=workshop.available(player,key,mode);
            data.put("material",workshop.display(key));
            data.put("cantidad",count);
            data.put("seleccionado",TextService.component(key.equals(mineral)
                    ? "<green>Seleccionado" : "<yellow>Clic para seleccionar"));
            Material fallback=Material.matchMaterial(layout.getString(path+".material","STONE"));
            ItemStack base=new ItemStack(fallback==null?Material.STONE:fallback);
            if(mode==MinerWorkshop.Mode.PULVERIZAR) {
                ItemStack enchanted=mmo.build("MATERIAL",workshop.id(key),1);
                if(enchanted!=null)base=enchanted;
            }
            inventory.setItem(layout.getInt(path+".slot"),icon(path,base,data));
            holder.slots.put(layout.getInt(path+".slot"),"mineral:"+key);
        }
        player.openInventory(inventory);
        return true;
    }

    /** Existing Cygnus Compactador Portátil opens the native workshop, not the old station. */
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void usePortable(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND
                || (event.getAction()!=Action.RIGHT_CLICK_AIR && event.getAction()!=Action.RIGHT_CLICK_BLOCK))
            return;
        ItemStack held=event.getPlayer().getInventory().getItemInMainHand();
        if(held==null || held.getType().isAir() || !held.hasItemMeta())return;
        if(!"MATERIAL".equalsIgnoreCase(mmo.type(held))
                || !"COMPACTADOR".equalsIgnoreCase(mmo.id(held)))return;
        event.setCancelled(true);
        Player player=event.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(plugin.isEnabled() && player.isOnline())open(player);
        });
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true); // Blocks shift clicks, number keys, offhand swaps and all item moves.
        if(!(event.getWhoClicked() instanceof Player player))return;
        int slot=event.getRawSlot();
        if(slot<0||slot>=54)return;
        String choice=holder.slots.get(slot);
        if(choice==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!plugin.isEnabled()||!player.isOnline()
                    ||player.getOpenInventory().getTopInventory().getHolder()!=holder)return;
            if(choice.equals("cerrar")){player.closeInventory();return;}
            if(choice.equals("volver")){jobs.openOverview(player);return;}
            if(choice.equals("confirmar")){
                if(workshop.process(player,holder.mineral,holder.mode,holder.wanted))
                    open(player,holder.mode,holder.mineral,holder.wanted);
                return;
            }
            if(choice.equals("compactar"))open(player,MinerWorkshop.Mode.COMPACTAR,holder.mineral,holder.wanted);
            else if(choice.equals("pulverizar"))open(player,MinerWorkshop.Mode.PULVERIZAR,holder.mineral,holder.wanted);
            else if(choice.equals("una"))open(player,holder.mode,holder.mineral,1);
            else if(choice.equals("cuatro"))open(player,holder.mode,holder.mineral,4);
            else if(choice.equals("maximo"))open(player,holder.mode,holder.mineral,-1);
            else if(choice.startsWith("mineral:"))
                open(player,holder.mode,choice.substring("mineral:".length()),holder.wanted);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder))return;
        for(int slot:event.getRawSlots())if(slot<54){event.setCancelled(true);return;}
    }
    public void disable() {
        if(listening)HandlerList.unregisterAll(this);
        listening=false;
        for(Player player:Bukkit.getOnlinePlayers())
            if(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)player.closeInventory();
    }
}
