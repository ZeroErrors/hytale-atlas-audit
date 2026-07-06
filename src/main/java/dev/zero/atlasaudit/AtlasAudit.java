package dev.zero.atlasaudit;

import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.trail.config.Trail;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AtlasAudit {

    static final String REPORT_FILE = "atlas-audit-report.txt";

    // GraphicsDevice refuses to launch below 8192. The next common tier is 16384.
    private static final int MIN_SPEC_LIMIT = 8192;
    private static final int HIGH_END_LIMIT = 16384;
    private static final int TOP_TEXTURES = 10;

    private static final int ITEM_ICON_ATLAS_WIDTH = 8192;
    private static final int ITEM_ICON_ATLAS_WIDTH_LEGACY = 2048;
    private static final String ITEM_ICON_ATLAS_WIDE_SINCE = "0.6.0-pre.7";

    private static final String[] CHARACTER_ROOTS = {
        "Characters/", "NPC/", "NPCs/", "Items/", "Consumable/", "Resources/", "VFX/", "Trailer/"
    };

    private AtlasAudit() {
    }

    @Nonnull
    static Report run() {
        var winning = new HashMap<String, CommonAssetRegistry.PackAsset>();
        for (var packAssets : CommonAssetRegistry.getAllAssets()) {
            if (!packAssets.isEmpty()) {
                var pa = packAssets.getLast();
                winning.put(pa.asset().getName(), pa);
            }
        }

        var entity = new Atlas("Model/Entity", 8192, 256, 16, false, 0, Dedupe.HASH, false);
        var blocks = new Atlas("Blocks", 8192, 512, 16, true, 0, Dedupe.HASH, true);
        var itemIcons = new Atlas("Item Icons", itemIconAtlasWidth(), 64, 0, false, 64, Dedupe.NAME, false);
        var fx = new Atlas("FX/Particles", 8192, 512, 1, false, 0, Dedupe.HASH, true);
        var customUi = new Atlas("Custom UI", 8192, 256, 1, false, 0, Dedupe.LOGICAL_2X, false);
        var worldMap = new Atlas("World Map", 2048, 256, 1, false, 0, Dedupe.NAME, false);

        for (var entry : winning.entrySet()) {
            var name = entry.getKey();
            if (!name.endsWith(".png")) {
                continue;
            }
            var target = routePathFiltered(name, entity, customUi, worldMap);
            if (target != null) {
                var pa = entry.getValue();
                target.offer(name, pa.pack(), pa.asset());
            }
        }

        feedBlocks(blocks, winning);
        feedItemIcons(itemIcons, winning);
        feedFx(fx, winning);

        var atlases = new ArrayList<AtlasResult>();
        for (var atlas : List.of(entity, blocks, itemIcons, fx, customUi, worldMap)) {
            atlases.add(atlas.result());
        }
        return new Report(atlases);
    }

    private static int itemIconAtlasWidth() {
        try {
            var version = ManifestUtil.getVersion();
            if (version != null
                && Semver.fromString(version).compareTo(Semver.fromString(ITEM_ICON_ATLAS_WIDE_SINCE)) >= 0) {
                return ITEM_ICON_ATLAS_WIDTH;
            }
        } catch (Exception | LinkageError ignored) {
        }
        return ITEM_ICON_ATLAS_WIDTH_LEGACY;
    }

    @Nullable
    private static Atlas routePathFiltered(@Nonnull String name, @Nonnull Atlas entity,
                                           @Nonnull Atlas customUi, @Nonnull Atlas worldMap) {
        if (name.startsWith("UI/Custom/")) {
            return customUi;
        }
        if (name.startsWith("UI/WorldMap/")) {
            return worldMap;
        }
        if (name.endsWith("-Icon.png")) {
            return null;
        }
        return isCharacterRelated(name) ? entity : null;
    }

    private static boolean isCharacterRelated(@Nonnull String name) {
        for (var root : CHARACTER_ROOTS) {
            if (name.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static void feedBlocks(@Nonnull Atlas atlas, @Nonnull Map<String, CommonAssetRegistry.PackAsset> winning) {
        try {
            for (var block : BlockType.getAssetMap().getAssetMap().values()) {
                var faces = block.getTextures();
                if (faces != null) {
                    for (var face : faces) {
                        if (face == null) {
                            continue;
                        }
                        offerPath(atlas, winning, face.getUp());
                        offerPath(atlas, winning, face.getDown());
                        offerPath(atlas, winning, face.getNorth());
                        offerPath(atlas, winning, face.getSouth());
                        offerPath(atlas, winning, face.getEast());
                        offerPath(atlas, winning, face.getWest());
                    }
                }
                offerPath(atlas, winning, block.getTextureSideMask());
                offerPath(atlas, winning, block.getTransitionTexture());
                var models = block.getCustomModelTexture();
                if (models != null) {
                    for (var model : models) {
                        if (model != null) {
                            offerPath(atlas, winning, model.getTexture());
                        }
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            atlas.markSourceError();
        }
    }

    private static void feedItemIcons(@Nonnull Atlas atlas, @Nonnull Map<String, CommonAssetRegistry.PackAsset> winning) {
        try {
            for (var item : Item.getAssetMap().getAssetMap().values()) {
                offerPath(atlas, winning, item.getIcon());
            }
        } catch (Exception | LinkageError e) {
            atlas.markSourceError();
        }
    }

    private static void feedFx(@Nonnull Atlas atlas, @Nonnull Map<String, CommonAssetRegistry.PackAsset> winning) {
        try {
            for (var spawner : ParticleSpawner.getAssetMap().getAssetMap().values()) {
                var particle = spawner.getParticle();
                if (particle != null) {
                    offerPath(atlas, winning, particle.getTexture());
                }
            }
        } catch (Exception | LinkageError e) {
            atlas.markSourceError();
        }
        try {
            for (var trail : Trail.getAssetMap().getAssetMap().values()) {
                offerPath(atlas, winning, trail.getTexture());
            }
        } catch (Exception | LinkageError e) {
            atlas.markSourceError();
        }
    }

    private static void offerPath(@Nonnull Atlas atlas, @Nonnull Map<String, CommonAssetRegistry.PackAsset> winning,
                                  @Nullable String path) {
        if (path == null || !path.endsWith(".png")) {
            return;
        }
        var pa = winning.get(path.replace('\\', '/'));
        if (pa != null) {
            atlas.offer(path, pa.pack(), pa.asset());
        }
    }

    private static int roundUpToPowerOfTwo(int value) {
        if (value <= 1) {
            return 1;
        }
        return Integer.highestOneBit(value - 1) << 1;
    }

    @Nullable
    private static int[] readPngSize(@Nonnull CommonAsset asset) {
        try {
            var bytes = asset.getBlob().join();
            if (bytes == null || bytes.length < 24) {
                return null;
            }
            if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
                return null;
            }
            if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
                return null;
            }
            var width = readBigEndianInt(bytes, 16);
            var height = readBigEndianInt(bytes, 20);
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new int[]{width, height};
        } catch (Exception e) {
            return null;
        }
    }

    private static int readBigEndianInt(@Nonnull byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
            | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8)
            | (bytes[offset + 3] & 0xFF);
    }

    private enum Dedupe {
        HASH,
        NAME,
        LOGICAL_2X
    }

    private record Entry(@Nonnull String name, @Nonnull String pack, int width, int height) {
    }

    private static final class Atlas {
        private final String name;
        private final int width;
        private final int initialHeight;
        private final int paddingPerSide;
        private final boolean unpaddedTiles;
        private final int tileClamp;
        private final Dedupe dedupe;
        private final boolean estimated;
        private final Map<String, Entry> entries = new HashMap<>();
        private int unreadable;
        private boolean sourceError;

        Atlas(String name, int width, int initialHeight, int paddingPerSide, boolean unpaddedTiles,
              int tileClamp, Dedupe dedupe, boolean estimated) {
            this.name = name;
            this.width = width;
            this.initialHeight = initialHeight;
            this.paddingPerSide = paddingPerSide;
            this.unpaddedTiles = unpaddedTiles;
            this.tileClamp = tileClamp;
            this.dedupe = dedupe;
            this.estimated = estimated;
        }

        void markSourceError() {
            sourceError = true;
        }

        void offer(@Nonnull String path, @Nonnull String pack, @Nonnull CommonAsset asset) {
            var key = switch (dedupe) {
                case HASH -> asset.getHash();
                case NAME -> path;
                case LOGICAL_2X -> path.replace("@2x.png", ".png");
            };
            var existing = entries.get(key);
            if (existing != null && dedupe != Dedupe.LOGICAL_2X) {
                return;
            }

            var size = readPngSize(asset);
            if (size == null) {
                unreadable++;
                return;
            }

            var w = size[0];
            var h = size[1];
            if (tileClamp > 0) {
                var clamped = Math.min(tileClamp, Math.min(w, h));
                w = clamped;
                h = clamped;
            }
            if (existing != null && (long) w * h <= (long) existing.width() * existing.height()) {
                return;
            }
            entries.put(key, new Entry(path, pack, w, h));
        }

        private int predictHeight() {
            var list = new ArrayList<>(entries.values());
            list.sort(Comparator.comparingInt(Entry::height).reversed());

            var x = 0;
            var y = 0;
            var nextY = 0;
            var height = initialHeight;
            for (var entry : list) {
                var pad = unpaddedTiles && entry.width() == 32 && entry.height() == 32 ? 0 : paddingPerSide;
                var padTotal = pad * 2;
                if (x + entry.width() + padTotal > width) {
                    x = 0;
                    y = nextY;
                }
                var requiredHeight = y + entry.height() + padTotal;
                if (requiredHeight > height) {
                    height = Math.max(roundUpToPowerOfTwo(requiredHeight), initialHeight);
                }
                nextY = Math.max(nextY, requiredHeight);
                x += entry.width() + padTotal;
            }
            return height;
        }

        @Nonnull
        AtlasResult result() {
            var list = new ArrayList<>(entries.values());
            var height = predictHeight();

            var usage = new HashMap<String, long[]>();
            for (var entry : list) {
                var pad = unpaddedTiles && entry.width() == 32 && entry.height() == 32 ? 0 : paddingPerSide;
                var area = (long) (entry.width() + 2 * pad) * (entry.height() + 2 * pad);
                var acc = usage.computeIfAbsent(entry.pack(), k -> new long[2]);
                acc[0]++;
                acc[1] += area;
            }
            var packs = new ArrayList<PackUsage>(usage.size());
            for (var entry : usage.entrySet()) {
                packs.add(new PackUsage(entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]));
            }
            packs.sort(Comparator.comparingLong(PackUsage::paddedArea).reversed());

            list.sort(Comparator.comparingLong((Entry e) -> (long) e.width() * e.height()).reversed());
            var largest = new ArrayList<TopTexture>();
            for (var i = 0; i < Math.min(TOP_TEXTURES, list.size()); i++) {
                var entry = list.get(i);
                largest.add(new TopTexture(entry.name(), entry.pack(), entry.width(), entry.height()));
            }

            return new AtlasResult(name, width, height, entries.size(), unreadable, sourceError, estimated, packs, largest);
        }
    }

    record PackUsage(@Nonnull String pack, int count, long paddedArea) {
    }

    record TopTexture(@Nonnull String name, @Nonnull String pack, int width, int height) {
    }

    record AtlasResult(@Nonnull String name, int width, int predictedHeight, int textureCount,
                              int unreadableCount, boolean sourceError, boolean estimated,
                              @Nonnull List<PackUsage> packs, @Nonnull List<TopTexture> largest) {

        boolean overflowsMinSpec() {
            return predictedHeight > MIN_SPEC_LIMIT || width > MIN_SPEC_LIMIT;
        }

        @Nonnull
        String status() {
            if (predictedHeight > HIGH_END_LIMIT || width > HIGH_END_LIMIT) {
                return "OVERFLOW on all GPUs";
            }
            if (overflowsMinSpec()) {
                return "OVERFLOW on min-spec GPUs";
            }
            return "OK";
        }
    }

    record Report(@Nonnull List<AtlasResult> atlases) {

        boolean anyOverflowMinSpec() {
            return atlases.stream().anyMatch(AtlasResult::overflowsMinSpec);
        }

        @Nonnull
        String summaryLine() {
            var overflowing = atlases.stream().filter(AtlasResult::overflowsMinSpec).map(AtlasResult::name).toList();
            if (overflowing.isEmpty()) {
                return "Texture atlas audit: all " + atlases.size() + " atlases fit the min-spec limit";
            }
            return "Texture atlas audit: OVERFLOW on min-spec GPUs -> " + String.join(", ", overflowing);
        }

        @Nonnull
        List<String> lines() {
            var lines = new ArrayList<String>();
            lines.add("=== Texture atlas audit ===");
            lines.add("Limit: " + MIN_SPEC_LIMIT + " (min-spec), " + HIGH_END_LIMIT + " (high-end)");
            lines.add("");
            for (var atlas : atlases) {
                var header = String.format(Locale.ROOT, "%-14s %5d tex   %5d x %-5d   %s",
                    atlas.name(), atlas.textureCount(), atlas.width(), atlas.predictedHeight(), atlas.status());
                if (atlas.estimated()) {
                    header += " (estimate)";
                }
                if (atlas.unreadableCount() > 0) {
                    header += "  (" + atlas.unreadableCount() + " unreadable)";
                }
                if (atlas.sourceError()) {
                    header += "  (definitions unavailable)";
                }
                lines.add(header);
                if (atlas.textureCount() > 0) {
                    lines.add("    packs:");
                    for (var pack : atlas.packs()) {
                        lines.add(String.format(Locale.ROOT, "      %-30s %6d tex   %8.2f MPx",
                            pack.pack(), pack.count(), pack.paddedArea() / 1_000_000.0));
                    }
                    lines.add("    largest:");
                    for (var texture : atlas.largest()) {
                        lines.add(String.format(Locale.ROOT, "      %dx%d  %s [%s]",
                            texture.width(), texture.height(), texture.name(), texture.pack()));
                    }
                }
                lines.add("");
            }
            return lines;
        }

        @Nonnull
        String fullReport() {
            return String.join("\n", lines()) + "\n";
        }
    }
}
