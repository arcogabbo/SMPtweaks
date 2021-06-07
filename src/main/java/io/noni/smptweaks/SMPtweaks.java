package io.noni.smptweaks;

import io.noni.smptweaks.commands.CollectCommand;
import io.noni.smptweaks.commands.LevelCommand;
import io.noni.smptweaks.commands.LevelTab;
import io.noni.smptweaks.commands.WhereisCommand;
import io.noni.smptweaks.database.DatabaseManager;
import io.noni.smptweaks.events.*;
import io.noni.smptweaks.models.ConfigCache;
import io.noni.smptweaks.placeholders.LevelExpansion;
import io.noni.smptweaks.recipes.RecipeManager;
import io.noni.smptweaks.tasks.PlayerMetaStorerTask;
import io.noni.smptweaks.tasks.TimeModifierTask;
import io.noni.smptweaks.tasks.WeatherClearerTask;
import io.noni.smptweaks.utils.LoggingUtils;
import io.noni.smptweaks.utils.TranslationUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Stream;

public final class SMPtweaks extends JavaPlugin {
    private static SMPtweaks plugin;
    private static DatabaseManager databaseManager;
    private static FileConfiguration config;
    private static ConfigCache configCache;
    private static Map<String, String> translations;

    /**
     * Plugin startup logic
     */
    @Override
    public void onEnable() {
        // Variable for checking startup duration
        long startingTime = System.currentTimeMillis();

        // Static reference to plugin
        plugin = this;

        // Copy default config files
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // Static reference to config
        config = getConfig();
        configCache = new ConfigCache();

        // Static reference to Hikari
        databaseManager = new DatabaseManager();

        // Load translations
        String languageCode = config.getString("language");
        translations = TranslationUtils.loadTranslations(config.getString("language"));

        //
        // Register Event Listeners
        //
        Stream.of(
            config.getBoolean("disable_night_skip")
                    ? new TimeSkip() : null,

            config.getBoolean("disable_night_skip")
                    ? new PlayerBedEnter() : null,

            config.getBoolean("disable_night_skip")
                    ? new PlayerBedLeave() : null,

            config.getBoolean("remove_xp_on_death.enabled") ||
            config.getBoolean("remove_inventory_on_death.enabled") ||
            config.getBoolean("remove_equipment_on_death.enabled") ||
            config.getBoolean("decrease_item_durability_on_death.enabled")
                    ? new PlayerDeath() : null,

            config.getInt("respawn_health") != 20 ||
            config.getInt("respawn_food_level") != 20
                    ? new PlayerRespawn() : null,

            config.getDouble("xp_multiplier") != 1
                    ? new PlayerExpChange() : null,

            config.getDouble("mending_repair_amount_multiplier") != 1
                    ? new PlayerItemMend() : null,

            config.getBoolean("server_levels.enabled")
                    ? new PlayerExpPickup() : null,

            config.getBoolean("buff_vegetarian_food")
                    ? new PlayerItemConsume() : null,

            config.getBoolean("server_levels.enabled")
                    ? new PlayerJoin() : null,

            config.getBoolean("server_levels.enabled")
                    ? new PlayerLeave() : null
        ).forEach(this::registerEvent);

        //
        // Register Recipes
        //
        Stream.of(
            config.getBoolean("craftable_elytra")
                    ? RecipeManager.elytra() : null
        ).forEach(this::registerRecipe);

        //
        // Register PlaceholderExpansions
        //
        Stream.of(
            config.getBoolean("server_levels.enabled") &&
            config.getBoolean("papi_placeholders.enabled")
                    ? new LevelExpansion() : null
        ).forEach(this::registerPlaceholder);

        //
        // Register Commands
        //
        if(config.getBoolean("enable_commands.whereis")) {
            getCommand("whereis").setExecutor(new WhereisCommand());
        }
        if(config.getBoolean("rewards.enabled")) {
            getCommand("collect").setExecutor(new CollectCommand());
        }
        if(config.getBoolean("enable_commands.level") && config.getBoolean("server_levels.enabled")) {
            getCommand("level").setExecutor(new LevelCommand());
            getCommand("level").setTabCompleter(new LevelTab());
        }

        //
        // Schedule tasks
        //
        if(config.getInt("shorten_nights_by") != 0 || config.getInt("extend_days_by") != 0) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new TimeModifierTask(), 0L, 2L);
        }
        if(config.getBoolean("clear_weather_at_dawn")) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new WeatherClearerTask(), 0L, 100L);
        }

        //
        // Done :)
        //
        LoggingUtils.info("Up and running! Startup took " + (System.currentTimeMillis() - startingTime) + "ms");
    }

    /**
     * Register events
     * @param listener
     */
    private void registerEvent(@Nullable Listener listener) {
        if(listener != null) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    /**
     * Register recipes
     * @param recipe
     */
    private void registerRecipe(@Nullable Recipe recipe) {
        if(recipe != null) {
            Bukkit.addRecipe(recipe);
        }
    }

    /**
     * Register placeholders
     * @param expansion
     */
    private void registerPlaceholder(@Nullable PlaceholderExpansion expansion) {
        if(getServer().getPluginManager().getPlugin("PlaceholderAPI") != null && expansion != null) {
            expansion.register();
        }
    }

    /**
     * Plugin shutdown logic
     */
    @Override
    public void onDisable() {
        LoggingUtils.info("Disabling SMPtweaks...");
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            new PlayerMetaStorerTask(player).run();
        }
    }

    /**
     * Get reference to this plugin
     * @return Plugin
     */
    public static SMPtweaks getPlugin() {
        return plugin;
    }

    /**
     * Get reference to DB
     * @return DatabaseManager
     */
    public static DatabaseManager getDB() {
        return databaseManager;
    }

    /**
     * Get reference to config
     * @return FileConfiguration
     */
    public static FileConfiguration getCfg() {
        return config;
    }

    /**
     * Get reference to cached config
     * @return ConfigCache
     */
    public static ConfigCache getConfigCache() {
        return configCache;
    }

    /**
     * Get hashmap of translations
     * @return Translations
     */
    public static Map<String, String> getTranslations() {
        return translations;
    }
}