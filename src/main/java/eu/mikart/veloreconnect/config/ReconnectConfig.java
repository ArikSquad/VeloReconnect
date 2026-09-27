package eu.mikart.veloreconnect.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

@Configuration
public final class ReconnectConfig {
    @Comment("Backend kick reason regex for reconnect limbo. Uses find(); leave empty to disable.")
    public String reconnectKickMessageRegex = "(?i)Server is rebooting";

    @Comment("Maximum time to keep trying before the player is disconnected, in milliseconds.")
    public long maxTimeoutMillis = 60000L;

    @Comment("Time between backend availability checks, in milliseconds.")
    public long checkIntervalMillis = 1000L;

    @Comment("Title fade-in time in milliseconds.")
    public long titleFadeInMillis = 0L;

    @Comment("Title stay time in milliseconds.")
    public long titleStayMillis = 1200L;

    @Comment("Title fade-out time in milliseconds.")
    public long titleFadeOutMillis = 0L;

    @Comment("Show titles")
    public boolean showTitle = true;

    @Comment("Queue reconnects when the proxy is busy.")
    public QueueConfig queue = new QueueConfig();

    @Comment("Proxy-side limbo visuals.")
    public VisualConfig visual = new VisualConfig();

    @Comment("Proxy-side limbo world options. Keep view-distance at 0 for the cleanest old-chunk visual hold.")
    public LimboConfig limbo = new LimboConfig();

    @Configuration
    public static final class VisualConfig {
        @Comment("Gamemode used inside proxy-side limbo: survival, creative, adventure, or spectator.")
        public String gamemode = "adventure";
    }

    @Configuration
    public static final class QueueConfig {
        @Comment("Enable reconnect queueing when the proxy has enough online players.")
        public boolean enabled = false;

        @Comment("Start queueing reconnects when online player count is at or above this value.")
        public int onlineThreshold = 10;

        @Comment("Maximum players allowed to reconnect during one batch window.")
        public int batchSize = 2;

        @Comment("Time between reconnect batch windows, in milliseconds.")
        public long batchIntervalMillis = 2000L;
    }

    @Configuration
    public static final class LimboConfig {
        @Comment("Virtual dimension used by LimboAPI: OVERWORLD, NETHER, or THE_END.")
        public String dimension = "OVERWORLD";

        @Comment("Spawn X used by the internal limbo world.")
        public double x = 0.0D;

        @Comment("Spawn Y used by the internal limbo world.")
        public double y = 64.0D;

        @Comment("Spawn Z used by the internal limbo world.")
        public double z = 0.0D;

        @Comment("Spawn yaw used by the internal limbo world.")
        public float yaw = 0.0F;

        @Comment("Spawn pitch used by the internal limbo world.")
        public float pitch = 0.0F;

        @Comment("LimboAPI read timeout in seconds.")
        public int readTimeoutSeconds = 120;

        @Comment("Virtual limbo view distance. Use 0 to keep the client's previous chunks visible.")
        public int viewDistance = 0;

        @Comment("Virtual limbo simulation distance. Use 0 to avoid visible world updates.")
        public int simulationDistance = 0;
    }
}
