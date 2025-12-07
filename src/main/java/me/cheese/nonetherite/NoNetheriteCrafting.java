package me.cheese.nonetherite;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NoNetheriteCrafting extends JavaPlugin implements Listener {

    private static final String BYPASS = "nonetherite.bypass";
    private static final String META_NOTIFY = "nnc_notify";

    private final Set<Material> bannedUse = new HashSet<>();

    private File maceDataFile;
    private boolean maceCraftedOnce = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadBannedUseItems();
        loadMaceFlag();

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("NoNetheriteCrafting enabled — Netherite gear & trim blocked, custom recipes allowed, mace limit active.");
    }

    // ============================================================
    // INIT
    // ============================================================

    private void loadBannedUseItems() {
        var list = getConfig().getStringList("banned-use");

        if (list.isEmpty()) {
            // Default block list: Netherite gear + Netherite trim
            bannedUse.add(Material.NETHERITE_SWORD);
            bannedUse.add(Material.NETHERITE_PICKAXE);
            bannedUse.add(Material.NETHERITE_AXE);
            bannedUse.add(Material.NETHERITE_SHOVEL);
            bannedUse.add(Material.NETHERITE_HOE);
            bannedUse.add(Material.NETHERITE_HELMET);
            bannedUse.add(Material.NETHERITE_CHESTPLATE);
            bannedUse.add(Material.NETHERITE_LEGGINGS);
            bannedUse.add(Material.NETHERITE_BOOTS);

            // NEW — Block Netherite Upgrade Smithing Template (trim)
            bannedUse.add(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            return;
        }

        for (String s : list) {
            Material m = Material.matchMaterial(s);
            if (m == null) {
                getLogger().warning("Invalid material in banned-use: " + s);
                continue;
            }

            // Ingots & scrap are NEVER blocked
            if (m == Material.NETHERITE_INGOT || m == Material.NETHERITE_SCRAP) {
                getLogger().warning("Ignoring attempt to ban Netherite Ingot or Scrap.");
                continue;
            }

            bannedUse.add(m);
        }
    }

    private void loadMaceFlag() {
        File folder = getDataFolder();

        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                getLogger().warning("Failed to create plugin folder!");
            }
        }

        maceDataFile = new File(folder, "mace-crafted.txt");

        if (maceDataFile.exists()) {
            maceCraftedOnce = true;
        }
    }

    // ============================================================
    // CRAFTING LOGIC
    // ============================================================

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        Player p = (Player) event.getView().getPlayer();
        if (p.hasPermission(BYPASS)) return;

        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null) return;

        Material mat = result.getType();

        // ------------------------------------------------------------
        // ALLOW ALL NON-VANILLA RECIPES (plugins, datapacks)
        // ------------------------------------------------------------
        if (event.getRecipe() instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (!key.getNamespace().equalsIgnoreCase("minecraft")) {
                return; // allow custom recipes always
            }
        }

        // ------------------------------------------------------------
        // ONE MACE
        // ------------------------------------------------------------
        if (mat == Material.MACE) {

            if (maceCraftedOnce) {
                inv.setResult(null);
                sendLimitedMessage(p, "§cOnly ONE mace can be crafted on this server.");
                return;
            }

            Bukkit.getScheduler().runTask(this, this::saveMaceFlag);
            return;
        }

        // ------------------------------------------------------------
        // BLOCK NETHERITE GEAR + NETHERITE TRIM TEMPLATE
        // ------------------------------------------------------------
        if (isNetheriteGear(mat) || mat == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
            inv.setResult(null);
            sendLimitedMessage(p, "§cCrafting Netherite items is disabled.");
        }
    }

    private void saveMaceFlag() {

        if (maceCraftedOnce) return;
        maceCraftedOnce = true;

        try {
            boolean created = maceDataFile.createNewFile();
            if (!created) {
                getLogger().warning("mace-crafted.txt already existed.");
            }
        } catch (IOException e) {
            getLogger().warning("Failed to write mace-crafted.txt: " + e.getMessage());
        }
    }

    // ============================================================
    // SMITHING (block netherite trim + netherite upgrades)
    // ============================================================

    @EventHandler
    public void onSmith(PrepareSmithingEvent event) {
        Player p = (Player) event.getView().getPlayer();
        if (p.hasPermission(BYPASS)) return;

        ItemStack result = event.getResult();
        if (result == null) return;

        Material mat = result.getType();

        if (isNetheriteGear(mat) ||
                mat == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {

            event.setResult(null);
            sendLimitedMessage(p, "§cNetherite upgrades and trims are disabled.");
        }
    }

    // ============================================================
    // USE / EQUIP BLOCKING
    // ============================================================

    @EventHandler
    public void onHold(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission(BYPASS)) return;

        ItemStack item = p.getInventory().getItem(event.getNewSlot());
        if (item == null) return;

        if (bannedUse.contains(item.getType())) {
            p.getInventory().setItem(event.getNewSlot(), null);
            sendLimitedMessage(p, "§cYou cannot use Netherite items.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission(BYPASS)) return;

        ItemStack item = event.getItem(); // MAY be null
        if (item == null) return;

        if (bannedUse.contains(item.getType())) {
            event.setCancelled(true);
            sendLimitedMessage(p, "§cYou cannot use Netherite items.");
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (p.hasPermission(BYPASS)) return;

        ItemStack item = p.getInventory().getItemInMainHand(); // MAY be null

        if (bannedUse.contains(item.getType())) {
            event.setCancelled(true);
            sendLimitedMessage(p, "§cYou cannot attack with Netherite items.");
        }
    }

    @EventHandler
    public void onArmorEquip(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (p.hasPermission(BYPASS)) return;

        ItemStack cursor = event.getCursor(); // NEVER null

        if (cursor.getType() != Material.AIR &&
                bannedUse.contains(cursor.getType())) {

            event.setCancelled(true);
            sendLimitedMessage(p, "§cYou cannot equip Netherite items.");
        }
    }

    @EventHandler
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission(BYPASS)) return;

        ItemStack item = event.getPlayerItem(); // NEVER null

        if (item.getType() != Material.AIR &&
                bannedUse.contains(item.getType())) {

            event.setCancelled(true);
            sendLimitedMessage(p, "§cYou cannot use Netherite items.");
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean isNetheriteGear(Material m) {
        return switch (m) {
            case NETHERITE_SWORD,
                 NETHERITE_PICKAXE,
                 NETHERITE_AXE,
                 NETHERITE_SHOVEL,
                 NETHERITE_HOE,
                 NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS -> true;
            default -> false;
        };
    }

    private void sendLimitedMessage(Player p, String msg) {
        if (p.hasMetadata(META_NOTIFY)) return;

        p.sendMessage(msg);
        p.setMetadata(META_NOTIFY, new FixedMetadataValue(this, true));

        Bukkit.getScheduler().runTaskLater(
                this,
                () -> p.removeMetadata(META_NOTIFY, this),
                10L
        );
    }
}
