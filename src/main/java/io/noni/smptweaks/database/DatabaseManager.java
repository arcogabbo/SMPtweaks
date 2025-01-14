package io.noni.smptweaks.database;

import com.zaxxer.hikari.HikariDataSource;
import io.noni.smptweaks.SMPtweaks;
import io.noni.smptweaks.models.PlayerMeta;
import io.noni.smptweaks.utils.LoggingUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DatabaseManager {
    private final HikariDataSource hikariDataSource;
    private static final FileConfiguration config = SMPtweaks.getPlugin().getConfig();
    private final String table;

    public DatabaseManager() {
        this.hikariDataSource = new HikariDataSource();
        this.hikariDataSource.setMaximumPoolSize(10);
        this.table = config.getString("mysql.table", "smptweaks_player");

        if(config.getBoolean("mysql.enabled")) {
            var host = config.getString("mysql.host");
            var database = config.getString("mysql.database");
            var username = config.getString("mysql.username");
            var password = config.getString("mysql.password");
            this.hikariDataSource.setJdbcUrl("jdbc:mysql://" + host + "/" + database);
            this.hikariDataSource.setUsername(username);
            this.hikariDataSource.setPassword(password);
        } else {
            var dbFile = this.getSQLite();
            if(dbFile != null) {
                this.hikariDataSource.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
        }

        if(!canConnect()) {
            LoggingUtils.error("Unable to connect to database");
            return;
        }

        if(!isSetUpCorrectly()) {
            LoggingUtils.warn("The database is not set up correctly");
            setUp();
        }
    }

    /**
     * Get HikariDataSource
     */
    public HikariDataSource getHikariDataSource() {
        return hikariDataSource;
    }

    /**
     * Create SQLite file
     */
    private File getSQLite() {
        var databaseFile = new File(SMPtweaks.getPlugin().getDataFolder(), "smptweaks.db");
        if(!databaseFile.exists()) {
            try {
                if(!databaseFile.createNewFile()) return null;
            } catch (IOException e) {
                LoggingUtils.error("Could not create SQLite database file");
                e.printStackTrace();
                return null;
            }
        }
        return databaseFile;
    }

    /**
     * Check if connection to database is possible
     */
    public boolean canConnect() {
        try(var ignored = this.hikariDataSource.getConnection()) {
            return true;
        } catch (SQLException throwables) {
            LoggingUtils.error("Unable to connect to database");
            throwables.printStackTrace();
            return false;
        }
    }

    /**
     * Check if table exists
     */
    public boolean isSetUpCorrectly() {
        try(var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "SELECT 1 " +
                    "FROM `" + table + "` " +
                    "LIMIT 1"
            );
            return preparedStatement.execute();
        } catch (SQLException throwables) {
            return false;
        }
    }

    /**
     * Create table for plugin
     */
    public void setUp() {
        try(var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                    "`uuid` VARCHAR(255) UNIQUE NULL PRIMARY KEY," +
                    "`name` VARCHAR(255) NOT NULL," +
                    "`level` SMALLINT DEFAULT 1 NOT NULL," +
                    "`total_xp` INTEGER DEFAULT 0 NOT NULL," +
                    "`xp_display_mode` TINYINT DEFAULT 0 NOT NULL," +
                    "`last_reward_claimed` DATETIME NULL," +
                    "`last_special_item_drop` DATETIME NULL" +
                    ")"
            );
            preparedStatement.execute();
            LoggingUtils.info("Successfully set up database");
        } catch (SQLException throwables) {
            LoggingUtils.error("Could not set up database");
            throwables.printStackTrace();
        }
    }

    /**
     * Get PlayerMeta from database
     * @param player
     * @return playerMeta
     */
    public PlayerMeta getPlayerMeta(Player player) {
        try (var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "SELECT `level`, `total_xp`, `xp_display_mode`, `last_special_item_drop` " +
                    "FROM `" + table + "` " +
                    "WHERE `uuid` = ? " +
                    "LIMIT 1"
            );
            preparedStatement.setString(1, player.getUniqueId().toString());
            var resultSet = preparedStatement.executeQuery();
            if(resultSet.next()) {
                return new PlayerMeta(
                        player,
                        resultSet.getInt("level"),
                        resultSet.getInt("total_xp"),
                        resultSet.getInt("xp_display_mode"),
                        true
                );
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    /**
     * Check if player is in database
     */
    private boolean playerInDB(Player player) {
        try (var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "SELECT `name` " +
                    "FROM `" + table + "` " +
                    "WHERE `uuid` = ? " +
                    "LIMIT 1"
            );
            preparedStatement.setString(1, player.getUniqueId().toString());
            var resultSet = preparedStatement.executeQuery();
            return resultSet.isBeforeFirst();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return false;
    }

    /**
     * Store PlayerMeta in database
     * @param player
     */
    public void savePlayerMeta(Player player) {
        var playerMeta = new PlayerMeta(player);

        try(var con = this.hikariDataSource.getConnection()) {
            PreparedStatement preparedStatement;

            if (!playerInDB(player)) {
                preparedStatement = con.prepareStatement("" +
                        "INSERT INTO `" + table + "` (`name`, `level`, `total_xp`, `xp_display_mode`, `uuid`) " +
                        "VALUES(?, ?, ?, ?, ?)"
                );
            } else {
                preparedStatement = con.prepareStatement("" +
                        "UPDATE `" + table + "` " +
                        "SET " +
                        "`name` = ?, " +
                        "`level` = ?, " +
                        "`total_xp` = ?, " +
                        "`xp_display_mode` = ? " +
                        "WHERE `uuid` = ?"

                );
            }
            preparedStatement.setString(1, playerMeta.getPlayer().getName());
            preparedStatement.setInt(2, playerMeta.getLevel());
            preparedStatement.setInt(3, playerMeta.getTotalXp());
            preparedStatement.setInt(4, playerMeta.getXpDisplayMode());
            preparedStatement.setString(5, playerMeta.getPlayer().getUniqueId().toString());
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    /**
     * Get last reward claimed datetime from database
     */
    public Date getLastRewardClaimedDate(Player player) {
        try (var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "SELECT `last_reward_claimed` " +
                    "FROM `" + table + "` " +
                    "WHERE `uuid` = ? " +
                    "LIMIT 1"
            );
            preparedStatement.setString(1, player.getUniqueId().toString());

            var resultSet = preparedStatement.executeQuery();
            if(resultSet.next()) {
                var datetimeString = resultSet.getString("last_reward_claimed");
                if(datetimeString == null) datetimeString = "0000-00-00 00:00:00";
                return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse(datetimeString);
            }
        } catch (SQLException | ParseException throwables) {
            throwables.printStackTrace();
        }
        return new Date();
    }

    /**
     * Update last reward claimed datetime in database
     */
    public void updateLastRewardClaimedDate(Player player) {
        try(var con = this.hikariDataSource.getConnection()) {
            var preparedStatement = con.prepareStatement("" +
                    "UPDATE `" + table + "` " +
                    "SET " +
                    "`last_reward_claimed` = ? " +
                    "WHERE `uuid` = ?"

            );
            var datetime = new Date();
            String myslqDatetime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(datetime);
            preparedStatement.setString(1, myslqDatetime);
            preparedStatement.setString(2, player.getUniqueId().toString());
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

}
