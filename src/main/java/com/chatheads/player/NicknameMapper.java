package com.chatheads.player;

import com.chatheads.client.ChatHeadsClient;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Manual nickname -> player associations ("Player123" shown in chat is really Notch).
 * Stored in config/chatheads_nicknames.properties as  nickname=target  where target is an account name or a UUID.
 */
public final class NicknameMapper {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID_RE = Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");

    public static final class Target {
        public final String name;   // account name (may be a placeholder if only a UUID was given)
        public final UUID uuid;     // may be null
        Target(String name, UUID uuid) { this.name = name; this.uuid = uuid; }
    }

    private final File file;
    private final Map<String, String> map = new TreeMap<String, String>();

    public NicknameMapper(File file) {
        this.file = file;
        load();
    }

    public synchronized Target get(String lowerNick) {
        String v = map.get(lowerNick);
        return v == null ? null : parseTarget(v);
    }

    /** @return false if nickname or target is not valid. */
    public synchronized boolean put(String nick, String target) {
        if (!NAME.matcher(nick).matches()) return false;
        if (!NAME.matcher(target).matches() && !UUID_RE.matcher(target).matches()) return false;
        map.put(nick.toLowerCase(Locale.ROOT), target);
        save();
        return true;
    }

    public synchronized boolean remove(String nick) {
        boolean r = map.remove(nick.toLowerCase(Locale.ROOT)) != null;
        if (r) save();
        return r;
    }

    public synchronized Map<String, String> all() { return new TreeMap<String, String>(map); }

    private static Target parseTarget(String v) {
        if (UUID_RE.matcher(v).matches()) {
            String d = v.replace("-", "");
            UUID id = UUID.fromString(d.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            return new Target(v, id);
        }
        return new Target(v, null);
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(file), UTF8);
            Properties p = new Properties();
            p.load(r);
            for (String k : p.stringPropertyNames()) map.put(k.toLowerCase(Locale.ROOT), p.getProperty(k).trim());
        } catch (IOException e) {
            ChatHeadsClient.LOG.warn("Could not read nickname file", e);
        } finally {
            if (r != null) try { r.close(); } catch (IOException ignored) {}
        }
    }

    private void save() {
        if (file == null) return;
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(file), UTF8);
            Properties p = new Properties();
            p.putAll(map);
            p.store(w, "Chat Heads nickname mappings: nickname=accountName|uuid");
        } catch (IOException e) {
            ChatHeadsClient.LOG.warn("Could not save nickname file", e);
        } finally {
            if (w != null) try { w.close(); } catch (IOException ignored) {}
        }
    }
}
