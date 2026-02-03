package net.kaelos.ar.command;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.mojang.brigadier.CommandDispatcher;

import net.kaelos.ar.AutomaticRestart;
import net.kaelos.ar.config.ConfigManager;
import net.kaelos.ar.data.holder.RestartDataHolder;
import net.kaelos.ar.init.SchedulerHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

public class ArCommands {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z");

    private static final MutableComponent COLOR_MOD_ID = Component.literal("AutomaticRestart").withStyle(ChatFormatting.LIGHT_PURPLE);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal(AutomaticRestart.MOD_ID)
            .then(Commands.literal("restart")
                        .requires(sourceStack -> sourceStack.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(context -> restart(context.getSource())))
                .then(Commands.literal("schedule")
                        .requires(sourceStack -> sourceStack.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(context -> getTimeUntilRestart(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(sourceStack -> sourceStack.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(context -> reload(context.getSource()))));
    }

    private static int restart(CommandSourceStack sourceStack) {
        MinecraftServer server = sourceStack.getServer();
        server.getPlayerList().saveAll();
        server.saveAllChunks(true, true, true);
        AutomaticRestart.restartRequested = true;
        AutomaticRestart.shutdownHook();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop");
        return 1;
    }

    @SuppressWarnings("null")
    private static int getTimeUntilRestart(CommandSourceStack sourceStack) {
        RestartDataHolder restartDataHolder = AutomaticRestart.restartDataHolder;

        if (restartDataHolder == null || restartDataHolder.getNextRestartAtMs() <= 0 || !restartDataHolder.isEnableScript()) {
            String msg = (restartDataHolder == null)
                ? "Automatic server reboot is disabled"
                : restartDataHolder.getTimeUntilRestartDisableText();

            sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + msg), false);
        }

        long nowMs = System.currentTimeMillis();
        assert restartDataHolder != null;
        long nextMs = restartDataHolder.getNextRestartAtMs();

        if (nextMs <= nowMs) {
            restartDataHolder.recalcSchedule();
            nextMs = restartDataHolder.getNextRestartAtMs();
            if (nextMs <= 0) {
                sourceStack.sendSuccess(() -> Component.literal(restartDataHolder.getTimeUntilRestartDisableText()), false);
            }
        }

        ZoneId zoneId = restartDataHolder.getZoneId();
        ZonedDateTime next = Instant.ofEpochMilli(nextMs).atZone(zoneId);

        long secondsLeft = Math.max(0, (nextMs - nowMs + 999) / 1000);
        String leftText = formatDuration(secondsLeft);

        if (restartDataHolder.isEnableScript()) {
            String whenText = next.format(DATE_TIME_FORMATTER);
            String msg = restartDataHolder.getTimeUntilRestartText() + " " + whenText + " (via " + leftText + ")";
            sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + msg), false);
        }

        return 1;
    }

    private static String formatDuration(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) 
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @SuppressWarnings("null")
    private static int reload(CommandSourceStack sourceStack) {
        ConfigManager configManager = new ConfigManager();

        try {
            configManager.loadConfig().setup();

            RestartDataHolder restartDataHolder = AutomaticRestart.restartDataHolder;
            if (restartDataHolder == null) {
                String nullText = "Reloaded, but holder is null";
                sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + nullText), false);
            }

            assert restartDataHolder != null;
            restartDataHolder.recalcSchedule();
            SchedulerHandler.cancelSchedulerTasks();
            SchedulerHandler.startRestartScheduler(sourceStack.getServer());

            if (restartDataHolder.getNextRestartAtMs() <= 0) {
                String emptyText = "Config reloaded, but restartTimes is empty/invalid";
                sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + emptyText), false);
            } else {
                String successText = "Config reloaded successfully";
                sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + successText), false);
            }
        } catch (IOException exception) {
            String failedText = "Failed to reload config";
            sourceStack.sendSuccess(() -> Component.literal("[").append(COLOR_MOD_ID).append("] " + failedText), false);
            AutomaticRestart.LOGGER.error("Failed to reload config file: ", exception);
        }

        return 1;
    }
}
