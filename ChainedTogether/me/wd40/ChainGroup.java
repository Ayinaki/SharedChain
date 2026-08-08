package me.wd40;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class ChainGroup {
   private final List<UUID> members = new ArrayList();
   private final Map<UUID, Bat> leashEntities = new HashMap();
   private final Set<UUID> offlineMembers = new HashSet();
   private final JavaPlugin plugin;
   private int tickCounter = 0;
   private static final int LEASH_REFRESH_INTERVAL = 100;

   public ChainGroup(UUID leaderId, JavaPlugin plugin) {
      this.plugin = plugin;
      this.members.add(leaderId);
   }

   public void addMember(UUID playerId) {
      this.members.add(playerId);
   }

   public void removeMember(UUID playerId) {
      Bat entity = (Bat)this.leashEntities.remove(playerId);
      if (entity != null && entity.isValid()) {
         entity.setLeashHolder((Entity)null);
         entity.remove();
      }

      int index = this.members.indexOf(playerId);
      if (index > 0) {
         UUID prevId = (UUID)this.members.get(index - 1);
         Bat prevEntity = (Bat)this.leashEntities.get(prevId);
         if (prevEntity != null && prevEntity.isValid() && prevEntity.isLeashed()) {
            prevEntity.setLeashHolder((Entity)null);
         }
      }

      this.members.remove(playerId);
      this.offlineMembers.remove(playerId);
   }

   public void prepareForDeath(UUID playerId) {
      Bat entity = (Bat)this.leashEntities.remove(playerId);
      if (entity != null && entity.isValid()) {
         entity.setLeashHolder((Entity)null);
         entity.remove();
      }

      int index = this.members.indexOf(playerId);
      if (index > 0) {
         UUID prevId = (UUID)this.members.get(index - 1);
         Bat prevEntity = (Bat)this.leashEntities.get(prevId);
         if (prevEntity != null && prevEntity.isValid()) {
            prevEntity.setLeashHolder((Entity)null);
         }
      }

   }

   public void setOffline(UUID playerId) {
      this.offlineMembers.add(playerId);
      Bat entity = (Bat)this.leashEntities.remove(playerId);
      if (entity != null && entity.isValid()) {
         entity.setLeashHolder((Entity)null);
         entity.remove();
      }

      int index = this.members.indexOf(playerId);
      if (index > 0) {
         UUID prevId = (UUID)this.members.get(index - 1);
         Bat prevEntity = (Bat)this.leashEntities.get(prevId);
         if (prevEntity != null && prevEntity.isValid()) {
            prevEntity.setLeashHolder((Entity)null);
         }
      }

   }

   public void setOnline(UUID playerId) {
      this.offlineMembers.remove(playerId);
   }

   public boolean isOffline(UUID playerId) {
      return this.offlineMembers.contains(playerId);
   }

   public boolean isLeader(UUID playerId) {
      return !this.members.isEmpty() && ((UUID)this.members.get(0)).equals(playerId);
   }

   public boolean hasMember(UUID playerId) {
      return this.members.contains(playerId);
   }

   public int getMemberCount() {
      return this.members.size();
   }

   public List<UUID> getMembers() {
      return new ArrayList(this.members);
   }

   public String getLeaderName() {
      if (this.members.isEmpty()) {
         return "Unknown";
      } else {
         Player leader = Bukkit.getPlayer((UUID)this.members.get(0));
         return leader != null ? leader.getName() : "Offline";
      }
   }

   public Location calculateRespawnLocation(UUID deadPlayerId) {
      List<Location> aliveLocations = new ArrayList();

      for(UUID memberId : this.members) {
         Player member;
         if (!memberId.equals(deadPlayerId) && !this.offlineMembers.contains(memberId) && (member = Bukkit.getPlayer(memberId)) != null && !member.isDead() && member.isOnline()) {
            aliveLocations.add(member.getLocation());
         }
      }

      if (aliveLocations.isEmpty()) {
         return null;
      } else {
         double avgX = (double)0.0F;
         double avgY = (double)0.0F;
         double avgZ = (double)0.0F;

         for(Location loc : aliveLocations) {
            avgX += loc.getX();
            avgY += loc.getY();
            avgZ += loc.getZ();
         }

         Location respawnLoc = new Location(((Location)aliveLocations.get(0)).getWorld(), avgX / (double)aliveLocations.size(), avgY / (double)aliveLocations.size(), avgZ / (double)aliveLocations.size());
         if (!aliveLocations.isEmpty()) {
            respawnLoc.setYaw(((Location)aliveLocations.get(0)).getYaw());
            respawnLoc.setPitch(((Location)aliveLocations.get(0)).getPitch());
         }

         return respawnLoc;
      }
   }

   public void update(double maxDistance, double pullStrength, Set<UUID> gracePeriodPlayers, boolean useParticles, String particleType) {
      ++this.tickCounter;
      if (useParticles) {
         if (!this.leashEntities.isEmpty()) {
            this.cleanupLeashEntities();
         }

         this.drawParticleChains(particleType);
      } else {
         boolean forceRefresh = this.tickCounter % 100 == 0;
         this.updateLeashEntities(forceRefresh);
      }

      this.applyLeadPhysics(maxDistance, pullStrength, gracePeriodPlayers);
   }

   private void cleanupLeashEntities() {
      for(Bat entity : this.leashEntities.values()) {
         if (entity != null && entity.isValid()) {
            entity.setLeashHolder((Entity)null);
            entity.remove();
         }
      }

      this.leashEntities.clear();
   }

   private void updateLeashEntities(boolean forceRefresh) {
      if (forceRefresh) {
         this.cleanupLeashEntities();
      }

      for(int i = 0; i < this.members.size(); ++i) {
         UUID memberId = (UUID)this.members.get(i);
         if (!this.offlineMembers.contains(memberId)) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline() && !player.isDead()) {
               Bat entity2 = (Bat)this.leashEntities.get(memberId);
               if (entity2 == null || !entity2.isValid()) {
                  Location spawnLoc = this.getBehindPlayerLocation(player);
                  entity2 = (Bat)player.getWorld().spawn(spawnLoc, Bat.class, (bat) -> {
                     bat.setAI(false);
                     bat.setInvulnerable(true);
                     bat.setSilent(true);
                     bat.setInvisible(true);
                     bat.setGravity(false);
                     bat.setCollidable(false);
                     bat.setAwake(true);
                     bat.addScoreboardTag("chain_entity");
                     if (bat.getAttribute(Attribute.GENERIC_SCALE) != null) {
                        bat.getAttribute(Attribute.GENERIC_SCALE).setBaseValue((double)0.0625F);
                     }

                  });
                  this.leashEntities.put(memberId, entity2);
               }

               Location batLocation = this.getBehindPlayerLocation(player);
               entity2.teleport(batLocation);
               UUID nextOnlineId = null;

               for(int j = i + 1; j < this.members.size(); ++j) {
                  UUID potentialNextId = (UUID)this.members.get(j);
                  Player potentialNext;
                  if (!this.offlineMembers.contains(potentialNextId) && (potentialNext = Bukkit.getPlayer(potentialNextId)) != null && potentialNext.isOnline() && !potentialNext.isDead()) {
                     nextOnlineId = potentialNextId;
                     break;
                  }
               }

               if (nextOnlineId != null) {
                  Bat nextEntity = (Bat)this.leashEntities.get(nextOnlineId);
                  if (nextEntity != null && nextEntity.isValid() && (!entity2.isLeashed() || !entity2.getLeashHolder().equals(nextEntity))) {
                     entity2.setLeashHolder(nextEntity);
                  }
               } else if (entity2.isLeashed()) {
                  entity2.setLeashHolder((Entity)null);
               }
            } else {
               Bat entity = (Bat)this.leashEntities.remove(memberId);
               if (entity != null && entity.isValid()) {
                  entity.setLeashHolder((Entity)null);
                  entity.remove();
               }
            }
         }
      }

   }

   private void applyLeadPhysics(double maxDistance, double pullStrength, Set<UUID> gracePeriodPlayers) {
      for(int i = 0; i < this.members.size() - 1; ++i) {
         UUID p1Id = (UUID)this.members.get(i);
         if (!this.offlineMembers.contains(p1Id)) {
            UUID p2Id = null;

            for(int j = i + 1; j < this.members.size(); ++j) {
               if (!this.offlineMembers.contains(this.members.get(j))) {
                  p2Id = (UUID)this.members.get(j);
                  break;
               }
            }

            if (p2Id != null && !gracePeriodPlayers.contains(p1Id) && !gracePeriodPlayers.contains(p2Id)) {
               Player p1 = Bukkit.getPlayer(p1Id);
               Player p2 = Bukkit.getPlayer(p2Id);
               if (p1 != null && p2 != null && !p1.isDead() && !p2.isDead() && p1.getWorld().equals(p2.getWorld())) {
                  double distance = p1.getLocation().distance(p2.getLocation());
                  if (distance > maxDistance) {
                     Vector direction = p2.getLocation().toVector().subtract(p1.getLocation().toVector()).normalize();
                     double pullAmount = Math.min((distance - maxDistance) * pullStrength, (double)0.5F);
                     Vector p1Velocity = direction.clone().multiply(pullAmount);
                     Vector p2Velocity = direction.clone().multiply(-pullAmount);
                     p1.setVelocity(p1.getVelocity().add(p1Velocity));
                     p2.setVelocity(p2.getVelocity().add(p2Velocity));
                     if (distance > maxDistance * 1.2 && Math.random() < 0.05) {
                        p1.playSound(p1.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.3F, 0.8F);
                        p2.playSound(p2.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.3F, 0.8F);
                     }
                  }
               }
            }
         }
      }

   }

   public void notifyMembers(String message, UUID... excludeIds) {
      Set<UUID> excludeSet = new HashSet(Arrays.asList(excludeIds));

      for(UUID memberId : this.members) {
         Player member;
         if (!excludeSet.contains(memberId) && !this.offlineMembers.contains(memberId) && (member = Bukkit.getPlayer(memberId)) != null) {
            member.sendMessage(message);
            member.playSound(member.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.5F);
         }
      }

   }

   public void displayMembers(Player viewer) {
      for(int i = 0; i < this.members.size(); ++i) {
         UUID memberId = (UUID)this.members.get(i);
         Player member = Bukkit.getPlayer(memberId);
         String name = member != null ? member.getName() : "§7Offline";
         String status = this.offlineMembers.contains(memberId) ? " §8(Disconnected)" : "";
         String prefix = i == 0 ? "§6★ [LEADER] §e" : "§f  " + i + ". §e";
         viewer.sendMessage(prefix + name + status);
      }

      viewer.sendMessage("§7⛓ Total members: §b" + this.members.size());
   }

   public void cleanup() {
      for(Bat entity : this.leashEntities.values()) {
         if (entity != null && entity.isValid()) {
            entity.setLeashHolder((Entity)null);
            entity.remove();
         }
      }

      this.leashEntities.clear();
      this.offlineMembers.clear();
   }

   private void drawParticleChains(String particleTypeName) {
      Particle.DustOptions dustOptions = null;
      String upperName = particleTypeName.toUpperCase();

      Particle particle;
      try {
         if (!upperName.equals("REDSTONE") && !upperName.equals("DUST")) {
            particle = Particle.valueOf(upperName);
         } else {
            particle = Particle.DUST;
            dustOptions = new Particle.DustOptions(Color.RED, 1.0F);
         }
      } catch (IllegalArgumentException var25) {
         particle = Particle.CRIT;
      }

      List<Player> viewers = new ArrayList();

      for(UUID memberId : this.members) {
         if (!this.offlineMembers.contains(memberId)) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
               viewers.add(p);
            }
         }
      }

      if (!viewers.isEmpty()) {
         for(int i = 0; i < this.members.size() - 1; ++i) {
            UUID p1Id = (UUID)this.members.get(i);
            if (!this.offlineMembers.contains(p1Id)) {
               UUID p2Id = null;

               for(int j = i + 1; j < this.members.size(); ++j) {
                  UUID potentialId = (UUID)this.members.get(j);
                  if (!this.offlineMembers.contains(potentialId)) {
                     p2Id = potentialId;
                     break;
                  }
               }

               if (p2Id != null) {
                  Player p1 = Bukkit.getPlayer(p1Id);
                  Player p2 = Bukkit.getPlayer(p2Id);
                  if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld()) && !p1.isDead() && !p2.isDead()) {
                     Location loc1 = p1.getLocation().add((double)0.0F, 0.9, (double)0.0F);
                     Location loc2 = p2.getLocation().add((double)0.0F, 0.9, (double)0.0F);
                     double distance = loc1.distance(loc2);
                     if (!(distance < (double)0.5F)) {
                        double maxParticleDistance = Math.min(distance, (double)50.0F);
                        Vector direction = loc2.toVector().subtract(loc1.toVector()).normalize();
                        double spacing = (double)0.5F;

                        for(double d = (double)0.0F; d < maxParticleDistance; d += spacing) {
                           Location particleLoc = loc1.clone().add(direction.clone().multiply(d));

                           for(Player viewer : viewers) {
                              if (viewer.getWorld().equals(particleLoc.getWorld())) {
                                 if (dustOptions != null) {
                                    viewer.spawnParticle(particle, particleLoc, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, dustOptions);
                                 } else {
                                    viewer.spawnParticle(particle, particleLoc, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

      }
   }

   private Location getBehindPlayerLocation(Player player) {
      Location loc = player.getLocation();
      Vector direction = loc.getDirection();
      direction.setY(0);
      direction.normalize();
      direction.multiply(-1);
      direction.multiply(0.3);
      Location behindLoc = loc.clone().add(direction);
      behindLoc.setY(loc.getY() + 0.9);
      return behindLoc;
   }
}
