package com.dtmmod.modules;

import com.dtmmod.DTMMOD;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.MapColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

public class MapArtDownloader extends Module {
    private static final HashMap<Integer, Byte> MAP_COLORS_REVERSE = new HashMap<>();
    private final HashMap<UUID, Boolean> processedMaps = new HashMap<>();
    private List<ItemFrameEntity> itemFrames = new ArrayList<>();
    private Map<Direction, List<ItemFrameEntity>> framesByFacing = new HashMap<>();
    private int currentIndex = 0;
    private static final int MAX_PROCESS_PER_TICK = 10;
    private static final File OUTPUT_DIR = new File("DTMMOD/maps/");
    private static final File INDIVIDUAL_DIR = new File("DTMMOD/maps/individual/");
    private static final File STITCHED_DIR = new File("DTMMOD/maps/stitched/");
    private static final File BYNAME_DIR = new File("DTMMOD/maps/stitched/byname/");
    private static final File BYCLUSTER_DIR = new File("DTMMOD/maps/stitched/bycluster/");
    private static final File UUID_DATA_DIR = new File("DTMMOD/maps/stitched/uuiddata/");
    private static final File UUID_BYNAME_DIR = new File("DTMMOD/maps/stitched/uuiddata/byname/");
    private static final File UUID_BYCLUSTER_DIR = new File("DTMMOD/maps/stitched/uuiddata/bycluster/");
    private int savedFilesThisCycle = 0;
    private boolean mapsLoaded = false;
    private int ticksWaiting = 0;
    private static final int MAX_WAIT_TICKS = 200;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> stitchByName = sgGeneral.add(new BoolSetting.Builder()
        .name("stitch-by-name")
        .description("Stitch maps with the same name prefix together.")
        .defaultValue(true)
        .build()
    );

    public MapArtDownloader() {
        super(DTMMOD.CATEGORY, "Mapart Downloader",
            "Downloads map art from item frames and stitches connected frames in DTMBOT/maps.");
        initializeMapColors();
    }

    private void initializeMapColors() {
        try {
            Field f = MapColor.class.getDeclaredField("COLORS");
            f.setAccessible(true);
            MapColor[] mapColors = (MapColor[]) f.get(null);
            for (MapColor mc : mapColors) {
                if (mc == null) continue;
                for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                    int renderColor = mc.getRenderColor(brightness);
                    byte colorByte = mc.getRenderColorByte(brightness);
                    MAP_COLORS_REVERSE.put(renderColor, colorByte);
                }
            }
            MAP_COLORS_REVERSE.put(0xff000000, (byte) 0);
        } catch (Exception e) {
        }
    }

    @Override
    public void onActivate() {
        processedMaps.clear();
        itemFrames = new ArrayList<>();
        framesByFacing = new HashMap<>();
        currentIndex = 0;
        savedFilesThisCycle = 0;
        mapsLoaded = false;
        ticksWaiting = 0;

        OUTPUT_DIR.mkdirs();
        INDIVIDUAL_DIR.mkdirs();
        STITCHED_DIR.mkdirs();
        BYNAME_DIR.mkdirs();
        BYCLUSTER_DIR.mkdirs();
        UUID_DATA_DIR.mkdirs();
        UUID_BYNAME_DIR.mkdirs();
        UUID_BYCLUSTER_DIR.mkdirs();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (!mapsLoaded) {
            ticksWaiting++;
            if (!areMapsLoaded()) {
                if (ticksWaiting >= MAX_WAIT_TICKS) {
                    info("Timeout waiting for maps to load after %d ticks.", MAX_WAIT_TICKS);
                    mapsLoaded = true;
                }
                return;
            }
            mapsLoaded = true;
            info("Maps loaded after %d ticks.", ticksWaiting);
        }

        if (itemFrames.isEmpty()) {
            itemFrames = new ArrayList<>(collectItemFrames(mc.world));
            info("Found %d item frames.", itemFrames.size());

            framesByFacing = new HashMap<>();
            for (ItemFrameEntity frame : itemFrames) {
                ItemStack stack = frame.getHeldItemStack();
                if (stack.getItem() == Items.FILLED_MAP) {
                    Direction facing = frame.getFacing();
                    framesByFacing.computeIfAbsent(facing, k -> new ArrayList<>()).add(frame);
                }
            }

            processClusters();
        }

        int processedThisTick = 0;
        while (currentIndex < itemFrames.size() && processedThisTick < MAX_PROCESS_PER_TICK) {
            ItemFrameEntity frame = itemFrames.get(currentIndex);
            ItemStack stack = frame.getHeldItemStack();
            if (stack.getItem() == Items.FILLED_MAP) {
                MapState mapState = getMapState(stack, mc.world);
                if (mapState != null) {
                    UUID mapUUID = generateMapUUID(mapState);
                    if (!processedMaps.containsKey(mapUUID)) {
                        processMap(mapState, mapUUID, stack);
                        processedMaps.put(mapUUID, true);
                    }
                }
            }
            currentIndex++;
            processedThisTick++;
        }

        if (currentIndex >= itemFrames.size()) {
            info("Finished processing cycle. Saved %d files.", savedFilesThisCycle);
            itemFrames = new ArrayList<>();
            framesByFacing = new HashMap<>();
            currentIndex = 0;
            savedFilesThisCycle = 0;
            mapsLoaded = false;
            ticksWaiting = 0;
        }
    }

    private boolean areMapsLoaded() {
        for (ItemFrameEntity frame : collectItemFrames(mc.world)) {
            ItemStack stack = frame.getHeldItemStack();
            if (stack.getItem() == Items.FILLED_MAP) {
                MapState mapState = getMapState(stack, mc.world);
                if (mapState == null || mapState.colors == null) return false;
            }
        }
        return true;
    }

    private List<ItemFrameEntity> collectItemFrames(ClientWorld world) {
        Vec3d playerPos = mc.player.getPos();
        double range = mc.options.getViewDistance().getValue() * 16.0;
        Box box = new Box(playerPos.x - range, playerPos.y - range, playerPos.z - range,
            playerPos.x + range, playerPos.y + range, playerPos.z + range);

        List<ItemFrameEntity> frames = new ArrayList<>();
        frames.addAll(world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM_FRAME, box, e -> e instanceof ItemFrameEntity));
        frames.addAll(world.getEntitiesByType(net.minecraft.entity.EntityType.GLOW_ITEM_FRAME, box, e -> e instanceof ItemFrameEntity));
        return frames;
    }

    private MapState getMapState(ItemStack stack, ClientWorld world) {
        if (stack.getItem() == Items.FILLED_MAP) {
            var mapId = stack.get(net.minecraft.component.DataComponentTypes.MAP_ID);
            if (mapId != null) return world.getMapState(mapId);
        }
        return null;
    }

    private UUID generateMapUUID(MapState state) {
        byte[] colors = state.colors;
        UUID uuid = UUID.nameUUIDFromBytes(colors);
        return new UUID(uuid.getMostSignificantBits() | 1L, uuid.getLeastSignificantBits());
    }

    private void processMap(MapState mapState, UUID mapUUID, ItemStack stack) {
        String mapName = stack.getCustomName() != null
            ? stack.getCustomName().getString().replaceAll("[\\/:*?\"<>|]", "_")
            : "map_" + mapUUID;

        File outputFile = new File(INDIVIDUAL_DIR, mapName + ".png");
        if (outputFile.exists()) return;

        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 128; x++)
            for (int y = 0; y < 128; y++)
                img.setRGB(x, y, getMapPixel(mapState, x, y));

        try {
            ImageIO.write(img, "png", outputFile);
            savedFilesThisCycle++;
            info("Saved map %s", outputFile.getName());
        } catch (IOException e) {
        }
    }

    private void processClusters() {
        for (var entry : framesByFacing.entrySet()) {
            Direction facing = entry.getKey();
            List<ItemFrameEntity> frames = entry.getValue();
            info("Processing %d frames facing %s", frames.size(), facing);

            if (stitchByName.get()) {
                Map<String, List<ItemFrameEntity>> framesByPrefix = new HashMap<>();
                for (ItemFrameEntity frame : frames) {
                    String name = frame.getHeldItemStack().getCustomName() != null
                        ? frame.getHeldItemStack().getCustomName().getString() : "";
                    String prefix = extractPrefix(name);
                    framesByPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(frame);
                }
                for (var prefixEntry : framesByPrefix.entrySet()) {
                    List<ItemFrameEntity> prefixFrames = prefixEntry.getValue();
                    if (prefixFrames.size() <= 1) continue;
                    List<UUID> tempUUIDs = prefixFrames.stream()
                        .map(frame -> {
                            MapState mapState = getMapState(frame.getHeldItemStack(), mc.world);
                            return mapState != null ? generateMapUUID(mapState) : null;
                        })
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.toList());
                    String clusterId = prefixEntry.getKey();
                    stitchCluster(prefixFrames, facing, clusterId, BYNAME_DIR, UUID_BYNAME_DIR);
                }
            } else {
                for (var cluster : findConnectedClusters(frames, facing)) {
                    if (cluster.size() <= 1) continue;
                    String clusterId = generateClusterId(cluster, facing);
                    stitchCluster(cluster, facing, clusterId, BYCLUSTER_DIR, UUID_BYCLUSTER_DIR);
                }
            }
        }
    }

    private String extractPrefix(String name) {
        if (name == null || name.isEmpty()) return "unnamed";
        return name.split("\\s+")[0].replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void stitchCluster(List<ItemFrameEntity> cluster, Direction facing, String clusterId, File stitchedDir, File uuidDir) {
        if (cluster.isEmpty()) return;

        List<UUID> clusterUUIDs = cluster.stream()
            .map(frame -> {
                ItemStack stack = frame.getHeldItemStack();
                MapState mapState = getMapState(stack, mc.world);
                return mapState != null ? generateMapUUID(mapState) : null;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

        Set<UUID> currentSet = new HashSet<>(clusterUUIDs);

        String filePrefix = clusterId.startsWith(facing.getName() + "_") ? "stitched_" : "";
        File stitchedFile = new File(stitchedDir, filePrefix + clusterId + ".png");
        String metaName = stitchedFile.getName().replace(".png", ".txt");
        File existingMetaFile = new File(uuidDir, metaName);
        if (stitchedFile.exists()) {
            Set<UUID> existingSet = loadUUIDSet(existingMetaFile);
            if (existingSet.equals(currentSet)) {
                return;
            } else {
                if (stitchedFile.delete() && existingMetaFile.delete()) {
                    info("Deleted mismatched stitch %s", stitchedFile.getName());
                }
            }
        }

        String facingPrefix = "stitched_" + facing.getName() + "_";
        File[] potentialPartials = stitchedDir.listFiles((dir, name) -> name.startsWith(facingPrefix) && name.endsWith(".png"));
        if (potentialPartials != null) {
            for (File pngFile : potentialPartials) {
                if (pngFile.equals(stitchedFile)) continue;
                File metaFile = new File(uuidDir, pngFile.getName().replace(".png", ".txt"));
                Set<UUID> oldSet = loadUUIDSet(metaFile);
                if (!oldSet.isEmpty() && currentSet.containsAll(oldSet) && oldSet.size() < currentSet.size()) {
                    if (pngFile.delete() && metaFile.delete()) {
                        info("Deleted partial stitch %s for larger cluster %s", pngFile.getName(), stitchedFile.getName());
                    }
                }
            }
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (ItemFrameEntity frame : cluster) {
            BlockPos pos = frame.getBlockPos();
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }

        int width, height;
        if (facing == Direction.UP || facing == Direction.DOWN) {
            width = maxX - minX + 1;
            height = maxZ - minZ + 1;
        } else {
            width = (facing.getAxis() == Direction.Axis.Z) ? maxX - minX + 1 : maxZ - minZ + 1;
            height = maxY - minY + 1;
        }

        BufferedImage stitchedImage = new BufferedImage(width * 128, height * 128, BufferedImage.TYPE_INT_ARGB);

        for (ItemFrameEntity frame : cluster) {
            ItemStack stack = frame.getHeldItemStack();
            MapState mapState = getMapState(stack, mc.world);
            if (mapState == null) continue;

            BlockPos pos = frame.getBlockPos();
            int gridX, gridY;

            if (facing == Direction.UP) {
                gridX = pos.getX() - minX;
                gridY = pos.getZ() - minZ;
            }
            else if (facing == Direction.DOWN) {
                gridX = pos.getX() - minX;
                gridY = maxZ - pos.getZ();
            }
            else {
                gridX = (facing.getAxis() == Direction.Axis.Z)
                    ? pos.getX() - minX
                    : pos.getZ() - minZ;
                gridY = maxY - pos.getY();
                if (facing == Direction.EAST) {
                    gridX = width - 1 - gridX;
                }
            }

            for (int x = 0; x < 128; x++)
                for (int y = 0; y < 128; y++)
                    stitchedImage.setRGB(gridX * 128 + x, gridY * 128 + y, getMapPixel(mapState, x, y));
        }

        try {
            ImageIO.write(stitchedImage, "PNG", stitchedFile);
            savedFilesThisCycle++;
            info("Saved stitched map %s", stitchedFile.getName());
            File metaFile = new File(uuidDir, metaName);
            saveUUIDSet(metaFile, clusterUUIDs);
        } catch (IOException e) {
        }
    }

    private Set<UUID> loadUUIDSet(File metaFile) {
        if (!metaFile.exists()) return Collections.emptySet();
        try {
            List<String> lines = Files.readAllLines(metaFile.toPath(), StandardCharsets.UTF_8);
            Set<UUID> set = new HashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    set.add(UUID.fromString(trimmed));
                }
            }
            return set;
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    private void saveUUIDSet(File metaFile, List<UUID> uuids) {
        try {
            List<String> lines = uuids.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.toList());
            Files.write(metaFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    private List<List<ItemFrameEntity>> findConnectedClusters(List<ItemFrameEntity> frames, Direction facing) {
        List<List<ItemFrameEntity>> clusters = new ArrayList<>();
        Set<ItemFrameEntity> visited = new HashSet<>();

        for (ItemFrameEntity start : frames) {
            if (visited.contains(start)) continue;
            List<ItemFrameEntity> cluster = new ArrayList<>();
            Queue<ItemFrameEntity> queue = new LinkedList<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                ItemFrameEntity current = queue.poll();
                cluster.add(current);
                BlockPos currentPos = current.getBlockPos();

                for (ItemFrameEntity other : frames) {
                    if (visited.contains(other)) continue;
                    BlockPos otherPos = other.getBlockPos();
                    if (Math.abs(currentPos.getX() - otherPos.getX()) +
                        Math.abs(currentPos.getY() - otherPos.getY()) +
                        Math.abs(currentPos.getZ() - otherPos.getZ()) == 1) {
                        queue.add(other);
                        visited.add(other);
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private String generateClusterId(List<ItemFrameEntity> cluster, Direction facing) {
        StringBuilder id = new StringBuilder();
        id.append(facing.getName());
        List<UUID> mapUUIDs = cluster.stream()
            .map(frame -> {
                MapState mapState = getMapState(frame.getHeldItemStack(), mc.world);
                return mapState != null ? generateMapUUID(mapState) : null;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());
        id.append("_").append(mapUUIDs.hashCode());
        return id.toString();
    }

    private int getMapPixel(MapState mapState, int x, int y) {
        int index = x + y * 128;
        if (index < 0 || index >= mapState.colors.length) return 0x00000000;

        byte colorByte = mapState.colors[index];
        int colorIndex = colorByte & 0xFF;
        if (colorIndex == 0) return 0x00000000;

        int colorId = colorIndex >> 2;
        int brightnessIndex = colorIndex & 3;
        MapColor.Brightness brightness = MapColor.Brightness.get(brightnessIndex);
        MapColor mapColor = MapColor.get(colorId);

        return mapColor != null ? mapColor.getRenderColor(brightness) : 0x00000000;
    }
}
