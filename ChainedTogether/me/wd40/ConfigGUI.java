package me.wd40;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class ConfigGUI implements Listener, CommandExecutor {
   private final chainTogether plugin;
   private final ChainManager chainManager;
   private static final String GUI_TITLE = "§6⛓ Chain Config ⛓";
   private static final List<String> PARTICLE_TYPES = Arrays.asList("CRIT", "FLAME", "HEART", "HAPPY_VILLAGER", "SMOKE", "WITCH", "DUST", "SNOWFLAKE", "END_ROD", "SOUL_FIRE_FLAME");

   public ConfigGUI(chainTogether plugin, ChainManager chainManager) {
      this.plugin = plugin;
      this.chainManager = chainManager;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("§c⛓ Only players can use this command!");
         return true;
      } else if (!player.hasPermission("chaintogether.admin")) {
         player.sendMessage("§c⛓ You don't have permission to use admin commands!");
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         return true;
      } else {
         this.openConfigGUI(player);
         return true;
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      HumanEntity whoClicked = event.getWhoClicked();
      if (whoClicked instanceof Player player) {
         if (event.getView().getTitle().equals("§6⛓ Chain Config ⛓")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
               int slot = event.getSlot();
               switch (slot) {
                  case 1:
                     this.adjustDouble("max-chain-distance", (double)-1.0F, (double)1.0F, (double)50.0F);
                  case 2:
                  case 4:
                  case 6:
                  case 7:
                  case 8:
                  case 9:
                  case 11:
                  case 13:
                  case 15:
                  case 16:
                  case 17:
                  case 18:
                  case 20:
                  case 22:
                  case 24:
                  case 25:
                  case 26:
                  case 27:
                  case 28:
                  case 29:
                  case 30:
                  case 31:
                  case 33:
                  case 34:
                  case 35:
                  case 36:
                  case 37:
                  case 38:
                  case 39:
                  case 40:
                  case 41:
                  case 42:
                  case 43:
                  case 44:
                  case 46:
                  case 47:
                  case 48:
                  default:
                     break;
                  case 3:
                     this.adjustDouble("max-chain-distance", (double)1.0F, (double)1.0F, (double)50.0F);
                     break;
                  case 5:
                     this.toggleBoolean("chained-players-pvp");
                     break;
                  case 10:
                     this.adjustDouble("pull-strength", -0.1, 0.1, (double)2.0F);
                     break;
                  case 12:
                     this.adjustDouble("pull-strength", 0.1, 0.1, (double)2.0F);
                     break;
                  case 14:
                     this.toggleBoolean("group-death");
                     break;
                  case 19:
                     this.adjustInt("chain-limit", -1, 2, 50);
                     break;
                  case 21:
                     this.adjustInt("chain-limit", 1, 2, 50);
                     break;
                  case 23:
                     this.toggleBoolean("use-particles");
                     break;
                  case 32:
                     this.cycleParticleType();
                     break;
                  case 45:
                     this.plugin.reloadConfig();
                     player.sendMessage("§c⛓ Changes discarded.");
                     player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5F, 1.0F);
                     player.closeInventory();
                     return;
                  case 49:
                     this.plugin.saveConfig();
                     this.chainManager.reloadConfig();
                     player.sendMessage("§a⛓ Configuration saved!");
                     player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.5F);
                     player.closeInventory();
                     return;
               }

               player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.0F);
               this.openConfigGUI(player);
            }
         }
      }
   }

   private void adjustDouble(String path, double amount, double min, double max) {
      double current = this.plugin.getConfig().getDouble(path);
      double newValue = Math.max(min, Math.min(max, current + amount));
      newValue = (double)Math.round(newValue * (double)10.0F) / (double)10.0F;
      this.plugin.getConfig().set(path, newValue);
   }

   private void adjustInt(String path, int amount, int min, int max) {
      int current = this.plugin.getConfig().getInt(path);
      int newValue = Math.max(min, Math.min(max, current + amount));
      this.plugin.getConfig().set(path, newValue);
   }

   private void toggleBoolean(String path) {
      boolean current = this.plugin.getConfig().getBoolean(path);
      this.plugin.getConfig().set(path, !current);
   }

   private void cycleParticleType() {
      String current = this.plugin.getConfig().getString("particle-type", "CRIT");
      int index = PARTICLE_TYPES.indexOf(current);
      int nextIndex = (index + 1) % PARTICLE_TYPES.size();
      this.plugin.getConfig().set("particle-type", PARTICLE_TYPES.get(nextIndex));
   }

   private void openConfigGUI(Player player) {
      Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, "§6⛓ Chain Config ⛓");
      ItemStack filler = this.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

      for(int i = 0; i < 54; ++i) {
         inv.setItem(i, filler);
      }

      double maxDistance = this.plugin.getConfig().getDouble("max-chain-distance", (double)5.0F);
      inv.setItem(1, this.createItem(Material.RED_DYE, "§c- Decrease", "§7Click to decrease by 1"));
      inv.setItem(2, this.createItem(Material.CHAIN, "§e§lMax Chain Distance", "§7Current: §b" + maxDistance + " blocks", "", "§7How far players can be apart", "§7before being pulled together"));
      inv.setItem(3, this.createItem(Material.LIME_DYE, "§a+ Increase", "§7Click to increase by 1"));
      boolean pvp = this.plugin.getConfig().getBoolean("chained-players-pvp", true);
      inv.setItem(5, this.createToggleItem("§e§lChained Players PvP", pvp, "§7Allow chained players to", "§7attack each other"));
      double pullStrength = this.plugin.getConfig().getDouble("pull-strength", 0.3);
      inv.setItem(10, this.createItem(Material.RED_DYE, "§c- Decrease", "§7Click to decrease by 0.1"));
      inv.setItem(11, this.createItem(Material.SLIME_BALL, "§e§lPull Strength", "§7Current: §b" + pullStrength, "", "§7How strongly players", "§7are pulled together"));
      inv.setItem(12, this.createItem(Material.LIME_DYE, "§a+ Increase", "§7Click to increase by 0.1"));
      boolean groupDeath = this.plugin.getConfig().getBoolean("group-death", false);
      inv.setItem(14, this.createToggleItem("§e§lGroup Death", groupDeath, "§7When one player dies,", "§7everyone in chain dies"));
      int chainLimit = this.plugin.getConfig().getInt("chain-limit", 10);
      inv.setItem(19, this.createItem(Material.RED_DYE, "§c- Decrease", "§7Click to decrease by 1"));
      inv.setItem(20, this.createItem(Material.IRON_BARS, "§e§lChain Limit", "§7Current: §b" + chainLimit + " players", "", "§7Maximum players allowed", "§7in a single chain"));
      inv.setItem(21, this.createItem(Material.LIME_DYE, "§a+ Increase", "§7Click to increase by 1"));
      boolean useParticles = this.plugin.getConfig().getBoolean("use-particles", false);
      inv.setItem(23, this.createToggleItem("§e§lUse Particles", useParticles, "§7Use particles instead of", "§7lead visuals for chains"));
      String particleType = this.plugin.getConfig().getString("particle-type", "CRIT");
      inv.setItem(32, this.createItem(Material.FIREWORK_STAR, "§e§lParticle Type", "§7Current: §b" + particleType, "", "§7Click to cycle through", "§7different particle effects", "", "§7Only applies when", "§7Use Particles is §aON"));
      inv.setItem(45, this.createItem(Material.REDSTONE_BLOCK, "§c§lCANCEL", "§7Discard all changes", "§7and close the menu"));
      inv.setItem(49, this.createItem(Material.EMERALD_BLOCK, "§a§lSAVE & CLOSE", "§7Save all changes", "§7to the config file"));
      inv.setItem(53, this.createItem(Material.BOOK, "§e§lINFO", "§7Left/Right buttons: §bAdjust values", "§7Toggle items: §bClick to toggle", "§7Particle type: §bClick to cycle"));
      player.openInventory(inv);
      player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0F, 1.0F);
   }

   private ItemStack createItem(Material material, String name, String... lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (lore.length > 0) {
         List<String> loreList = new ArrayList();

         for(String line : lore) {
            loreList.add(line);
         }

         meta.setLore(loreList);
      }

      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createToggleItem(String name, boolean enabled, String... description) {
      Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      String status = enabled ? "§aON" : "§cOFF";
      List<String> lore = new ArrayList();
      lore.add("§7Status: " + status);
      lore.add("");

      for(String line : description) {
         lore.add(line);
      }

      lore.add("");
      lore.add("§eClick to toggle");
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.setLore(lore);
      item.setItemMeta(meta);
      return item;
   }
}
