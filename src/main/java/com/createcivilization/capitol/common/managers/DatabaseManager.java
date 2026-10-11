package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.Capitol;
import com.createcivilization.capitol.common.compat.sable.SableCompat;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

	// bump this whenever a migration is added below
	private static final int SCHEMA_VERSION = 7;

	public static CapitolDatabase database = new CapitolDatabase();


	private static Connection connection;

	public static void init(Path saveFolder){
		Capitol.LOGGER.info("Attempting to initialize database");
		try{
			Class.forName("org.sqlite.JDBC");
			Path databasePath = saveFolder.resolve("capitol.db");
			String url = "jdbc:sqlite:" + databasePath.toAbsolutePath();

			connection = DriverManager.getConnection(url);

			int version = getSchemaVersion();
			runMigrations(version, databasePath);

			try (Statement stmt = connection.createStatement()) {
				stmt.execute("PRAGMA journal_mode=WAL;");
				stmt.execute("PRAGMA synchronous=NORMAL;");
				stmt.execute("PRAGMA foreign_keys=ON;");
			}

			createTables();
			Capitol.LOGGER.info("Capitol Database initialized");

			Capitol.LOGGER.info("Warming Cache");
			database.warmCache();

		} catch (ClassNotFoundException e) {
			Capitol.LOGGER.error("No SQLite driver found.", e);
		} catch (SQLException e) {
			Capitol.LOGGER.error("Failed to initialize capitol database", e);
			// don't leave a broken connection open
			try {
				if (connection != null) connection.close();
			} catch (SQLException ignored) {}
		}

	}

	private static void createTables() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(
				"CREATE TABLE IF NOT EXISTS teams (" +
					"id TEXT PRIMARY KEY NOT NULL," +
					"name TEXT NOT NULL UNIQUE," +
					"tag TEXT NOT NULL," +
					"current_claims INT NOT NULL, " +
					"max_claims INT," +
					"color INT NOT NULL," +
					"description TEXT," +
					"team_permissions INT NOT NULL," +
					"created_at LONG NOT NULL," +
					"capitol_x INTEGER," +
					"capitol_y INTEGER," +
					"capitol_z INTEGER," +
					"capitol_dimension TEXT)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS team_roles (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"team_id TEXT NOT NULL," +
					"name TEXT NOT NULL," +
					"permissions INTEGER NOT NULL DEFAULT 0," +
					"color INTEGER NOT NULL DEFAULT 16777215," +
					"position INTEGER NOT NULL DEFAULT 0," +
					"system_key TEXT," +
					"flags INTEGER NOT NULL DEFAULT 1," +
					"UNIQUE (team_id, name)," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS team_members (" +
					"team_id TEXT NOT NULL," +
					"player_uuid TEXT NOT NULL," +
					"role_id INTEGER NOT NULL," +
					"PRIMARY KEY (team_id, player_uuid)," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE," +
					"FOREIGN KEY (role_id) REFERENCES team_roles (id) ON DELETE RESTRICT)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS member_roles (" +
					"team_id TEXT NOT NULL," +
					"player_uuid TEXT NOT NULL," +
					"role_id INTEGER NOT NULL," +
					"PRIMARY KEY (team_id, player_uuid, role_id)," +
					"FOREIGN KEY (team_id, player_uuid) REFERENCES team_members (team_id, player_uuid) ON DELETE CASCADE," +
					"FOREIGN KEY (role_id) REFERENCES team_roles (id) ON DELETE CASCADE)"
			);
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_member_roles_role ON member_roles(role_id)");
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_member_roles_player ON member_roles(player_uuid)");

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS chunks (" +
					"dimension TEXT NOT NULL," +  // Should be like "minecraft:overwold" ect
					"chunk_x INTEGER NOT NULL," +
					"chunk_z INTEGER NOT NULL," +
					"team_id TEXT NOT NULL," +
					"force_loaded BOOLEAN NOT NULL," +
					"capitol_block_id INTEGER," +  // the capitol block this chunk falls under, if any
					"PRIMARY KEY (dimension, chunk_x, chunk_z)," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS capitol_blocks (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"team_id TEXT NOT NULL," +
					"dimension TEXT NOT NULL," +
					"x INTEGER NOT NULL," +
					"y INTEGER NOT NULL," +
					"z INTEGER NOT NULL," +
					"is_capital INTEGER NOT NULL DEFAULT 0," +
					"tier TEXT," +  // VILLAGE/TOWN/CITY; null for the Capital
					"name TEXT NOT NULL UNIQUE," +  // globally unique, set when the block is named
					"mayor_uuid TEXT," +  // who runs this block, defaults to the team leader
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);


			stmt.execute(
				"CREATE TABLE IF NOT EXISTS player_permissions (" +
					"team_id TEXT NOT NULL," +
					"player_uuid TEXT NOT NULL," +
					"permissions INTEGER NOT NULL DEFAULT 0," +
					"PRIMARY KEY (team_id, player_uuid)," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS wars (" +
					"declaring_team_id TEXT NOT NULL," +
					"receiving_team_id TEXT NOT NULL," +
					"time_of_creation LONG NOT NULL," +
					"PRIMARY KEY (declaring_team_id, receiving_team_id)," +
					"FOREIGN KEY (declaring_team_id) REFERENCES teams (id) ON DELETE CASCADE," +
					"FOREIGN KEY (receiving_team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS allies (" +
					"team_id TEXT NOT NULL," +
					"ally_id TEXT NOT NULL," +
					"PRIMARY KEY (team_id, ally_id)," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE," +
					"FOREIGN KEY (ally_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			stmt.execute(
				"CREATE TABLE IF NOT EXISTS sub_claims (" +
					"id TEXT PRIMARY KEY NOT NULL," +
					"team_id TEXT NOT NULL," +
					"name TEXT NOT NULL," +
					"dimension TEXT NOT NULL," +
					"min_x INTEGER NOT NULL," +
					"min_y INTEGER NOT NULL," +
					"min_z INTEGER NOT NULL," +
					"max_x INTEGER NOT NULL," +
					"max_y INTEGER NOT NULL," +
					"max_z INTEGER NOT NULL," +
					"owner_uuid TEXT NOT NULL," +
					"permissions INTEGER NOT NULL DEFAULT 0," +
					"protections INTEGER NOT NULL DEFAULT 0," +
					"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
			);

			if(SableCompat.LOADED){
				stmt.execute(
					"CREATE TABLE IF NOT EXISTS sub_levels (" +
						"id TEXT NOT NULL," +
						"team_id TEXT NOT NULL," +
						"PRIMARY KEY (id)," +
						"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
				);
			} else {
				stmt.execute(
					"DROP TABLE IF EXISTS sub_levels"
				);
			}

		}
	}

	private static void runMigrations(int current, Path databasePath) throws SQLException {
		// snapshot before any structural change so an upgrade can always be rolled back
		if (current > 0 && current < SCHEMA_VERSION) {
			backupDatabase(databasePath, current);
		}
		if (current < 1) {
			// Databases from before capitol position tracking lack these columns.
			// Fresh databases get them from createTables() instead, so only migrate
			// when an old teams table actually exists.
			if (tableExists("teams") && !columnExists("teams", "capitol_x")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("ALTER TABLE teams ADD COLUMN capitol_x INTEGER");
					stmt.execute("ALTER TABLE teams ADD COLUMN capitol_y INTEGER");
					stmt.execute("ALTER TABLE teams ADD COLUMN capitol_z INTEGER");
					stmt.execute("ALTER TABLE teams ADD COLUMN capitol_dimension TEXT");
				}
			}
			setSchemaVersion(1);
		}
		if (current < 2) {
			try (Statement stmt = connection.createStatement()) {
				stmt.execute(
					"CREATE TABLE IF NOT EXISTS capitol_blocks (" +
						"id INTEGER PRIMARY KEY AUTOINCREMENT," +
						"team_id TEXT NOT NULL," +
						"dimension TEXT NOT NULL," +
						"x INTEGER NOT NULL," +
						"y INTEGER NOT NULL," +
						"z INTEGER NOT NULL," +
						"is_capital INTEGER NOT NULL DEFAULT 0," +
						"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
				);
				// Any capitol block that predates this migration becomes the team's Capital.
				if (tableExists("teams") && columnExists("teams", "capitol_x")) {
					stmt.execute(
						"INSERT INTO capitol_blocks (team_id, dimension, x, y, z, is_capital) " +
							"SELECT id, capitol_dimension, capitol_x, capitol_y, capitol_z, 1 FROM teams " +
							"WHERE capitol_x IS NOT NULL AND capitol_dimension IS NOT NULL"
					);
				}
			}
			setSchemaVersion(2);
		}
		if (current < 3) {
			// capitol blocks get a settlement tier; the Capital doesn't have one
			if (tableExists("capitol_blocks") && !columnExists("capitol_blocks", "tier")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("ALTER TABLE capitol_blocks ADD COLUMN tier TEXT");
					// old extra blocks start as Villages
					stmt.execute("UPDATE capitol_blocks SET tier = 'VILLAGE' WHERE is_capital = 0 AND tier IS NULL");
				}
			}
			// chunks now remember which capitol block they belong to
			if (tableExists("chunks") && !columnExists("chunks", "capitol_block_id")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("ALTER TABLE chunks ADD COLUMN capitol_block_id INTEGER");
				}
				backfillCapitolBlockChunks();
			}
			setSchemaVersion(3);
		}
		if (current < 4) {
			// blocks get a globally-unique name (set at placement) + a mayor
			if (tableExists("capitol_blocks") && !columnExists("capitol_blocks", "name")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("ALTER TABLE capitol_blocks ADD COLUMN name TEXT");
					stmt.execute("ALTER TABLE capitol_blocks ADD COLUMN mayor_uuid TEXT");
					// old blocks get a placeholder name so the unique constraint has something to hold
					stmt.execute("UPDATE capitol_blocks SET name = CASE WHEN is_capital = 1 THEN 'Capital ' || id ELSE 'Village ' || id END WHERE name IS NULL");
					// sqlite can't add UNIQUE to an existing column, so use a unique index instead
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_capitol_blocks_name ON capitol_blocks(name)");
				}
			}
			setSchemaVersion(4);
		}
		if (current < 5) {
			// war system tables (wars + allies); fresh databases get them from createTables()
			if (tableExists("teams")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute(
						"CREATE TABLE IF NOT EXISTS wars (" +
							"declaring_team_id TEXT NOT NULL," +
							"receiving_team_id TEXT NOT NULL," +
							"time_of_creation LONG NOT NULL," +
							"PRIMARY KEY (declaring_team_id, receiving_team_id)," +
							"FOREIGN KEY (declaring_team_id) REFERENCES teams (id) ON DELETE CASCADE," +
							"FOREIGN KEY (receiving_team_id) REFERENCES teams (id) ON DELETE CASCADE)"
					);
					stmt.execute(
						"CREATE TABLE IF NOT EXISTS allies (" +
							"team_id TEXT NOT NULL," +
							"ally_id TEXT NOT NULL," +
							"PRIMARY KEY (team_id, ally_id)," +
							"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE," +
							"FOREIGN KEY (ally_id) REFERENCES teams (id) ON DELETE CASCADE)"
					);
				}
			}
			// owner roles get the new DECLARE_WAR permission bit (1L << 28)
			if (tableExists("team_roles")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("UPDATE team_roles SET permissions = permissions | " + (1L << 28) + " WHERE name = 'owner'");
				}
			}
			setSchemaVersion(5);
		}
		if (current < 6) {
			// sub-claim table (subclaim-branch). Fresh databases get it from createTables(),
			// so only databases that predate it need this step.
			if (!tableExists("sub_claims")) {
				try (Statement stmt = connection.createStatement()) {
					stmt.execute(
						"CREATE TABLE IF NOT EXISTS sub_claims (" +
							"id TEXT PRIMARY KEY NOT NULL," +
							"team_id TEXT NOT NULL," +
							"name TEXT NOT NULL," +
							"dimension TEXT NOT NULL," +
							"min_x INTEGER NOT NULL," +
							"min_y INTEGER NOT NULL," +
							"min_z INTEGER NOT NULL," +
							"max_x INTEGER NOT NULL," +
							"max_y INTEGER NOT NULL," +
							"max_z INTEGER NOT NULL," +
							"owner_uuid TEXT NOT NULL," +
							"permissions INTEGER NOT NULL DEFAULT 0," +
							"protections INTEGER NOT NULL DEFAULT 0," +
							"FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE)"
					);
				}
			} else {
				// older sub_claims tables can be missing the owner/permission columns
				if (!columnExists("sub_claims", "owner_uuid")) {
					try (Statement stmt = connection.createStatement()) {
						stmt.execute("ALTER TABLE sub_claims ADD COLUMN owner_uuid TEXT NOT NULL DEFAULT ''");
						stmt.execute("ALTER TABLE sub_claims ADD COLUMN permissions INTEGER NOT NULL DEFAULT 0");
					}
				}
				if (!columnExists("sub_claims", "protections")) {
					try (Statement stmt = connection.createStatement()) {
						stmt.execute("ALTER TABLE sub_claims ADD COLUMN protections INTEGER NOT NULL DEFAULT 0");
					}
				}
			}
			setSchemaVersion(6);
		}
		if (current < 7) {
			// roles overhaul: multiple roles per member + editable role metadata, in one transaction
			inTransaction(DatabaseManager::migrateToV7);
		}
	}

	// v7: multi-role support, editable role metadata, and the sub-claim permission bit fix.
	// Every step is guarded so it can run on a database that already has some of it.
	private static void migrateToV7() throws SQLException {
		if (tableExists("team_roles")) {
			if (!columnExists("team_roles", "color"))
				execute("ALTER TABLE team_roles ADD COLUMN color INTEGER NOT NULL DEFAULT 16777215");
			if (!columnExists("team_roles", "position"))
				execute("ALTER TABLE team_roles ADD COLUMN position INTEGER NOT NULL DEFAULT 0");
			if (!columnExists("team_roles", "system_key"))
				execute("ALTER TABLE team_roles ADD COLUMN system_key TEXT");
			if (!columnExists("team_roles", "flags"))
				execute("ALTER TABLE team_roles ADD COLUMN flags INTEGER NOT NULL DEFAULT 1");

			// tag the well-known roles so owner/member/default stop depending on the name
			execute("UPDATE team_roles SET system_key = 'owner' WHERE system_key IS NULL AND name = 'owner'");
			execute("UPDATE team_roles SET system_key = 'member' WHERE system_key IS NULL AND name = 'member'");
			execute("UPDATE team_roles SET system_key = 'default' WHERE system_key IS NULL AND name = 'default'");

			// hierarchy: default < member < custom roles (stable by id) < owner
			execute("UPDATE team_roles SET position = 0 WHERE system_key = 'default'");
			execute("UPDATE team_roles SET position = 1 WHERE system_key = 'member'");
			execute("UPDATE team_roles SET position = 1000000 WHERE system_key = 'owner'");
			execute("UPDATE team_roles SET position = 2 + (" +
				"SELECT COUNT(*) FROM team_roles other WHERE other.team_id = team_roles.team_id " +
				"AND other.system_key IS NULL AND other.id < team_roles.id) " +
				"WHERE system_key IS NULL");

			// every team gets an editable, scoped mayor role (seeded empty)
			execute("INSERT OR IGNORE INTO team_roles (team_id, name, permissions, color, position, system_key, flags) " +
				"SELECT teams.id, 'mayor', 0, 16777215, 0, 'mayor', 1 FROM teams " +
				"WHERE NOT EXISTS (SELECT 1 FROM team_roles WHERE team_roles.team_id = teams.id AND team_roles.system_key = 'mayor')");

			// bit 27 used to mean MANAGE_CAPITOL_BLOCKS and CLAIM_SUB_CLAIMS at once;
			// move sub-claims to bit 29
			execute("UPDATE team_roles SET permissions = permissions | 536870912 WHERE (permissions & 134217728) != 0");
		}

		if (tableExists("team_members") && tableExists("team_roles") && columnExists("team_roles", "system_key")) {
			execute("CREATE TABLE IF NOT EXISTS member_roles (" +
				"team_id TEXT NOT NULL," +
				"player_uuid TEXT NOT NULL," +
				"role_id INTEGER NOT NULL," +
				"PRIMARY KEY (team_id, player_uuid, role_id)," +
				"FOREIGN KEY (team_id, player_uuid) REFERENCES team_members (team_id, player_uuid) ON DELETE CASCADE," +
				"FOREIGN KEY (role_id) REFERENCES team_roles (id) ON DELETE CASCADE)");
			execute("CREATE INDEX IF NOT EXISTS idx_member_roles_role ON member_roles(role_id)");
			execute("CREATE INDEX IF NOT EXISTS idx_member_roles_player ON member_roles(player_uuid)");

			// members on a custom role keep it as an extra role, base role becomes 'member'
			execute("INSERT OR IGNORE INTO member_roles (team_id, player_uuid, role_id) " +
				"SELECT team_members.team_id, team_members.player_uuid, team_members.role_id " +
				"FROM team_members JOIN team_roles ON team_roles.id = team_members.role_id " +
				"WHERE team_roles.system_key IS NULL");
			execute("UPDATE team_members SET role_id = (" +
				"SELECT team_roles.id FROM team_roles " +
				"WHERE team_roles.team_id = team_members.team_id AND team_roles.system_key = 'member') " +
				"WHERE role_id IN (SELECT id FROM team_roles WHERE system_key IS NULL)");
		}

		setSchemaVersion(7);
	}

	@FunctionalInterface
	private interface SQLRunnable {
		void run() throws SQLException;
	}

	private static void inTransaction(SQLRunnable work) throws SQLException {
		boolean previousAutoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			work.run();
			connection.commit();
		} catch (SQLException | RuntimeException e) {
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(previousAutoCommit);
		}
	}

	private static void execute(String sql) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		}
	}

	private static void backupDatabase(Path databasePath, int fromVersion) {
		Path backup = databasePath.resolveSibling(databasePath.getFileName() + ".bak-v" + fromVersion);
		try {
			// keep the first snapshot, don't overwrite it with a half-migrated db
			if (Files.exists(backup)) return;
			Files.copy(databasePath, backup, StandardCopyOption.COPY_ATTRIBUTES);
			Capitol.LOGGER.info("Backed up capitol.db to {} before migration", backup.getFileName());
		} catch (IOException e) {
			Capitol.LOGGER.error("Failed to back up capitol.db, aborting migration", e);
			throw new RuntimeException(e);
		}
	}

	// old dbs don't have chunk attribution, so we work it out from how the
	// chunks were claimed back then (7x7 around each block on placement)
	private static void backfillCapitolBlockChunks() throws SQLException {
		if (!tableExists("capitol_blocks") || !tableExists("chunks")) return;
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery(
				 "SELECT id, team_id, dimension, x, z FROM capitol_blocks ORDER BY id")) {
			List<CapitolBlockRow> blocks = new ArrayList<>();
			while (rs.next()) {
				// block pos -> chunk pos of the chunk it sits in
				blocks.add(new CapitolBlockRow(
					rs.getLong("id"),
					rs.getString("team_id"),
					rs.getString("dimension"),
					rs.getInt("x") >> 4,
					rs.getInt("z") >> 4
				));
			}
			for (CapitolBlockRow block : blocks) {
				// chunks were claimed in a 7x7 (radius 3) around the block when placed
				try (PreparedStatement update = connection.prepareStatement(
					"UPDATE chunks SET capitol_block_id = ? " +
						"WHERE dimension = ? AND chunk_x = ? AND chunk_z = ? AND team_id = ? " +
						"AND capitol_block_id IS NULL")) {
					for (int dx = -3; dx <= 3; dx++) {
						for (int dz = -3; dz <= 3; dz++) {
							update.setLong(1, block.id);
							update.setString(2, block.dimension);
							update.setInt(3, block.chunkX + dx);
							update.setInt(4, block.chunkZ + dz);
							update.setString(5, block.teamId);
							update.executeUpdate();
						}
					}
				}
			}
		}
	}

	private record CapitolBlockRow(long id, String teamId, String dimension, int chunkX, int chunkZ) {}


	private static boolean tableExists(String table) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
			"SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
			ps.setString(1, table);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean columnExists(String table, String column) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (column.equals(rs.getString("name"))) return true;
				}
			}
		}
		return false;
	}

	private static int getSchemaVersion() throws SQLException {
		try (ResultSet rs = connection.createStatement().executeQuery("PRAGMA user_version")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	private static void setSchemaVersion(int version) throws SQLException {
		connection.createStatement().execute("PRAGMA user_version = " + version);
	}

	public static Connection getConnection() {
		return connection;
	}

	public static void closeConnection() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
				Capitol.LOGGER.info("Capitol Database closed");
			}
		} catch (SQLException e) {
			Capitol.LOGGER.error("Failed to close Capitol database", e);
		}
	}

}