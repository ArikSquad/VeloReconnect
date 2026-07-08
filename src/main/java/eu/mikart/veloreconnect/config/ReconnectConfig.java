package eu.mikart.veloreconnect.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;

@Configuration
public final class ReconnectConfig {
    @Comment("Backend servers where autoreconnect is enabled. Use Velocity server names.")
    public List<String> monitoredServers = List.of("survival");

    @Comment("Maximum reconnect attempts before the player is disconnected.")
    public int maxAttempts = 60;

    @Comment("Delay between reconnect attempts in milliseconds.")
    public long retryDelayMillis = 1000L;

    @Comment("Delay before first reconnect attempt in milliseconds.")
    public long firstRetryDelayMillis = 1500L;

    @Comment("Title fade-in time in milliseconds.")
    public long titleFadeInMillis = 250L;

    @Comment("Title stay time in milliseconds.")
    public long titleStayMillis = 1200L;

    @Comment("Title fade-out time in milliseconds.")
    public long titleFadeOutMillis = 250L;

    @Comment("Show a gold restarting title as soon as the backend goes down.")
    public boolean showRestartingTitle = true;

    @Comment("Show a green connecting title while the proxy retries the original backend.")
    public boolean showConnectingTitle = true;

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

        @Comment("Virtual limbo view distance. Use 0 to avoid sending replacement chunks.")
        public int viewDistance = 0;

        @Comment("Virtual limbo simulation distance. Use 0 to avoid visible world updates.")
        public int simulationDistance = 0;
    }
}
