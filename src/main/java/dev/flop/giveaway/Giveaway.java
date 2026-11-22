package dev.flop.giveaway;

import net.minecraft.client.MinecraftClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** All giveaway state: entries, countdown, and the wheel draw. */
public final class Giveaway {
    private Giveaway() {}

    public enum State { IDLE, RUNNING, DRAWING, DONE }
    public enum Result { OK, UPDATED, CLOSED, BAD_NAME, ALREADY_ENTERED, NAME_TAKEN, SUBS_ONLY, REMOVED }

    public record Entry(String userKey, String ign, String viewer, String source, boolean supporter, long time) {
        public boolean bedrock() { return ign.startsWith("."); }
    }
    public record FeedItem(String text, int color, long time) {}

    public static final double TAU = Math.PI * 2;
    public static final int MAX_SLICES = 24;
    static final SecureRandom RNG = new SecureRandom();

    /** Minecraft Java usernames: 3-16 chars, letters/digits/underscore. */
    private static final Pattern IGN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    /** Bedrock (Geyser/Floodgate) names: "." + gamertag, spaces become underscores. */
    private static final Pattern BEDROCK = Pattern.compile("^\\.[A-Za-z0-9_]{1,16}$");
    private static final Pattern IGN_IN_TEXT = Pattern.compile("!<?(\\.?[A-Za-z0-9_]{1,16})>?");
    /** Common bot commands that should never count as a name. */
    private static final Set<String> COMMANDS = Set.of(
            "discord", "socials", "uptime", "commands", "help", "lurk", "unlurk", "followage",
            "watchtime", "points", "so", "shoutout", "giveaway", "enter", "join", "rules", "ip",
            "server", "song", "sr", "specs", "mods", "pack", "age", "tiktok", "youtube", "prime");

    public static volatile State state = State.IDLE;
    public static volatile String prize = "";
    public static volatile int winnersWanted = 1;
    public static volatile long startAt, endAt;

    public static final List<Entry> entries = new CopyOnWriteArrayList<>();
    /** viewer -> their current entry */
    private static final Map<String, Entry> byUser = new ConcurrentHashMap<>();
    /** lower-case name -> viewer who owns it */
    private static final Map<String, String> nameOwner = new ConcurrentHashMap<>();
    /** viewers removed by the host can't re-enter this giveaway */
    private static final Set<String> banned = ConcurrentHashMap.newKeySet();
    public static final Deque<FeedItem> feed = new ConcurrentLinkedDeque<>();

    public static volatile Draw draw;
    public static volatile String lastJoin = "";
    public static volatile long lastJoinAt;
    private static int lastSecond = -1;

    // ---------------------------------------------------------------- control

    public static synchronized void start() {
        Config c = Config.get();
        entries.clear();
        byUser.clear();
        nameOwner.clear();
        banned.clear();
        prize = c.prize.isBlank() ? "Giveaway" : c.prize.trim();
        winnersWanted = c.winners;
        startAt = System.currentTimeMillis();
        endAt = startAt + c.durationSec * 1000L;
        draw = null;
        lastSecond = -1;
        state = State.RUNNING;
        feed("Giveaway started: " + prize, c.accent);
    }

    public static synchronized void endNow() {
        if (state == State.RUNNING) endAt = System.currentTimeMillis();
    }

    public static synchronized void cancel() {
        state = State.IDLE;
        draw = null;
        feed("Giveaway cancelled", 0xFFFF6B6B);
    }

    public static synchronized void remove(Entry e) {
        if (entries.remove(e)) {
            // The viewer stays locked out; their name is freed up.
            nameOwner.remove(e.ign().toLowerCase(Locale.ROOT));
            byUser.remove(e.userKey());
            banned.add(e.userKey());
            feed("Removed " + e.ign(), 0xFFAAAAAA);
        }
    }

    /** Throws away the last winner (e.g. not online) and spins again. */
    public static synchronized void rerollLast() {
        Draw d = draw;
        if (state != State.DONE || d == null || d.winners.isEmpty() || d.pool.isEmpty()) return;
        Entry gone = d.winners.remove(d.winners.size() - 1);
        feed("Rerolled " + gone.ign(), 0xFFAAAAAA);
        d.spin(System.currentTimeMillis());
        state = State.DRAWING;
    }

    // ---------------------------------------------------------------- entries

    /** Twitch chat message: must start with !<IGN>. */
    public static void onChat(String userKey, String viewer, String message, boolean supporter) {
        if (state != State.RUNNING || message == null) return;
        String m = message.trim();
        if (!m.startsWith("!")) return;
        String rest = m.substring(1).replace("<", "").replace(">", "").trim();
        String tok;
        if (rest.startsWith(".")) {
            tok = bedrockName(rest.substring(1));
            if (tok == null) return;
        } else {
            int sp = rest.indexOf(' ');
            tok = sp >= 0 ? rest.substring(0, sp) : rest;
            if (tok.isEmpty() || COMMANDS.contains(tok.toLowerCase(Locale.ROOT))) return;
        }
        report(tryEnter(userKey, viewer, tok, supporter, "Twitch"), viewer, tok);
    }

    /**
     * "!.Cool Gamer 12 |" -> ".Cool_Gamer_12". Gamertags can contain spaces, so the whole message
     * is used; if that is too long (extra chat text), only the first word is kept.
     */
    static String bedrockName(String raw) {
        String clean = raw.replaceAll("[^A-Za-z0-9_ ]", " ").trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return null;
        String joined = clean.replace(' ', '_');
        if (joined.length() <= 16) return "." + joined;
        int sp = clean.indexOf(' ');
        return "." + (sp > 0 ? clean.substring(0, sp) : clean);
    }

    /** Streamlabs donation: the message may contain !<IGN> anywhere. */
    public static void onDonation(String donor, String message) {
        if (state != State.RUNNING || message == null || !Config.get().donationEntries) return;
        Matcher mt = IGN_IN_TEXT.matcher(message);
        if (!mt.find()) return;
        String ign = mt.group(1);
        report(tryEnter("sl:" + donor, donor, ign, true, "Donation"), donor, ign);
    }

    private static void report(Result r, String viewer, String ign) {
        switch (r) {
            case OK -> feed("+ " + ign + "  (" + viewer + ")", Config.get().accent);
            case UPDATED -> feed(viewer + " changed name to " + ign, Config.get().accent);
            case ALREADY_ENTERED -> feed(viewer + " already entered", 0xFF8A8F98);
            case NAME_TAKEN -> feed(ign + " is already in", 0xFF8A8F98);
            case SUBS_ONLY -> feed(viewer + ": subs only", 0xFF8A8F98);
            default -> { }
        }
    }

    public static synchronized Result tryEnter(String userKey, String viewer, String ign, boolean supporter, String source) {
        if (state != State.RUNNING || System.currentTimeMillis() >= endAt) return Result.CLOSED;
        if (ign == null || !(IGN.matcher(ign).matches() || BEDROCK.matcher(ign).matches())) return Result.BAD_NAME;
        if (Config.get().subsOnly && !supporter) return Result.SUBS_ONLY;
        String uk = userKey.toLowerCase(Locale.ROOT);
        String nk = ign.toLowerCase(Locale.ROOT);
        if (banned.contains(uk)) return Result.REMOVED;
        String owner = nameOwner.get(nk);
        if (owner != null) return owner.equals(uk) ? Result.ALREADY_ENTERED : Result.NAME_TAKEN;

        long now = System.currentTimeMillis();
        Entry old = byUser.get(uk);
        Entry e = new Entry(uk, ign, viewer, source, supporter, old != null ? old.time() : now);
        Result r;
        if (old != null) {
            // Typo fix: swap their old name for the new one, keeping their spot in the list.
            nameOwner.remove(old.ign().toLowerCase(Locale.ROOT));
            int idx = entries.indexOf(old);
            if (idx >= 0) entries.set(idx, e); else entries.add(e);
            r = Result.UPDATED;
        } else {
            entries.add(e);
            r = Result.OK;
        }
        nameOwner.put(nk, uk);
        byUser.put(uk, e);
        lastJoin = ign;
        lastJoinAt = now;
        return r;
    }

    public static void feed(String text, int color) {
        feed.addFirst(new FeedItem(text, color, System.currentTimeMillis()));
        while (feed.size() > 40) feed.pollLast();
    }

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (state == State.RUNNING) {
            long left = endAt - now;
            int sec = (int) Math.ceil(left / 1000.0);
            if (sec != lastSecond && sec > 0 && sec <= 5) Sounds.tick(1.2f + (5 - sec) * 0.1f);
            lastSecond = sec;
            if (left <= 0) beginDraw(client);
        }
        Draw d = draw;
        if (state == State.DRAWING && d != null) {
            if (d.phase == Draw.Phase.SPINNING && now - d.phaseAt >= Draw.SPIN_MS) {
                d.phase = Draw.Phase.REVEAL;
                d.phaseAt = now;
                d.winners.add(d.current);
                d.pool.remove(d.current);
                Sounds.win();
                feed("Winner: " + d.current.ign(), 0xFFFFD45A);
            } else if (d.phase == Draw.Phase.REVEAL && now - d.phaseAt >= Draw.REVEAL_MS) {
                if (d.winners.size() < winnersWanted && !d.pool.isEmpty()) {
                    d.spin(now);
                } else {
                    d.phase = Draw.Phase.FINISHED;
                    state = State.DONE;
                }
            }
        }
    }

    private static synchronized void beginDraw(MinecraftClient client) {
        if (entries.isEmpty()) {
            state = State.DONE;
            draw = null;
            feed("Giveaway ended with no entries", 0xFFFF6B6B);
            return;
        }
        Draw d = new Draw(entries);
        d.spin(System.currentTimeMillis());
        draw = d;
        state = State.DRAWING;
        client.setScreen(new WheelScreen());
    }

    // ---------------------------------------------------------------- wheel draw

    public static final class Draw {
        public enum Phase { SPINNING, REVEAL, FINISHED }
        public static final long SPIN_MS = 7000, REVEAL_MS = 3800;

        public final List<Entry> pool;
        public final List<Entry> winners = new ArrayList<>();
        public List<Entry> slices = List.of();
        public int winnerSlice;
        public Entry current;
        public double startRot, endRot;
        public long phaseAt;
        public Phase phase = Phase.FINISHED;

        Draw(List<Entry> all) { pool = new ArrayList<>(all); }

        /** Current wheel angle in radians (screen space, clockwise). */
        public double rotation(long now) {
            if (phase != Phase.SPINNING) return endRot;
            double t = Math.min(1, (now - phaseAt) / (double) SPIN_MS);
            double e = 1 - Math.pow(1 - t, 4); // ease-out quart: fast start, long slow finish
            return startRot + (endRot - startRot) * e;
        }

        void spin(long now) {
            if (pool.isEmpty()) { phase = Phase.FINISHED; return; }
            current = pool.get(RNG.nextInt(pool.size()));

            List<Entry> s = new ArrayList<>();
            if (pool.size() <= MAX_SLICES) {
                s.addAll(pool);
            } else {
                // Too many names to fit: show the (already chosen) winner plus random others.
                List<Entry> others = new ArrayList<>(pool);
                others.remove(current);
                Collections.shuffle(others, RNG);
                s.addAll(others.subList(0, MAX_SLICES - 1));
                s.add(current);
            }
            Collections.shuffle(s, RNG);
            slices = s;
            winnerSlice = s.indexOf(current);

            double seg = TAU / s.size();
            startRot = ((endRot % TAU) + TAU) % TAU;
            // Pointer sits at the top (-90deg). Land somewhere inside the winner slice, not dead-centre.
            double target = -Math.PI / 2 - (winnerSlice + 0.5 + (RNG.nextDouble() - 0.5) * 0.7) * seg;
            double delta = ((target - startRot) % TAU + TAU) % TAU;
            endRot = startRot + TAU * (5 + RNG.nextInt(2)) + delta;
            phase = Phase.SPINNING;
            phaseAt = now;
        }
    }
}
