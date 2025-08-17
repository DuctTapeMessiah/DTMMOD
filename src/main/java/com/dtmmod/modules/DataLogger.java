package com.dtmmod.modules;

import com.dtmmod.DTMMOD;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DataLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Enum to define logging types
    public enum LogType {
        PLAYER_DETECTION,
        DIRECT_MESSAGES,
        ALL
    }

    private final Setting<LogType> logType = sgGeneral.add(new EnumSetting.Builder<LogType>()
        .name("log-type")
        .description("Select which events to log.")
        .defaultValue(LogType.ALL)
        .build()
    );

    private final Setting<Boolean> logToFile = sgGeneral.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Enables logging to files.")
        .defaultValue(true)
        .build()
    );

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Set<String> playersInRange = new HashSet<>();

    public DataLogger() {
        super(DTMMOD.CATEGORY, "DataLogger", "Logs players entering render distance & DMs to DTMMOD/logs");
    }

    @Override
    public void onActivate() {
        new File("DTMMOD/logs").mkdirs();
        playersInRange.clear();
        ChatUtils.info("DataLogger activated!");
    }

    @Override
    public void onDeactivate() {
        playersInRange.clear();
        ChatUtils.info("DataLogger deactivated!");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(logType.get() == LogType.PLAYER_DETECTION || logType.get() == LogType.ALL)) return;

        ClientWorld world = mc.world;
        ClientPlayerEntity us = mc.player;
        if (world == null || us == null) return;

        String localPlayerName = us.getName().getString();
        int renderDistance = mc.options.getViewDistance().getValue() * 16;
        double renderDistanceSquared = renderDistance * renderDistance;

        List<PlayerEntity> nearbyPlayers = world.getPlayers().stream()
            .filter(player -> player != us)
            .filter(player -> !player.getName().getString().equals(localPlayerName))
            .filter(player -> player.squaredDistanceTo(us) <= renderDistanceSquared)
            .collect(Collectors.toList());

        Set<String> currentPlayers = nearbyPlayers.stream()
            .map(player -> player.getName().getString())
            .collect(Collectors.toSet());

        // Handle players entering render
        for (PlayerEntity player : nearbyPlayers) {
            String playerName = player.getName().getString();
            if (!playersInRange.contains(playerName)) {
                double distance = Math.sqrt(us.squaredDistanceTo(player));
                String message = String.format(
                    "Player detected: %s at (%.1f, %.1f, %.1f), distance: %.1f blocks",
                    playerName, player.getX(), player.getY(), player.getZ(), distance
                );

                ChatUtils.info(message);

                if (logToFile.get()) {
                    String logMessage = String.format("[%s] %s",
                        LocalDateTime.now().format(TIMESTAMP_FORMAT), message);
                    logToPlayerFile(logMessage);
                }

                playersInRange.add(playerName);
            }
        }

        Set<String> leftPlayers = new HashSet<>(playersInRange);
        leftPlayers.removeAll(currentPlayers);

        for (String playerName : leftPlayers) {
            String message = String.format("Player left render distance: %s", playerName);
            ChatUtils.info(message);

            if (logToFile.get()) {
                String logMessage = String.format("[%s] %s",
                    LocalDateTime.now().format(TIMESTAMP_FORMAT), message);
                logToPlayerFile(logMessage);
            }

            playersInRange.remove(playerName);
        }
    }


    @EventHandler
    private void onPrivateMessage(PacketEvent.Receive event) {
        if (!(logType.get() == LogType.DIRECT_MESSAGES || logType.get() == LogType.ALL)) return;

        String messageText = null;
        String sender = null;

        if (event.packet instanceof GameMessageS2CPacket packet) {
            messageText = packet.content().getString();
        } else if (event.packet instanceof ChatMessageS2CPacket packet) {
            messageText = packet.body().content();
        } else if (event.packet instanceof ProfilelessChatMessageS2CPacket packet) {
            messageText = packet.message().getString();
        }

        if (messageText == null) return;

        if (messageText.contains(" whispers: ")) {
            String[] parts = messageText.split(" whispers: ", 2);
            if (parts.length == 2) {
                sender = parts[0].trim();
                messageText = parts[1].trim();
                String logMessage = String.format("[%s] %s -> %s",
                    LocalDateTime.now().format(TIMESTAMP_FORMAT), sender, messageText);

                if (logToFile.get()) logToDMFile(logMessage);
            }
        }
    }

    private void logToFile(String filePath, String message) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(message + "\n");
        } catch (IOException e) {
            ChatUtils.error("Failed to write to log file %s: %s", filePath, e.getMessage());
            e.printStackTrace();
        }
    }

    private void logToPlayerFile(String message) {
        String date = LocalDate.now().format(DATE_FORMAT);
        String filePath = String.format("DTMMOD/logs/%s_playerdetector.txt", date);
        logToFile(filePath, message);
    }

    private void logToDMFile(String message) {
        String date = LocalDate.now().format(DATE_FORMAT);
        String filePath = String.format("DTMMOD/logs/%s_directmessages.txt", date);
        logToFile(filePath, message);
    }
}
