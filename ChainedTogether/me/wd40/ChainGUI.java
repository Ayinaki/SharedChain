package me.wd40;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

public class ChainGUI implements Listener, CommandExecutor {
   private final chainTogether plugin;
   private final ChainManager chainManager;
   private final Map<UUID, ChainBuilder> activeBuilders = new HashMap();

   public ChainGUI(chainTogether plugin, ChainManager chainManager) {
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
         this.openAdminGUI(player);
         return true;
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      HumanEntity whoClicked = event.getWhoClicked();
      if (whoClicked instanceof Player player) {
         if (event.getView().getTitle().equals("§6⛓ Chain Builder ⛓")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            ChainBuilder builder;
            if (clicked == null || clicked.getType() == Material.AIR || (builder = (ChainBuilder)this.activeBuilders.get(player.getUniqueId())) == null) {
               return;
            }

            if (clicked.getType() == Material.EMERALD_BLOCK) {
               this.handleCreateChain(player, builder);
               return;
            }

            if (clicked.getType() == Material.REDSTONE_BLOCK) {
               player.sendMessage("§c⛓ Chain builder cancelled.");
               player.closeInventory();
               this.activeBuilders.remove(player.getUniqueId());
               return;
            }

            if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
               SkullMeta meta = (SkullMeta)clicked.getItemMeta();
               if (meta.getOwningPlayer() == null) {
                  return;
               }

               UUID targetId = meta.getOwningPlayer().getUniqueId();
               if (event.getClick() == ClickType.RIGHT) {
                  builder.leader = targetId;
                  player.sendMessage("§a⛓ Set " + meta.getOwningPlayer().getName() + " as leader!");
                  player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.5F);
                  this.openAdminGUI(player);
                  return;
               }

               if (event.getClick() == ClickType.LEFT) {
                  if (builder.leader != null && builder.leader.equals(targetId)) {
                     player.sendMessage("§c⛓ Can't add the leader as a member!");
                     player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                     return;
                  }

                  if (builder.members.contains(targetId)) {
                     builder.members.remove(targetId);
                     player.sendMessage("§c⛓ Removed " + meta.getOwningPlayer().getName());
                     player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.5F, 1.0F);
                  } else {
                     builder.members.add(targetId);
                     player.sendMessage("§a⛓ Added " + meta.getOwningPlayer().getName());
                     player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.5F, 1.0F);
                  }

                  this.openAdminGUI(player);
               }
            }
         }
      }

   }

   private void handleCreateChain(Player admin, ChainBuilder builder) {
      if (builder.leader == null) {
         admin.sendMessage("§c⛓ You need to select a leader first! (Right-click a player head)");
         admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else if (builder.members.isEmpty()) {
         admin.sendMessage("§c⛓ You need to add at least one member! (Left-click player heads)");
         admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else {
         Player leader = Bukkit.getPlayer(builder.leader);
         if (leader == null) {
            admin.sendMessage("§c⛓ Leader is no longer online!");
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         } else if (this.chainManager.getChainGroup(builder.leader) != null) {
            admin.sendMessage("§c⛓ Leader is already in a chain!");
         } else {
            List<UUID> validMembers = new ArrayList();

            for(UUID memberId : builder.members) {
               Player member;
               if (this.chainManager.getChainGroup(memberId) == null && (member = Bukkit.getPlayer(memberId)) != null) {
                  validMembers.add(memberId);
                  member.sendMessage("§6⛓ [ADMIN] §eYou have been added to a chain by " + admin.getName() + "!");
                  member.playSound(member.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
               }
            }

            if (validMembers.isEmpty()) {
               admin.sendMessage("§c⛓ No valid members to add!");
            } else {
               this.chainManager.addToChain(builder.leader, validMembers);
               leader.sendMessage("§6⛓ [ADMIN] §eYou are now the leader of a chain created by " + admin.getName() + "!");
               leader.playSound(leader.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
               int var10001 = validMembers.size();
               admin.sendMessage("§a⛓ [ADMIN] Successfully created chain with " + (var10001 + 1) + " players!");
               admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.5F);
               admin.closeInventory();
               this.activeBuilders.remove(admin.getUniqueId());
            }
         }
      }
   }

   private void openAdminGUI(Player player) {
      ChainBuilder builder = (ChainBuilder)this.activeBuilders.computeIfAbsent(player.getUniqueId(), (k) -> new ChainBuilder());
      Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, "§6⛓ Chain Builder ⛓");
      int slot = 0;

      for(Player p : Bukkit.getOnlinePlayers()) {
         if (slot >= 45) {
            break;
         }

         ItemStack head = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta)head.getItemMeta();
         meta.setOwningPlayer(p);
         List<String> lore = new ArrayList();
         if (builder.leader != null && builder.leader.equals(p.getUniqueId())) {
            meta.setDisplayName("§6★ " + p.getName() + " §e(LEADER)");
            lore.add("§7This player is set as leader");
         } else if (builder.members.contains(p.getUniqueId())) {
            meta.setDisplayName("§a✓ " + p.getName() + " §7(Selected)");
            lore.add("§7This player will be in the chain");
            lore.add("§cLeft-click to remove");
         } else {
            meta.setDisplayName("§e" + p.getName());
            lore.add("§aLeft-click: §7Add to chain");
            lore.add("§bRight-click: §7Set as leader");
         }

         ChainGroup existingChain = this.chainManager.getChainGroup(p.getUniqueId());
         if (existingChain != null) {
            lore.add("§c⚠ Already in a chain!");
         }

         meta.setLore(lore);
         head.setItemMeta(meta);
         inv.setItem(slot, head);
         ++slot;
      }

      ItemStack create = new ItemStack(Material.EMERALD_BLOCK);
      ItemMeta createMeta = create.getItemMeta();
      createMeta.setDisplayName("§a§lCREATE CHAIN");
      List<String> createLore = new ArrayList();
      String var10001 = builder.leader != null ? Bukkit.getOfflinePlayer(builder.leader).getName() : "None";
      createLore.add("§7Leader: §e" + var10001);
      createLore.add("§7Members: §e" + builder.members.size());
      createLore.add("");
      createLore.add("§aClick to create the chain!");
      createMeta.setLore(createLore);
      create.setItemMeta(createMeta);
      inv.setItem(49, create);
      ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
      ItemMeta cancelMeta = cancel.getItemMeta();
      cancelMeta.setDisplayName("§c§lCANCEL");
      List<String> cancelLore = new ArrayList();
      cancelLore.add("§7Close without creating");
      cancelMeta.setLore(cancelLore);
      cancel.setItemMeta(cancelMeta);
      inv.setItem(45, cancel);
      ItemStack info = new ItemStack(Material.BOOK);
      ItemMeta infoMeta = info.getItemMeta();
      infoMeta.setDisplayName("§e§lHOW TO USE");
      List<String> infoLore = new ArrayList();
      infoLore.add("§7Right-click a head: §bSet as leader");
      infoLore.add("§7Left-click a head: §aAdd to chain");
      infoLore.add("§7Left-click again: §cRemove from chain");
      infoMeta.setLore(infoLore);
      info.setItemMeta(infoMeta);
      inv.setItem(53, info);
      player.openInventory(inv);
      player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0F, 1.0F);
   }

   private static class ChainBuilder {
      private UUID leader;
      private final List<UUID> members = new ArrayList();
   }
}
