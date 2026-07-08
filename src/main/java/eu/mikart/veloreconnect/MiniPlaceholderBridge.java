package eu.mikart.veloreconnect;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class MiniPlaceholderBridge {
    private final boolean available;

    public MiniPlaceholderBridge(ProxyServer proxy) {
        this.available = proxy.getPluginManager().isLoaded("miniplaceholders");
    }

    public TagResolver resolver() {
        if (!available) {
            return TagResolver.empty();
        }
        return MiniPlaceholders.audienceGlobalPlaceholders();
    }
}
