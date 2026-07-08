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
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.exlll.configlib.YamlConfigurations;
import eu.mikart.veloreconnect.config.ReconnectConfig;
import io.github.miniplaceholders.api.MiniPlaceholders;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
    private final Set<UUID> reconnectingPlayers = ConcurrentHashMap.newKeySet();

    private ReconnectConfig config;
    private MiniPlaceholderBridge placeholders;
    private Limbo limbo;

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

    public static MiniMessage MM = MiniMessage.builder().tags(
        MiniPlaceholders.audienceGlobalPlaceholders()
    ).build();

    @Inject
    public VeloReconnectPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
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

        this.placeholders = new MiniPlaceholderBridge(proxy);
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
        if (!config.monitoredServers.contains(previousServer.getServerInfo().getName())) {
            return false;
        }

        Player player = event.getPlayer();
        reconnectingPlayers.add(player.getUniqueId());
        limbo.spawnPlayer(player, new ReconnectSession(previousServer));
        return true;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        reconnectingPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void showTitle(Player player, String titleKey, String subtitleKey, int attempt) {
        Component title = message(titleKey, attempt);
        Component subtitle = message(subtitleKey, attempt);
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
            Duration.ofMillis(config.titleFadeInMillis),
            Duration.ofMillis(config.titleStayMillis),
            Duration.ofMillis(config.titleFadeOutMillis)
        )));
    }

    private Component message(final @NotNull String key, int attempt) {
        return Component.translatable(key, Argument.string("attempt", Integer.toString(attempt)), Argument.string("max_attempts", Integer.toString(config.maxAttempts)), Argument.tagResolver(TagResolver.resolver(placeholders.resolver())));
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
            .setShouldRejoin(true)
            .setShouldRespawn(true)
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

    private final class ReconnectSession implements LimboSessionHandler {
        private final RegisteredServer targetServer;
        private final PingOptions pingOptions = PingOptions.builder()
            .timeout(Duration.ofMillis(Math.max(250L, config.retryDelayMillis)))
            .build();

        private LimboPlayer limboPlayer;
        private boolean connected = true;
        private int attempt;

        private ReconnectSession(RegisteredServer targetServer) {
            this.targetServer = targetServer;
        }

        @Override
        public void onSpawn(Limbo limbo, LimboPlayer player) {
            this.limboPlayer = player;
            this.limboPlayer.disableFalling();
            this.limboPlayer.setGameMode(gameMode());
            if (config.showRestartingTitle) {
                showTitle(player.getProxyPlayer(), "title.restarting", "subtitle.restarting", 0);
            }
            scheduleNext(config.firstRetryDelayMillis);
        }

        @Override
        public void onDisconnect() {
            this.connected = false;
            if (limboPlayer != null) {
                reconnectingPlayers.remove(limboPlayer.getProxyPlayer().getUniqueId());
            }
        }

        private void scheduleNext(long delayMillis) {
            if (!connected || limboPlayer == null) {
                return;
            }
            limboPlayer.getScheduledExecutor().schedule(this::tryReconnect, delayMillis, TimeUnit.MILLISECONDS);
        }

        private void tryReconnect() {
            if (!connected || limboPlayer == null) {
                return;
            }

            attempt++;
            Player player = limboPlayer.getProxyPlayer();

            targetServer.ping(pingOptions).whenComplete((_, exception) -> {
                if (!connected || limboPlayer == null) {
                    return;
                }

                if (exception == null) {
                    reconnectingPlayers.remove(player.getUniqueId());
                    if (config.showConnectingTitle) {
                        showTitle(player, "title.connecting", "subtitle.connecting", attempt);
                    }
                    limboPlayer.disconnect(targetServer);
                    return;
                }

                if (attempt >= config.maxAttempts) {
                    connected = false;
                    reconnectingPlayers.remove(player.getUniqueId());
                    player.disconnect(message("disconnect.failed", attempt));
                    return;
                }

                scheduleNext(config.retryDelayMillis);
            });
        }
    }
}
