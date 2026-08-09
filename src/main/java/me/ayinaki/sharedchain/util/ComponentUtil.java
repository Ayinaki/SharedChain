package me.ayinaki.sharedchain.util;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.font.FontImageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class ComponentUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    /** Font image service registered by the plugin at enable; null when disabled. */
    private static FontImageService fontImages;
    private final SharedChain plugin;

    public ComponentUtil(SharedChain plugin) {
        this.plugin = plugin;
    }

    public static void setFontImages(FontImageService service) {
        fontImages = service;
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix", "<dark_gray>[<gold>SharedChain</gold>]</dark_gray> ");
        String message = plugin.getConfig().getString("messages." + key, "Missing message: " + key);
        return applyFonts(MINI_MESSAGE.deserialize(prefix + message, resolvers));
    }

    public static Component parse(String input) {
        return parse(input, new TagResolver[0]);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return applyFonts(MINI_MESSAGE.deserialize(input, resolvers));
    }

    private static Component applyFonts(Component component) {
        return fontImages != null ? fontImages.applyFonts(component) : component;
    }
}
