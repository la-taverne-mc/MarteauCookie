package be.mrcookie112.marteaucookie.listenerlook;

import org.bukkit.Material;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldguard.WorldGuard;
import org.bukkit.ChatColor;
import java.util.stream.Collectors;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListenerLook implements Listener {

    private Map<Player, BlockData> selectedBlockDataMap = new HashMap<>();
    private Map<Player, Map<Block, BlockData>> adjacentBlockDataMap = new HashMap<>();
    private List<String> nonSelectableBlocks;
    private FileConfiguration config;

    public ListenerLook(JavaPlugin plugin) {
        this.config = plugin.getConfig();
        this.nonSelectableBlocks = config.getStringList("nonSelectableBlocks");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack heldItem = event.getItem();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        Material selectionMaterial = Material.getMaterial(config.getString("selectionItem.material", "STICK"));
        String selectionDisplayName = config.getString("selectionItem.displayName", "");
        List<String> selectionLore = config.getStringList("selectionItem.lore");

        // Vérifier si l'item actuel correspond à la configuration
        if (heldItem == null || heldItem.getType() != selectionMaterial
                || (selectionDisplayName != null && !selectionDisplayName.isEmpty() &&
                   (!heldItem.hasItemMeta() || !heldItem.getItemMeta().hasDisplayName() ||
                    !heldItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', selectionDisplayName))))
                || (selectionLore != null && !selectionLore.isEmpty() &&
                   (!heldItem.hasItemMeta() || !heldItem.getItemMeta().hasLore() ||
                    !heldItem.getItemMeta().getLore().equals(selectionLore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList()))))) {
            return; // Ignorer les événements si l'item ne correspond pas à la configuration
        }


        		
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return; // si rien n'est sélectionné
        }
        
        Location loc = new Location(clickedBlock.getWorld(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        
        
        
        if (!player.isOp() && !set.testState(localPlayer, Flags.BUILD)) {
            event.setCancelled(true);
            // Message d'allerte personnalisable
            String RegionMessage = config.getString("messages.RegionMessage");
            player.sendMessage(RegionMessage != null ? RegionMessage : "Cette region est protégée");
            return;
        }
        
        
        if (nonSelectableBlocks.contains(clickedBlock.getType().toString())) {
            String cannotSelectMessage = config.getString("messages.cannotSelectBlock");
            cannotSelectMessage = cannotSelectMessage.replace("%block%", clickedBlock.getType().toString());
            player.sendMessage(cannotSelectMessage);
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            // Clic gauche : sélecteur de bloc
            if (!selectedBlockDataMap.containsKey(player)) {
                // Vérifier si le joueur peut sélectionner un nouveau bloc
                if (clickedBlock.getType() == Material.DIRT) {
                    // Message troll personnalisable
                    String trollMessage = config.getString("messages.trollMessage");
                    player.sendMessage(trollMessage != null ? trollMessage : "C'est pas très malin de remplacer de la terre par de la terre ;)");
                    return;
                }

                BlockData selectedBlockData = clickedBlock.getBlockData().clone();
                selectedBlockDataMap.put(player, selectedBlockData);
                player.sendMessage(config.getString("messages.selectBlock"));

                // Remplacer le bloc sélectionné par de l'air
                clickedBlock.setType(Material.AIR);
            } else {
                player.sendMessage(config.getString("messages.mustPlaceSelected"));
            }
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // Clic droit : remplacement du bloc
            BlockData selectedBlockData = selectedBlockDataMap.get(player);

            if (selectedBlockData == null) {
                player.sendMessage(config.getString("messages.mustSelectFirst"));
                return;
            }

            if (clickedBlock.getType() == Material.DIRT) {
                // Bloquer temporairement les événements de mise à jour du bloc
                event.setCancelled(true);

                // Sauvegarder les données des blocs adjacents uniquement lors du clic droit
                saveAdjacentBlockData(player, clickedBlock);

                // Restaurer les données des blocs adjacents avant le remplacement
                restoreAdjacentBlockData(player);

                // Appliquer les données au bloc sans mettre à jour immédiatement
                clickedBlock.setBlockData(selectedBlockData, false);

                String replaceBlockMessage = config.getString("messages.replaceBlock");
                replaceBlockMessage = replaceBlockMessage.replace("%block%", clickedBlock.getType().toString());
                player.sendMessage(replaceBlockMessage);

                // Réinitialiser le bloc sélectionné après le remplacement
                selectedBlockDataMap.remove(player);
            } else {
                player.sendMessage(config.getString("messages.canOnlyReplaceDirt"));
            }
        }
    }

    // Sauvegarder les données des blocs adjacents uniquement lors du clic droit
    private void saveAdjacentBlockData(Player player, Block centerBlock) {
        Map<Block, BlockData> adjacentBlockData = new HashMap<>();
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) {
                continue; // Ignorer le bloc central
            }

            Block adjacentBlock = centerBlock.getRelative(face);
            BlockData blockData = adjacentBlock.getBlockData().clone();
            adjacentBlockData.put(adjacentBlock, blockData);
        }

        adjacentBlockDataMap.put(player, adjacentBlockData);
    }

    // Restaurer les données des blocs adjacents
    private void restoreAdjacentBlockData(Player player) {
        Map<Block, BlockData> adjacentBlockData = adjacentBlockDataMap.get(player);

        if (adjacentBlockData != null) {
            for (Map.Entry<Block, BlockData> entry : adjacentBlockData.entrySet()) {
                Block adjacentBlock = entry.getKey();
                BlockData blockData = entry.getValue();

                // Gérer spécifiquement le cas des leviers
                if (blockData instanceof Switch && adjacentBlock.getBlockData() instanceof Switch) {
                    Switch switchData = (Switch) blockData;
                    Switch adjacentSwitch = (Switch) adjacentBlock.getBlockData();

                    // Maintenir l'état d'alimentation du levier
                    adjacentSwitch.setPowered(switchData.isPowered());
                    adjacentBlock.setBlockData(adjacentSwitch, false);
                } else {
                    // Restaurer les données du bloc normal
                    adjacentBlock.setBlockData(blockData, false);
                }
            }

            adjacentBlockDataMap.remove(player);
        }
    }
}
