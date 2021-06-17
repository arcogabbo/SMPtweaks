package io.noni.smptweaks.events;

import io.noni.smptweaks.SMPtweaks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class CreatureSpawn implements Listener {

    @EventHandler
    void onCreatureSpawn(CreatureSpawnEvent e) {
        if(e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        EntityType entityType = e.getEntity().getType();
        Float multiplier = SMPtweaks.getConfigCache().getEntitySpawnRates().get(entityType);
        if(multiplier != null && Math.random() > multiplier) {
            e.setCancelled(true);
        }

        // Shulker spawn logic
        if(SMPtweaks.getCfg().getBoolean("shulkers_spawn_naturally")) {
            Location loc = e.getLocation();
            if(Math.random() > 0.2 || loc.getBlock().getBiome() != Biome.END_HIGHLANDS) {
                return;
            }
            if(loc.subtract(0, 1, 0).getBlock().getType() != Material.PURPUR_BLOCK) {
                return;
            }
            for(Entity nearbyEntity : e.getEntity().getNearbyEntities(8, 8, 8)) {
                if(nearbyEntity.getType() == EntityType.SHULKER) return;
            }
            if(loc.getWorld() != null) {
                e.setCancelled(true);
                loc.getWorld().spawn(e.getLocation(), Shulker.class);
            }
        }

    }
}