package eu.mikart.veloreconnect;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import eu.mikart.veloreconnect.config.ReconnectConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ReconnectSession implements LimboSessionHandler {
    private final VeloReconnectPlugin plugin;
    private final RegisteredServer targetServer;

    private LimboPlayer limboPlayer;
    private boolean connected = true;
    private int attempt;
    private final ReconnectConfig config;
    private final PingOptions pingOptions;
    private final long startedAtMillis = System.currentTimeMillis();
    private final int maxChecks;

    ReconnectSession(VeloReconnectPlugin plugin, ReconnectConfig config, RegisteredServer targetServer) {
        this.plugin = plugin;
        this.config = config;
        long checkIntervalMillis = Math.max(250L, config.checkIntervalMillis);
        this.pingOptions = PingOptions.builder()
            .timeout(Duration.ofMillis(checkIntervalMillis))
            .build();
        this.maxChecks = Math.max(1, (int) Math.ceil(config.maxTimeoutMillis / (double) checkIntervalMillis));
        this.targetServer = targetServer;
    }

    private Component message(final @NotNull String key, int attempt) {
        return Component.translatable(key,
            Argument.string("attempt", Integer.toString(attempt)),
            Argument.string("max_attempts", Integer.toString(maxChecks)),
            Argument.tagResolver(TagResolver.resolver(VeloReconnectPlugin.placeholders.resolver())));
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

    @Override
    public void onSpawn(Limbo limbo, LimboPlayer player) {
        this.limboPlayer = player;
        this.limboPlayer.disableFalling();
        if (config.showTitle) {
            showTitle(player.getProxyPlayer(), "title.restarting", "subtitle.restarting", 0);
        }
        scheduleNext(config.checkIntervalMillis);
    }

    @Override
    public void onDisconnect() {
        this.connected = false;
        if (limboPlayer != null) {
            VeloReconnectPlugin.reconnectingPlayers.remove(limboPlayer.getProxyPlayer().getUniqueId());
        }
    }

    private void scheduleNext(long delayMillis) {
        if (!connected || limboPlayer == null) {
            return;
        }
        limboPlayer.getScheduledExecutor().schedule(this::tryReconnect, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    private void tryReconnect() {
        if (!connected || limboPlayer == null) {
            return;
        }

        attempt++;
        Player player = limboPlayer.getProxyPlayer();
        if (config.showTitle) {
            showTitle(player, "title.restarting", "subtitle.restarting", attempt);
        }
        if (timedOut()) {
            connected = false;
            VeloReconnectPlugin.reconnectingPlayers.remove(player.getUniqueId());
            player.disconnect(message("disconnect.failed", attempt));
            return;
        }

        targetServer.ping(pingOptions).whenComplete((_, exception) -> {
            if (!connected || limboPlayer == null) {
                return;
            }

            if (exception == null) {
                if (!plugin.tryAcquireReconnectSlot()) {
                    if (config.showTitle) {
                        showTitle(player, "title.queued", "subtitle.queued", attempt);
                    }
                    scheduleNext(config.checkIntervalMillis);
                    return;
                }

                VeloReconnectPlugin.reconnectingPlayers.remove(player.getUniqueId());
                limboPlayer.disconnect(targetServer);
                return;
            }

            if (timedOut()) {
                connected = false;
                VeloReconnectPlugin.reconnectingPlayers.remove(player.getUniqueId());
                player.disconnect(message("disconnect.failed", attempt));
                return;
            }

            scheduleNext(config.checkIntervalMillis);
        });
    }

    private boolean timedOut() {
        return System.currentTimeMillis() - startedAtMillis >= config.maxTimeoutMillis || attempt >= maxChecks;
    }
}
