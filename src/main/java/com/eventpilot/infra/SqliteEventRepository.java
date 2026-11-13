package com.eventpilot.infra;

import com.eventpilot.application.EventRepository;
import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.domain.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite storage. Each event is stored as one JSON document (the aggregate is
 * always loaded and saved as a whole), next to its undo/redo history and a
 * small key-value table used for agent memory and settings.
 */
public class SqliteEventRepository implements EventRepository {

    private final String url;

    public SqliteEventRepository(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory for " + databaseFile, e);
        }
        this.url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initSchema() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "status TEXT NOT NULL, json TEXT NOT NULL, updated_at TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS history (event_id TEXT PRIMARY KEY, json TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS kv (scope TEXT NOT NULL, key TEXT NOT NULL, "
                    + "value TEXT NOT NULL, PRIMARY KEY (scope, key))");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database " + url + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Event event) {
        String sql = "INSERT INTO events (id, name, status, json, updated_at) VALUES (?, ?, ?, ?, datetime('now')) "
                + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, status = excluded.status, "
                + "json = excluded.json, updated_at = excluded.updated_at";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, event.id());
            ps.setString(2, event.name());
            ps.setString(3, event.status().name());
            ps.setString(4, Json.write(event));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save event: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Event> findById(String id) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT json FROM events WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Json.read(rs.getString(1), Event.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load event: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Event> findAll() {
        List<Event> events = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT json FROM events ORDER BY updated_at DESC")) {
            while (rs.next()) {
                events.add(Json.read(rs.getString(1), Event.class));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list events: " + e.getMessage(), e);
        }
        return events;
    }

    @Override
    public void delete(String id) {
        execute("DELETE FROM events WHERE id = ?", id);
        execute("DELETE FROM history WHERE event_id = ?", id);
        execute("DELETE FROM kv WHERE scope = ?", id);
    }

    @Override
    public CommandHistory.Snapshot loadHistory(String eventId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT json FROM history WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return CommandHistory.Snapshot.empty();
                }
                return Json.MAPPER.readValue(rs.getString(1), CommandHistory.Snapshot.class);
            }
        } catch (SQLException | JsonProcessingException e) {
            System.err.println("Warning: undo history of " + eventId + " could not be loaded: " + e.getMessage());
            return CommandHistory.Snapshot.empty();
        }
    }

    @Override
    public void saveHistory(String eventId, CommandHistory.Snapshot snapshot) {
        String sql = "INSERT INTO history (event_id, json) VALUES (?, ?) "
                + "ON CONFLICT(event_id) DO UPDATE SET json = excluded.json";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, Json.write(snapshot));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save history: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> getValue(String scope, String key) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM kv WHERE scope = ? AND key = ?")) {
            ps.setString(1, scope);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read setting: " + e.getMessage(), e);
        }
    }

    @Override
    public void putValue(String scope, String key, String value) {
        String sql = "INSERT INTO kv (scope, key, value) VALUES (?, ?, ?) "
                + "ON CONFLICT(scope, key) DO UPDATE SET value = excluded.value";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save setting: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeValue(String scope, String key) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM kv WHERE scope = ? AND key = ?")) {
            ps.setString(1, scope);
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete setting: " + e.getMessage(), e);
        }
    }

    private void execute(String sql, String arg) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
