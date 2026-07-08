package eu.mikart.veloreconnect;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import eu.mikart.veloreconnect.config.ReconnectConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ReconnectSession implements LimboSessionHandler {
    private final RegisteredServer targetServer;

    private LimboPlayer limboPlayer;
    private boolean connected = true;
    private int attempt;
    private final ReconnectConfig config;
    private final PingOptions pingOptions;

    ReconnectSession(ReconnectConfig config, RegisteredServer targetServer) {
        this.config = config;
        this.pingOptions = PingOptions.builder()
            .timeout(Duration.ofMillis(Math.max(250L, config.retryDelayMillis)))
            .build();
        this.targetServer = targetServer;
    }

    private Component message(final @NotNull String key, int attempt) {
        return Component.translatable(key,
            Argument.string("attempt", Integer.toString(attempt)),
            Argument.string("max_attempts", Integer.toString(config.maxAttempts)),
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
        if (config.showRestartingTitle) {
            showTitle(player.getProxyPlayer(), "title.restarting", "subtitle.restarting", 0);
        }
        scheduleNext(config.firstRetryDelayMillis);
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
                VeloReconnectPlugin.reconnectingPlayers.remove(player.getUniqueId());
                if (config.showConnectingTitle) {
                    showTitle(player, "title.connecting", "subtitle.connecting", attempt);
                }
                limboPlayer.disconnect(targetServer);
                return;
            }

            if (attempt >= config.maxAttempts) {
                connected = false;
                VeloReconnectPlugin.reconnectingPlayers.remove(player.getUniqueId());
                player.disconnect(message("disconnect.failed", attempt));
                return;
            }

            scheduleNext(config.retryDelayMillis);
        });
    }
}