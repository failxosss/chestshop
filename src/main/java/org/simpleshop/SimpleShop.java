package org.simpleshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SimpleShop extends JavaPlugin {

    private static Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Nebyl nalezen Vault nebo zadny ekonomicky plugin (napr. Essentials).");
            getLogger().severe("SimpleShop se vypina - naistaluj Vault + ekonomicky plugin a restartuj server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getLogger().info("SimpleShop byl uspesne nacten. Pouzita ekonomika: " + economy.getName());
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }
}
