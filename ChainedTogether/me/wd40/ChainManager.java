package me.wd40;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

public class ChainManager implements Listener, CommandExecutor {
   private final chainTogether plugin;
   private final Map<UUID, ChainGroup> playerToChain = new HashMap();
   private final Map<UUID, ChainRequest> pendingRequests = new HashMap();
   private final Map<UUID, Location> pendingRespawns = new HashMap();
   private final Set<UUID> gracePeriodPlayers = new HashSet();
   private final Set<UUID> offlinePlayers = new HashSet();
   private double maxChainDistance;
   private double pullStrength;
   private boolean chainedPlayersPvp;
   private int chainLimit;
   private boolean groupDeath;
   private boolean useParticles;
   private String particleType;
   private int updateTaskId;
   private int requestExpiryTaskId;

   public ChainManager(chainTogether plugin) {
      this.plugin = plugin;
      this.loadConfig();
      this.startTasks();
   }

   private void loadConfig() {
      this.maxChainDistance = this.plugin.getConfig().getDouble("max-chain-distance", (double)5.0F);
      this.pullStrength = this.plugin.getConfig().getDouble("pull-strength", 0.3);
      this.chainedPlayersPvp = this.plugin.getConfig().getBoolean("chained-players-pvp", true);
      this.chainLimit = this.plugin.getConfig().getInt("chain-limit", 10);
      this.groupDeath = this.plugin.getConfig().getBoolean("group-death", false);
      this.useParticles = this.plugin.getConfig().getBoolean("use-particles", false);
      this.particleType = this.plugin.getConfig().getString("particle-type", "CRIT");
   }

   public void reloadConfig() {
      this.loadConfig();
   }

   private void startTasks() {
      this.updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         Set<ChainGroup> processedGroups = new HashSet();

         for(ChainGroup group : this.playerToChain.values()) {
            if (!processedGroups.contains(group)) {
               group.update(this.maxChainDistance, this.pullStrength, this.gracePeriodPlayers, this.useParticles, this.particleType);
               processedGroups.add(group);
            }
         }

      }, 0L, 1L);
      this.requestExpiryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         long currentTime = System.currentTimeMillis();
         this.pendingRequests.entrySet().removeIf((entry) -> {
            if (currentTime - ((ChainRequest)entry.getValue()).timestamp > 30000L) {
               Player target = Bukkit.getPlayer((UUID)entry.getKey());
               if (target != null) {
                  target.sendMessage("§c⛓ Chain request from §e" + ((ChainRequest)entry.getValue()).requesterName + "§c has expired.");
                  target.playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5F, 1.0F);
                  return true;
               } else {
                  return true;
               }
            } else {
               return false;
            }
         });
      }, 0L, 20L);
   }

   public void shutdown() {
      Bukkit.getScheduler().cancelTask(this.updateTaskId);
      Bukkit.getScheduler().cancelTask(this.requestExpiryTaskId);

      for(ChainGroup group : new HashSet(this.playerToChain.values())) {
         group.cleanup();
      }

      this.playerToChain.clear();
      this.pendingRequests.clear();
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!(sender instanceof Player)) {
         sender.sendMessage("§c⛓ Only players can use this command!");
         return true;
      } else {
         Player player = (Player)sender;
         switch (command.getName().toLowerCase()) {
            case "chain" -> this.handleChainCommand(player, args);
            case "chainaccept" -> this.handleChainAccept(player);
            case "chaindeny" -> this.handleChainDeny(player);
            case "unchain" -> this.handleUnchain(player);
            case "chainkick" -> this.handleChainKick(player, args);
            case "chainlist" -> this.handleChainList(player);
            case "breakchain" -> this.handleBreakChain(player);
            case "chainforce" -> this.handleChainForce(player, args);
            case "chainbreakall" -> this.handleChainBreakAll(player, args);
            case "chaintp" -> this.handleChainTp(player, args);
            case "chaininfo" -> this.handleChainInfo(player, args);
            case "chainlistall" -> this.handleChainListAll(player);
         }

         return true;
      }
   }

   private void handleChainCommand(Player player, String[] args) {
      if (args.length != 1) {
         player.sendMessage("§c⛓ Usage: /chain <player>");
      } else {
         Player target = Bukkit.getPlayer(args[0]);
         if (target == null) {
            player.sendMessage("§c⛓ Player not found!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         } else if (target.equals(player)) {
            player.sendMessage("§c⛓ You cannot chain yourself!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         } else {
            ChainGroup playerGroup = (ChainGroup)this.playerToChain.get(player.getUniqueId());
            if (playerGroup != null && !playerGroup.isLeader(player.getUniqueId())) {
               player.sendMessage("§c⛓ Only the §6leader §ccan add new members!");
               player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else if (playerGroup != null && playerGroup.getMemberCount() >= this.chainLimit) {
               player.sendMessage("§c⛓ Your chain is at the maximum limit of §e" + this.chainLimit + " §cplayers!");
               player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else if (this.playerToChain.containsKey(target.getUniqueId())) {
               player.sendMessage("§c⛓ That player is already in a chain!");
               player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else if (this.pendingRequests.containsKey(target.getUniqueId())) {
               player.sendMessage("§c⛓ That player already has a pending chain request!");
               player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else {
               this.pendingRequests.put(target.getUniqueId(), new ChainRequest(player.getUniqueId(), player.getName()));
               target.sendMessage("§e⛓ §6" + player.getName() + " §ewants to chain with you!");
               target.sendMessage("§a⛓ Type §b/chainaccept §ato accept or §c/chaindeny §ato deny");
               target.sendMessage("§7⛓ (Request expires in 30 seconds)");
               target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.5F);
               player.sendMessage("§a⛓ Chain request sent to §e" + target.getName() + "§a!");
               player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.0F);
            }
         }
      }
   }

   private void handleChainAccept(Player player) {
      ChainRequest request = (ChainRequest)this.pendingRequests.remove(player.getUniqueId());
      if (request == null) {
         player.sendMessage("§c⛓ You don't have any pending chain requests!");
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else {
         Player requester = Bukkit.getPlayer(request.requesterId);
         if (requester == null) {
            player.sendMessage("§c⛓ The requester is no longer online!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         } else {
            ChainGroup requesterGroup = (ChainGroup)this.playerToChain.get(requester.getUniqueId());
            if (requesterGroup == null) {
               ChainGroup newGroup = new ChainGroup(requester.getUniqueId(), this.plugin);
               newGroup.addMember(player.getUniqueId());
               this.playerToChain.put(requester.getUniqueId(), newGroup);
               this.playerToChain.put(player.getUniqueId(), newGroup);
            } else {
               requesterGroup.addMember(player.getUniqueId());
               this.playerToChain.put(player.getUniqueId(), requesterGroup);
            }

            player.sendMessage("§a⛓ You have been chained to §6" + requester.getName() + "'s §agroup!");
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
            requester.sendMessage("§a⛓ §e" + player.getName() + " §ahas joined your chain!");
            requester.playSound(requester.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
            ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
            group.notifyMembers("§e⛓ " + player.getName() + " §ehas joined the chain!", player.getUniqueId(), requester.getUniqueId());
         }
      }
   }

   private void handleChainDeny(Player player) {
      ChainRequest request = (ChainRequest)this.pendingRequests.remove(player.getUniqueId());
      if (request == null) {
         player.sendMessage("§c⛓ You don't have any pending chain requests!");
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else {
         Player requester = Bukkit.getPlayer(request.requesterId);
         if (requester != null) {
            requester.sendMessage("§c⛓ " + player.getName() + " denied your chain request.");
            requester.playSound(requester.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 0.8F);
         }

         player.sendMessage("§c⛓ You denied the chain request from §e" + request.requesterName);
         player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 0.8F);
      }
   }

   private void handleUnchain(Player player) {
      ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
      if (group == null) {
         player.sendMessage("§c⛓ You are not in a chain!");
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else if (group.isLeader(player.getUniqueId())) {
         player.sendMessage("§c⛓ As the leader, leaving will break the entire chain!");
         player.sendMessage("§e⛓ Use §b/breakchain §eto confirm.");
         player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
      } else {
         this.removePlayerFromChain(player.getUniqueId(), player.getName());
         player.sendMessage("§a⛓ You have left the chain!");
         player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 1.0F);
      }

   }

   private void handleChainKick(Player player, String[] args) {
      if (args.length != 1) {
         player.sendMessage("§c⛓ Usage: /chainkick <player>");
      } else {
         ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
         if (group != null && group.isLeader(player.getUniqueId())) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null && group.hasMember(target.getUniqueId())) {
               if (target.equals(player)) {
                  player.sendMessage("§c⛓ Use /breakchain to dissolve the chain!");
                  return;
               }

               this.removePlayerFromChain(target.getUniqueId(), target.getName());
               target.sendMessage("§c⛓ You have been kicked from the chain by §6" + player.getName() + "§c!");
               target.playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0F, 1.2F);
               player.sendMessage("§a⛓ Kicked §e" + target.getName() + "§a from the chain!");
               player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 0.8F);
            } else {
               player.sendMessage("§c⛓ That player is not in your chain!");
               player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            }

         } else {
            player.sendMessage("§c⛓ Only the §6leader §ccan kick members!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
         }
      }
   }

   private void handleChainList(Player player) {
      ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
      if (group == null) {
         player.sendMessage("§c⛓ You are not in a chain!");
         player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
      } else {
         player.sendMessage("§6§l⛓ Chain Members ⛓");
         group.displayMembers(player);
         player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
      }

   }

   private void handleBreakChain(Player player) {
      ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
      if (group == null) {
         player.sendMessage("§c⛓ You are not in a chain!");
      } else if (!group.isLeader(player.getUniqueId()) && !player.hasPermission("chaintogether.breakchain.others")) {
         player.sendMessage("§c⛓ Only the §6leader §cor admins can break the chain!");
      } else {
         this.breakChain(group, player.getName());
         player.sendMessage("§a⛓ Chain broken!");
      }

   }

   private void handleChainForce(Player player, String[] args) {
      if (!player.hasPermission("chaintogether.admin.force")) {
         player.sendMessage("§c⛓ No permission!");
      } else if (args.length < 2) {
         player.sendMessage("§c⛓ Usage: /chainforce <leader> <player1> [player2]...");
      } else {
         Player leader = Bukkit.getPlayer(args[0]);
         if (leader != null && !this.playerToChain.containsKey(leader.getUniqueId())) {
            ChainGroup newGroup = new ChainGroup(leader.getUniqueId(), this.plugin);
            this.playerToChain.put(leader.getUniqueId(), newGroup);

            for(int i = 1; i < args.length; ++i) {
               Player member = Bukkit.getPlayer(args[i]);
               if (member != null && !this.playerToChain.containsKey(member.getUniqueId())) {
                  newGroup.addMember(member.getUniqueId());
                  this.playerToChain.put(member.getUniqueId(), newGroup);
               }
            }

            player.sendMessage("§a⛓ [ADMIN] Force-chained " + newGroup.getMemberCount() + " players!");
         } else {
            player.sendMessage("§c⛓ Invalid leader!");
         }
      }
   }

   private void handleChainBreakAll(Player player, String[] args) {
      if (!player.hasPermission("chaintogether.admin.breakall")) {
         player.sendMessage("§c⛓ No permission!");
      } else if (args.length != 1) {
         player.sendMessage("§c⛓ Usage: /chainbreakall <player>");
      } else {
         Player target = Bukkit.getPlayer(args[0]);
         ChainGroup group = target != null ? (ChainGroup)this.playerToChain.get(target.getUniqueId()) : null;
         if (group == null) {
            player.sendMessage("§c⛓ That player is not in a chain!");
         } else {
            this.breakChain(group, player.getName());
            player.sendMessage("§a⛓ [ADMIN] Broke chain!");
         }

      }
   }

   private void handleChainTp(Player player, String[] args) {
      if (!player.hasPermission("chaintogether.admin.tp")) {
         player.sendMessage("§c⛓ No permission!");
      } else {
         Player target;
         if (args.length == 1 && (target = Bukkit.getPlayer(args[0])) != null) {
            player.teleport(target);
            player.sendMessage("§a⛓ [ADMIN] Teleported!");
         }
      }

   }

   private void handleChainInfo(Player player, String[] args) {
      if (!player.hasPermission("chaintogether.admin.info")) {
         player.sendMessage("§c⛓ No permission!");
      } else if (args.length == 1) {
         Player target = Bukkit.getPlayer(args[0]);
         ChainGroup group = target != null ? (ChainGroup)this.playerToChain.get(target.getUniqueId()) : null;
         if (group == null) {
            player.sendMessage("§c⛓ That player is not in a chain!");
         } else {
            player.sendMessage("§6§l⛓ Chain Info ⛓");
            group.displayMembers(player);
         }

      }
   }

   private void handleChainListAll(Player player) {
      if (!player.hasPermission("chaintogether.admin.listall")) {
         player.sendMessage("§c⛓ No permission!");
      } else {
         Set<ChainGroup> allChains = new HashSet(this.playerToChain.values());
         player.sendMessage("§6§l⛓ Active Chains: §e" + allChains.size());
      }

   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      ChainGroup group = (ChainGroup)this.playerToChain.get(playerId);
      if (group != null) {
         this.offlinePlayers.add(playerId);
         group.setOffline(playerId);
         group.getLeaderName();
         group.notifyMembers("§e⛓ " + player.getName() + " §elogged off while chained!", playerId);
         group.notifyMembers("§7⛓ They will be asked to rejoin when they come back online.", playerId);
         player.sendMessage("§e⛓ You logged off while in a chain. You'll be asked to rejoin when you return!");
      }

      this.pendingRequests.remove(playerId);
      this.gracePeriodPlayers.remove(playerId);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      ChainGroup group;
      if (this.offlinePlayers.contains(playerId) && (group = (ChainGroup)this.playerToChain.get(playerId)) != null) {
         UUID leaderId = (UUID)group.getMembers().get(0);
         Player leader = Bukkit.getPlayer(leaderId);
         if (leader != null && leader.isOnline()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline()) {
                  this.offlinePlayers.remove(playerId);
                  group.setOnline(playerId);
                  player.teleport(leader.getLocation());
                  player.sendMessage("§6⛓ ═══════════════════════════════");
                  player.sendMessage("§a⛓ You've been teleported back to your chain!");
                  player.sendMessage("§e⛓ Leader: §6" + leader.getName());
                  player.sendMessage("§e⛓ Members: §b" + group.getMemberCount());
                  player.sendMessage("§7⛓ Type /unchain if you want to leave");
                  player.sendMessage("§6⛓ ═══════════════════════════════");
                  player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
                  player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
                  group.notifyMembers("§a⛓ " + player.getName() + " §ahas rejoined the chain!", playerId);
                  this.gracePeriodPlayers.add(playerId);
                  Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.gracePeriodPlayers.remove(playerId), 60L);
               }

            }, 40L);
         } else {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline()) {
                  this.offlinePlayers.remove(playerId);
                  this.removePlayerFromChain(playerId, player.getName());
                  player.sendMessage("§c⛓ Your chain leader was offline, so you've been removed from the chain.");
                  player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 1.0F);
               }

            }, 40L);
         }
      }

   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getPlayer();
      ChainGroup group = (ChainGroup)this.playerToChain.get(player.getUniqueId());
      if (group != null) {
         this.gracePeriodPlayers.add(player.getUniqueId());
         group.prepareForDeath(player.getUniqueId());
         if (this.groupDeath) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               for(UUID memberId : new ArrayList(group.getMembers())) {
                  Player member = Bukkit.getPlayer(memberId);
                  if (member != null && !member.equals(player) && !member.isDead()) {
                     member.setHealth((double)0.0F);
                     member.sendMessage("§c⛓ Chain partner died! Everyone dies!");
                  }

                  this.playerToChain.remove(memberId);
               }

               group.cleanup();
            }, 1L);
         } else {
            Location respawnLoc = group.calculateRespawnLocation(player.getUniqueId());
            if (respawnLoc != null) {
               this.pendingRespawns.put(player.getUniqueId(), respawnLoc);
               player.sendMessage("§e⛓ Respawning near your chain...");
               group.notifyMembers("§e⛓ " + player.getName() + " §ewill respawn nearby!", player.getUniqueId());
            } else {
               this.removePlayerFromChain(player.getUniqueId(), player.getName());
            }
         }
      }
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      Location respawnLoc = (Location)this.pendingRespawns.remove(player.getUniqueId());
      if (respawnLoc != null) {
         this.gracePeriodPlayers.add(player.getUniqueId());
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               player.teleport(respawnLoc);
               player.sendMessage("§a⛓ Respawned near your chain!");
               player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.gracePeriodPlayers.remove(player.getUniqueId()), 200L);
            }

         }, 10L);
      }

   }

   @EventHandler
   public void onEntityDamage(EntityDamageEvent event) {
      Entity entity = event.getEntity();
      if (entity.getScoreboardTags().contains("chain_entity")) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
      if (event.getRightClicked().getScoreboardTags().contains("chain_entity")) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onEntityUnleash(EntityUnleashEvent event) {
      if (event.getEntity().getScoreboardTags().contains("chain_entity")) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (!this.chainedPlayersPvp) {
         if (event.getEntity() instanceof Player) {
            Player victim = (Player)event.getEntity();
            if (event.getDamager() instanceof Player) {
               Player attacker = (Player)event.getDamager();
               ChainGroup victimGroup = (ChainGroup)this.playerToChain.get(victim.getUniqueId());
               ChainGroup attackerGroup = (ChainGroup)this.playerToChain.get(attacker.getUniqueId());
               if (victimGroup != null && victimGroup == attackerGroup) {
                  event.setCancelled(true);
                  attacker.sendMessage("§c⛓ You can't attack players in your chain!");
               }
            }
         }

      }
   }

   private void removePlayerFromChain(UUID playerId, String playerName) {
      ChainGroup group = (ChainGroup)this.playerToChain.get(playerId);
      if (group != null) {
         boolean wasLeader = group.isLeader(playerId);
         group.removeMember(playerId);
         this.playerToChain.remove(playerId);
         this.gracePeriodPlayers.remove(playerId);
         if (wasLeader) {
            this.breakChain(group, playerName);
         } else if (group.getMemberCount() <= 1) {
            this.breakChain(group, "system");
         } else {
            group.notifyMembers("§e⛓ " + playerName + " §ehas left the chain!", playerId);
         }

      }
   }

   private void breakChain(ChainGroup group, String breaker) {
      for(UUID memberId : new ArrayList(group.getMembers())) {
         Player member = Bukkit.getPlayer(memberId);
         if (member != null) {
            member.sendMessage("§c⛓ Chain broken by " + breaker + "!");
            member.playSound(member.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.7F, 1.0F);
         }

         this.playerToChain.remove(memberId);
         this.gracePeriodPlayers.remove(memberId);
      }

      group.cleanup();
   }

   public ChainGroup getChainGroup(UUID playerId) {
      return (ChainGroup)this.playerToChain.get(playerId);
   }

   public void addToChain(UUID leaderId, List<UUID> memberIds) {
      ChainGroup group = new ChainGroup(leaderId, this.plugin);
      this.playerToChain.put(leaderId, group);

      for(UUID memberId : memberIds) {
         group.addMember(memberId);
         this.playerToChain.put(memberId, group);
      }

   }

   public Map<UUID, ChainGroup> getAllChains() {
      return this.playerToChain;
   }

   private static class ChainRequest {
      private final UUID requesterId;
      private final String requesterName;
      private final long timestamp = System.currentTimeMillis();

      public ChainRequest(UUID requesterId, String requesterName) {
         this.requesterId = requesterId;
         this.requesterName = requesterName;
      }
   }
}
