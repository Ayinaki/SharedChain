package me.ayinaki.sharedchain.util;

import me.ayinaki.sharedchain.SharedChain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class ComponentUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final SharedChain plugin;

    public ComponentUtil(SharedChain plugin) {
        this.plugin = plugin;
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix", "<dark_gray>[<gold>SharedChain</gold>]</dark_gray> ");
        String message = plugin.getConfig().getString("messages." + key, "Missing message: " + key);
        return MINI_MESSAGE.deserialize(prefix + message, resolvers);
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(input, resolvers);
    }
}
