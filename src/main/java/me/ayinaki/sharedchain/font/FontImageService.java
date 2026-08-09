package me.ayinaki.sharedchain.font;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.ayinaki.sharedchain.SharedChain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Font image support (ItemsAdder-style). PNGs dropped into
 * {@code plugins/SharedChain/font-images/} are sliced into 8px-wide glyph cells,
 * mapped to sequential private-use-area characters, and packaged into a resource
 * pack. The pack is served by a built-in HTTP server and applied to players on join.
 * <p>
 * In any chat message or plugin message, {@code %imagename%} is replaced with the
 * glyph characters so the image renders in-game (requires the resource pack).
 * <p>
 * The glyphs are merged into {@code assets/minecraft/font/default.json} (the
 * vanilla providers are preserved) because chat in 26.2 always renders with the
 * default font and ignores the component {@code font} attribute. Our bitmap
 * providers are placed before the vanilla references so they take precedence over
 * the default font's own glyphs (e.g. unifont's private-use-area coverage).
 */
public final class FontImageService implements Listener {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");
    /** Resource pack format for Minecraft 26.2 (major.minor, from version.json resource_major/minor). */
    private static final int PACK_FORMAT_MAJOR = 88;
    private static final int PACK_FORMAT_MINOR = 0;
    /**
     * Invisible char with a -1px advance, defined as a space provider in the pack.
     * 26.2 gives every bitmap glyph a +1px advance (shadow allowance), which leaves a
     * 1px gap at every cell boundary of multi-cell images. Interleaving this char
     * between glyphs cancels the +1 so cells tile seamlessly. The codepoint is in the
     * private use area, unused by the vanilla default font, and our providers are
     * declared before the vanilla references so this definition wins.
     */
    private static final char OFFSET_CHAR = '\uE0FE';
    /** End of the private use area - a safe ceiling for glyph characters. */
    private static final int MAX_CHAR = 0xF8FF;

    private final SharedChain plugin;

    private final Map<String, ImageEntry> images = new LinkedHashMap<>();
    private File imagesDir;
    private File packFile;
    private byte[] packBytes;
    private byte[] sha1;
    private String packUrl;
    private HttpServer httpServer;

    public FontImageService(SharedChain plugin) {
        this.plugin = plugin;
    }

    /** Scans the images folder, (re)generates the pack, and starts the server. Safe to call repeatedly. */
    public void load() {
        images.clear();
        if (!plugin.getConfig().getBoolean("font-images.enabled", true)) {
            plugin.getComponentLogger().info("Font images are disabled in config.");
            return;
        }

        imagesDir = new File(plugin.getDataFolder(), "font-images");
        scanImages();

        if (images.isEmpty()) {
            plugin.getComponentLogger().info("No font images found in " + imagesDir
                    + " - skipping resource pack generation. Drop PNGs there to enable images.");
            return;
        }

        generatePack();
        if (plugin.getConfig().getBoolean("font-images.pack-server.enabled", true)) {
            startServer();
        }

        plugin.getComponentLogger().info("Loaded " + images.size() + " font image(s) into the default font."
                + " Use %name% placeholders in chat and plugin messages. Pack: "
                + (packUrl != null ? packUrl : "(pack server disabled)"));
    }

    /** Re-scans, regenerates the pack, and re-applies it to everyone online. */
    public void reload() {
        shutdown();
        load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPackToPlayer(player);
        }
        // A newly added / changed death icon affects the below-name counter.
        plugin.getUIService().refreshDeathDisplayName();
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        packBytes = null;
        sha1 = null;
        packUrl = null;
        packFile = null;
    }

    /** Pushes the resource pack to a player (cached client-side via the SHA-1 hash). */
    public void applyPackToPlayer(Player player) {
        if (packUrl == null || sha1 == null) return;
        player.setResourcePack(packUrl, sha1);
    }

    public List<String> getImageNames() {
        return new ArrayList<>(images.keySet());
    }

    public String getPackUrl() {
        return packUrl;
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public boolean hasImage(String name) {
        return images.containsKey(name);
    }

    /** Rendered glyph height of an image, or -1 when the image isn't loaded. */
    public int getImageHeight(String name) {
        ImageEntry entry = images.get(name);
        return entry != null ? entry.height() : -1;
    }

    /**
     * Replaces every {@code %name%} that matches a loaded image with the glyph
     * characters (which live in the default font). The original component is
     * returned unchanged when nothing matches.
     */
    public Component applyFonts(Component component) {
        if (images.isEmpty()) return component;
        return rebuild(component);
    }

    private Component rebuild(Component component) {
        if (component instanceof TextComponent text) {
            String content = text.content();
            if (content.indexOf('%') >= 0) {
                List<Component> runs = splitToRuns(content);
                if (runs != null) {
                    List<Component> children = new ArrayList<>(runs);
                    children.addAll(text.children());
                    return text.content("").children(children);
                }
            }
        }

        List<Component> children = component.children();
        if (children.isEmpty()) return component;

        List<Component> newChildren = null;
        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            Component rebuilt = rebuild(child);
            if (rebuilt != child) {
                if (newChildren == null) newChildren = new ArrayList<>(children);
                newChildren.set(i, rebuilt);
            }
        }
        if (newChildren == null) return component;
        return component.children(newChildren);
    }

    /**
     * Splits plain text into alternating text/image runs. Returns {@code null} when
     * the text contains no known image placeholder.
     */
    private List<Component> splitToRuns(String content) {
        List<Component> runs = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(content);
        int last = 0;
        boolean found = false;
        while (matcher.find()) {
            ImageEntry entry = images.get(matcher.group(1));
            if (entry == null) continue;
            found = true;
            if (matcher.start() > last) {
                runs.add(Component.text(content.substring(last, matcher.start())));
            }
            // Plain text: the glyphs live in the default font, which chat always uses.
            // Two 26.2 bitmap-font quirks are compensated here:
            // 1. Every bitmap glyph advances contentWidth + 1 (shadow allowance), leaving
            //    a 1px gap at each cell boundary - cancelled by interleaving the
            //    -1px advance OFFSET_CHAR between glyphs.
            // 2. The glyph drop shadow would paint those gaps black - disabled with
            //    ShadowColor.none().
            runs.add(Component.text(interleaveOffsetChars(entry.chars())).shadowColor(ShadowColor.none()));
            last = matcher.end();
        }
        if (!found) return null;
        if (last < content.length()) {
            runs.add(Component.text(content.substring(last)));
        }
        return runs;
    }

    // ------------------------------------------------------------------
    // Image scanning
    // ------------------------------------------------------------------

    private void scanImages() {
        if (!imagesDir.isDirectory()) return;
        File[] files = imagesDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        YamlConfiguration overrides = loadOverrides();
        int nextChar = parseBaseChar(plugin.getConfig().getString("font-images.base-char", "\uF000"));
        int defaultAscent = plugin.getConfig().getInt("font-images.ascent", -1);

        for (File file : files) {
            String name = sanitizeName(file.getName());
            if (name.isEmpty()) {
                plugin.getComponentLogger().warn("Font image: skipping '" + file.getName() + "' - invalid name (use letters, numbers, _ or -).");
                continue;
            }
            if (images.containsKey(name)) {
                plugin.getComponentLogger().warn("Font image: duplicate name '" + name + "' - skipping '" + file.getName() + "'.");
                continue;
            }

            BufferedImage image;
            try {
                image = ImageIO.read(file);
            } catch (IOException e) {
                plugin.getComponentLogger().warn("Font image: could not read '" + file.getName() + "': " + e.getMessage());
                continue;
            }
            if (image == null) {
                plugin.getComponentLogger().warn("Font image: unsupported file '" + file.getName() + "'.");
                continue;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            if (width % 8 != 0 || height % 8 != 0) {
                plugin.getComponentLogger().warn("Font image: skipping '" + file.getName()
                        + "' (" + width + "x" + height + ") - dimensions must be multiples of 8.");
                continue;
            }
            if (width > 256 || height > 256) {
                plugin.getComponentLogger().warn("Font image: skipping '" + file.getName()
                        + "' (" + width + "x" + height + ") - max size is 256x256.");
                continue;
            }

            // Optional per-image resize from font-images.yml (width/height overrides,
            // e.g. to shrink a large logo before it is sliced into glyphs).
            int targetWidth = overrides.getInt(name + ".width", -1);
            int targetHeight = overrides.getInt(name + ".height", -1);
            if (targetWidth > 0 || targetHeight > 0) {
                if (targetWidth <= 0) targetWidth = Math.round(width * (targetHeight / (float) height));
                if (targetHeight <= 0) targetHeight = Math.round(height * (targetWidth / (float) width));
                targetWidth = clampToGrid(targetWidth);
                targetHeight = clampToGrid(targetHeight);
                if (targetWidth != width || targetHeight != height) {
                    image = resizeImage(image, targetWidth, targetHeight);
                    width = targetWidth;
                    height = targetHeight;
                    plugin.getComponentLogger().info("Font image: '" + name + "' resized to " + width + "x" + height + ".");
                }
            }

            // Optional per-image render height (font-images.yml: name: { render-height }).
            // When smaller than the image height, the client renders the full-resolution
            // image scaled down (oversampled), so a high-res source can be shown at a
            // compact size without looking pixelated.
            int renderHeight = overrides.getInt(name + ".render-height", -1);
            if (renderHeight > 0 && renderHeight < height) {
                renderHeight = Math.max(8, renderHeight);
                plugin.getComponentLogger().info("Font image: '" + name + "' renders at " + renderHeight
                        + "px tall (source " + height + "px, downscaled by the client).");
            } else {
                renderHeight = height;
            }

            int charsNeeded = width / 8;
            if (nextChar + charsNeeded - 1 > MAX_CHAR) {
                plugin.getComponentLogger().warn("Font image: skipping '" + file.getName()
                        + "' - no private-use characters left (raise base-char or remove images).");
                continue;
            }

            StringBuilder chars = new StringBuilder(charsNeeded);
            for (int i = 0; i < charsNeeded; i++) {
                chars.append((char) (nextChar + i));
            }
            nextChar += charsNeeded;

            int ascent = defaultAscent >= 0 ? defaultAscent : renderHeight;
            ascent = overrides.getInt(name + ".ascent", ascent);
            if (ascent > renderHeight) {
                plugin.getComponentLogger().warn("Font image: '" + name + "' ascent (" + ascent
                        + ") exceeds render height (" + renderHeight + "), clamping to " + renderHeight + ".");
                ascent = renderHeight;
            }
            if (ascent < 0) ascent = 0;

            byte[] pngData;
            try {
                pngData = toPngBytes(image);
            } catch (IOException e) {
                plugin.getComponentLogger().warn("Font image: could not encode '" + file.getName() + "': " + e.getMessage());
                continue;
            }

            images.put(name, new ImageEntry(name, chars.toString(), renderHeight, ascent,
                    "sharedchain:font/" + name + ".png", pngData));
        }
    }

    private YamlConfiguration loadOverrides() {
        File overridesFile = new File(plugin.getDataFolder(), "font-images.yml");
        return overridesFile.isFile() ? YamlConfiguration.loadConfiguration(overridesFile) : new YamlConfiguration();
    }

    private static String sanitizeName(String fileName) {
        String base = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
        return base.replaceAll("[^a-z0-9_-]", "_");
    }

    private static int parseBaseChar(String value) {
        if (value == null || value.isEmpty()) return 0xF000;
        // Literal "\uF000" as written in single-quoted YAML.
        if (value.length() == 6 && value.startsWith("\\u")) {
            try {
                return Integer.parseInt(value.substring(2), 16);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return value.codePointAt(0);
    }

    private static BufferedImage resizeImage(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /** Rounds to the nearest multiple of 8, clamped to the 8..256 allowed range. */
    private static int clampToGrid(int value) {
        return Math.max(8, Math.min(256, Math.round(value / 8f) * 8));
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }

    /** Interleaves the -1px advance char between glyphs so multi-cell images tile seamlessly. */
    private static String interleaveOffsetChars(String chars) {
        if (chars.length() <= 1) return chars;
        StringBuilder sb = new StringBuilder(chars.length() * 2 - 1);
        for (int i = 0; i < chars.length(); i++) {
            if (i > 0) sb.append(OFFSET_CHAR);
            sb.append(chars.charAt(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Resource pack generation
    // ------------------------------------------------------------------

    private void generatePack() {
        File dir = new File(plugin.getDataFolder(), "resource-pack");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getComponentLogger().warn("Font images: could not create " + dir);
            return;
        }
        packFile = new File(dir, "sharedchain-fonts.zip");

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(packFile)))) {
            putEntry(zip, "pack.mcmeta", buildPackMcmeta().getBytes(StandardCharsets.UTF_8));
            // Glyphs live in the default font so chat renders them; the images stay in our
            // namespace. Bitmap font "file" paths resolve relative to textures/, so
            // "sharedchain:font/x.png" must live at assets/sharedchain/textures/font/x.png.
            putEntry(zip, "assets/minecraft/font/default.json", buildFontJson().getBytes(StandardCharsets.UTF_8));
            for (ImageEntry entry : images.values()) {
                putEntry(zip, "assets/sharedchain/textures/font/" + entry.name() + ".png", entry.pngData());
            }
            // Bundled attempt-counter visuals (previously a separate custom resource
            // pack): the glyph sheet + pill that the boss bar text uses, and transparent
            // boss bar sprites so only the text shows. The matching font providers are
            // added in buildFontJson.
            if (attemptCounterEnabled()) {
                putBundled(zip, "assets/sharedchain/textures/font/bossbar/ascii.png", "font/bossbar/ascii.png");
                putBundled(zip, "assets/sharedchain/textures/font/bossbar/test2.png", "font/bossbar/test2.png");
                if (plugin.getConfig().getBoolean("font-images.attempt-counter.hide-bar", true)) {
                    putBundled(zip, "assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
                            "gui/boss_bar/yellow_background.png");
                    putBundled(zip, "assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
                            "gui/boss_bar/yellow_progress.png");
                }
            }
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Font images: failed to write resource pack: " + e.getMessage());
            return;
        }

        try {
            packBytes = Files.readAllBytes(packFile.toPath());
            sha1 = MessageDigest.getInstance("SHA-1").digest(packBytes);
        } catch (Exception e) {
            plugin.getComponentLogger().warn("Font images: failed to hash resource pack: " + e.getMessage());
        }
    }

    private static void putEntry(ZipOutputStream zip, String path, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(data);
        zip.closeEntry();
    }

    /** Writes a bundled plugin resource into the pack, skipping it if missing. */
    private void putBundled(ZipOutputStream zip, String zipPath, String resourcePath) throws IOException {
        byte[] data = bundledResource(resourcePath);
        if (data == null || data.length == 0) {
            plugin.getComponentLogger().warn("Font images: bundled resource '" + resourcePath + "' missing - skipping " + zipPath);
            return;
        }
        putEntry(zip, zipPath, data);
    }

    private byte[] bundledResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Font images: could not read bundled resource '" + path + "'", e);
            return null;
        }
    }

    private String buildPackMcmeta() {
        // 26.2 replaced the single `pack_format` int with a major.minor range
        // (min_format/max_format) - legacy pack_format-only packs are rejected by
        // the client, which shows up as a red border in the pack selection screen.
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"SharedChain font images\",\n"
                + "    \"min_format\": [" + PACK_FORMAT_MAJOR + ", " + PACK_FORMAT_MINOR + "],\n"
                + "    \"max_format\": " + PACK_FORMAT_MAJOR + "\n"
                + "  }\n"
                + "}\n";
    }

    private String buildFontJson() {
        // Our providers come FIRST so they win over the vanilla font's own glyphs
        // (the unifont reference covers the private use area too).
        List<String> providers = new ArrayList<>();
        for (ImageEntry entry : images.values()) {
            providers.add("    {\n"
                    + "      \"type\": \"bitmap\",\n"
                    + "      \"file\": \"" + entry.pngPath() + "\",\n"
                    + "      \"ascent\": " + entry.ascent() + ",\n"
                    + "      \"height\": " + entry.height() + ",\n"
                    + "      \"chars\": [\"" + escapeChars(entry.chars()) + "\"]\n"
                    + "    }");
        }
        if (attemptCounterEnabled()) {
            providers.add(buildAttemptCounterProviders());
        }
        // -1px advance char used to cancel the +1 bitmap glyph advance (see OFFSET_CHAR),
        // plus the fine-positioning advances of the bundled attempt counter when enabled.
        providers.add("    {\n"
                + "      \"type\": \"space\",\n"
                + "      \"advances\": {\n"
                + "        \"" + String.format("\\u%04x", (int) OFFSET_CHAR) + "\": -1"
                + buildSpaceAdvancesSuffix()
                + "\n      }\n"
                + "    }");
        String vanillaProviders = loadVanillaProviders();
        if (!vanillaProviders.isEmpty()) {
            providers.add(vanillaProviders);
        }

        StringBuilder sb = new StringBuilder("{\n  \"providers\": [\n");
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(providers.get(i));
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    /**
     * The bundled attempt-counter glyphs, taken from the plugin's original custom
     * resource pack: a 16x16 grid of 8px cells (the digits, the ATTEMPT label and
     * the pill pieces) mapped to \uE100-\uE1FF, plus the pill glyph \uE001. The boss
     * bar name built by UserInterfaceService#buildAttemptTitle already emits these
     * characters, so bundling them here makes the counter render for everyone.
     */
    private String buildAttemptCounterProviders() {
        return "    {\n"
                + "      \"type\": \"bitmap\",\n"
                + "      \"file\": \"sharedchain:font/bossbar/ascii.png\",\n"
                + "      \"ascent\": 2,\n"
                + "      \"height\": 8,\n"
                + "      \"chars\": [\n        " + buildAsciiChars() + "\n      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"type\": \"bitmap\",\n"
                + "      \"file\": \"sharedchain:font/bossbar/test2.png\",\n"
                + "      \"ascent\": 7,\n"
                + "      \"height\": 16,\n"
                + "      \"chars\": [\"\\uE001\"]\n"
                + "    }";
    }

    /** \uE100 through \uE1FF, 16 per row - matches the 16x16 grid of 8px cells in ascii.png. */
    private static String buildAsciiChars() {
        StringBuilder rows = new StringBuilder();
        for (int r = 0; r < 16; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < 16; c++) {
                row.append((char) (0xE100 + r * 16 + c));
            }
            if (r > 0) rows.append(",\n        ");
            rows.append('"').append(escapeChars(row.toString())).append('"');
        }
        return rows.toString();
    }

    /** Fine-positioning advances from the original attempt-counter pack, if enabled. */
    private String buildSpaceAdvancesSuffix() {
        if (!attemptCounterEnabled()) return "";
        return ", \"\\uF801\": -1, \"\\uF802\": -2, \"\\uF803\": -3, \"\\uF804\": -4,\n"
                + "        \"\\uF805\": -5, \"\\uF806\": -6, \"\\uF807\": -7, \"\\uF808\": -63,\n"
                + "        \"\\uF821\": 1, \"\\uF822\": 2, \"\\uF823\": 3, \"\\uF824\": 4,\n"
                + "        \"\\uF80A\": -512, \"\\uF80B\": -1024";
    }

    private boolean attemptCounterEnabled() {
        return plugin.getConfig().getBoolean("font-images.attempt-counter.enabled", true);
    }

    /** Extracts the providers array body from the bundled vanilla default font. */
    private String loadVanillaProviders() {
        try (InputStream in = plugin.getResource("font/vanilla-default.json")) {
            if (in == null) return "";
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end <= start) return "";
            return json.substring(start + 1, end).trim();
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Font images: could not read bundled vanilla font providers", e);
            return "";
        }
    }

    private static String escapeChars(String chars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length(); i++) {
            sb.append(String.format("\\u%04x", (int) chars.charAt(i)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Pack server
    // ------------------------------------------------------------------

    private void startServer() {
        int port = plugin.getConfig().getInt("font-images.pack-server.port", 8123);
        String override = plugin.getConfig().getString("font-images.pack-server.url", "");
        packUrl = (override == null || override.isBlank())
                ? "http://localhost:" + port + "/pack.zip"
                : override;

        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/pack.zip", this::handlePackRequest);
            httpServer.setExecutor(null);
            httpServer.start();
            plugin.getComponentLogger().info("Font image pack server listening on port " + port);
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Font images: could not start pack server on port "
                    + port + ": " + e.getMessage());
            packUrl = null;
        }
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) || packBytes == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, packBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(packBytes);
            }
        }
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyPackToPlayer(event.getPlayer());
    }

    /** Rewrites chat so {@code %imagename%} renders as the image. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Component message = event.message();
        Component replaced = applyFonts(message);
        if (replaced != message) {
            event.message(replaced);
        }
    }

    /** DIAGNOSTIC: logs what the client does with the resource pack prompt. */
    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        plugin.getComponentLogger().info("Resource pack for " + event.getPlayer().getName() + ": " + event.getStatus());
    }

    private record ImageEntry(String name, String chars, int height, int ascent, String pngPath, byte[] pngData) {
    }
}
