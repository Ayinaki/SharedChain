package me.wd40;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class chainTogether extends JavaPlugin {
   private static chainTogether instance;
   private ChainManager chainManager;
   private ChainGUI chainGUI;
   private ConfigGUI configGUI;

   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      this.chainManager = new ChainManager(this);
      this.chainGUI = new ChainGUI(this, this.chainManager);
      this.configGUI = new ConfigGUI(this, this.chainManager);
      Bukkit.getPluginManager().registerEvents(this.chainManager, this);
      Bukkit.getPluginManager().registerEvents(this.chainGUI, this);
      Bukkit.getPluginManager().registerEvents(this.configGUI, this);
      this.getCommand("chain").setExecutor(this.chainManager);
      this.getCommand("chainaccept").setExecutor(this.chainManager);
      this.getCommand("chaindeny").setExecutor(this.chainManager);
      this.getCommand("unchain").setExecutor(this.chainManager);
      this.getCommand("chainkick").setExecutor(this.chainManager);
      this.getCommand("chainlist").setExecutor(this.chainManager);
      this.getCommand("breakchain").setExecutor(this.chainManager);
      this.getCommand("chainadmin").setExecutor(this.chainGUI);
      this.getCommand("chainconfig").setExecutor(this.configGUI);
      this.getCommand("chainforce").setExecutor(this.chainManager);
      this.getCommand("chainbreakall").setExecutor(this.chainManager);
      this.getCommand("chaintp").setExecutor(this.chainManager);
      this.getCommand("chaininfo").setExecutor(this.chainManager);
      this.getCommand("chainlistall").setExecutor(this.chainManager);
      this.getLogger().info("ChainTogether enabled!");
   }

   public void onDisable() {
      if (this.chainManager != null) {
         this.chainManager.shutdown();
      }

      this.getLogger().info("ChainTogether disabled!");
   }

   public static chainTogether getInstance() {
      return instance;
   }

   public ChainManager getChainManager() {
      return this.chainManager;
   }
}
