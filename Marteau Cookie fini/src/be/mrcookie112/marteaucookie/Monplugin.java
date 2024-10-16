package be.mrcookie112.marteaucookie;

import be.mrcookie112.marteaucookie.listenerlook.ListenerLook;
import org.bukkit.plugin.java.JavaPlugin;

public class Monplugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Enregistrement du listener en passant l'instance de JavaPlugin
        new ListenerLook(this);
        
        // Message de démarrage
        getLogger().info("Le plugin MarteauCookie est activé !");
        
        // Charger la configuration
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        // Message de désactivation
        getLogger().info("Le plugin MarteauCookie est désactivé !");
        
        // Charger la configuration
        saveDefaultConfig();
    }
}
