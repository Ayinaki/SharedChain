package me.ayinaki.ayinchallenge.util;

import me.ayinaki.ayinchallenge.AyinChallenge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;

public class ComponentUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final AyinChallenge plugin;

    public ComponentUtil(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix", "<dark_gray>[<gold>AyinChallenge</gold>]</dark_gray> ");
        String message = plugin.getConfig().getString("messages." + key, "Missing message: " + key);
        return MINI_MESSAGE.deserialize(prefix + message, resolvers);
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(input, resolvers);
    }

    public static TagResolver createResolvers(Map<String, String> placeholders) {
        return TagResolver.resolver(
            placeholders.entrySet().stream()
                .map(entry -> Placeholder.parsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new)
        );
    }
}
