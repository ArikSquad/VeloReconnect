package eu.mikart.veloreconnect;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.exlll.configlib.YamlConfigurations;
import eu.mikart.veloreconnect.config.ReconnectConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Plugin(
    id = "veloreconnect",
    authors = "ArikSquad",
    version = "1.0.0",
    name = "VeloReconnect",
    dependencies = {
        @Dependency(id = "limboapi"),
        @Dependency(id = "miniplaceholders", optional = true)
    }
)
public final class VeloReconnectPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    public static final Set<UUID> reconnectingPlayers = ConcurrentHashMap.newKeySet();

    private ReconnectConfig config;
    public static MiniPlaceholderBridge placeholders;
    private Limbo limbo;
    private final AtomicLong reconnectBatchStartedAtMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger reconnectBatchUsed = new AtomicInteger();

    private final List<Locale> supportedLocales = List.of(
        Locale.US,
        Locale.forLanguageTag("de-DE"),
        Locale.forLanguageTag("es-ES"),
        Locale.forLanguageTag("fi-FI"),
        Locale.forLanguageTag("fr-FR"),
        Locale.forLanguageTag("ja-JP"),
        Locale.forLanguageTag("pt-BR"),
        Locale.forLanguageTag("ru-RU"),
        Locale.forLanguageTag("zn-CN")
    );

    public static MiniMessage MM;

    @Inject
    public VeloReconnectPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        placeholders = new MiniPlaceholderBridge(proxy);
        MM = MiniMessage.builder().tags(TagResolver.builder().resolvers(placeholders.resolver(), TagResolver.standard()).build()).build();
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();

        MiniMessageTranslationStore store = MiniMessageTranslationStore.create(Key.key("veloreconnect:lang"), MM);
        for (Locale locale : supportedLocales) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages.messages", locale);
            store.registerAll(locale, bundle, true);
        }
        GlobalTranslator.translator().addSource(store);
        this.limbo = createLimbo();
        logger.info("VeloReconnect enabled!");
    }

    @Subscribe(priority = 78)
    public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
        event.setOnKickCallback(this::handleKick);
    }

    private boolean handleKick(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return false;
        }

        RegisteredServer previousServer = event.getServer();
        Player player = event.getPlayer();
        reconnectingPlayers.add(player.getUniqueId());
        limbo.spawnPlayer(player, new ReconnectSession(this, config, previousServer));
        return true;
    }

    boolean tryAcquireReconnectSlot() {
        if (!config.queue.enabled || proxy.getPlayerCount() < config.queue.onlineThreshold) {
            return true;
        }

        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1L, config.queue.batchIntervalMillis);
        long batchStartedAt = reconnectBatchStartedAtMillis.get();
        if (now - batchStartedAt >= intervalMillis
            && reconnectBatchStartedAtMillis.compareAndSet(batchStartedAt, now)) {
            reconnectBatchUsed.set(0);
        }

        int batchSize = Math.max(1, config.queue.batchSize);
        while (true) {
            int used = reconnectBatchUsed.get();
            if (used >= batchSize) {
                return false;
            }
            if (reconnectBatchUsed.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        reconnectingPlayers.remove(event.getPlayer().getUniqueId());
    }

    private Limbo createLimbo() {
        LimboFactory factory = (LimboFactory) proxy.getPluginManager()
            .getPlugin("limboapi")
            .flatMap(PluginContainer::getInstance)
            .orElseThrow(() -> new IllegalStateException("LimboAPI is required for proxy-side limbo reconnects."));
        Dimension dimension = Dimension.valueOf(config.limbo.dimension.toUpperCase(Locale.ROOT));
        VirtualWorld world = factory.createVirtualWorld(
            dimension,
            config.limbo.x,
            config.limbo.y,
            config.limbo.z,
            config.limbo.yaw,
            config.limbo.pitch
        );
        return factory.createLimbo(world)
            .setName("VeloReconnect")
            .setReadTimeout(config.limbo.readTimeoutSeconds)
            .setGameMode(gameMode())
            .setShouldRejoin(false)
            .setShouldRespawn(false)
            .setReducedDebugInfo(true)
            .setViewDistance(config.limbo.viewDistance)
            .setSimulationDistance(config.limbo.simulationDistance);
    }

    private GameMode gameMode() {
        return GameMode.valueOf(config.visual.gamemode.toUpperCase(Locale.ROOT));
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            this.config = YamlConfigurations.update(configPath, ReconnectConfig.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load VeloReconnect config", exception);
        }
    }
}
