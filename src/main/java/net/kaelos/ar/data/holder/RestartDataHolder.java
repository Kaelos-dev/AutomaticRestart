package net.kaelos.ar.data.holder;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.kaelos.ar.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public class RestartDataHolder {
    private static final MutableComponent COLOR_MOD_ID = Component.literal("AutomaticRestart").withStyle(ChatFormatting.LIGHT_PURPLE);
    
    private final Config.Settings settings;
    private final Config.Scheduler scheduler;

    private final ZoneId zoneId;

    private final NavigableMap<Long, String> warnAtMs = new TreeMap<>();

    private long nextRestartAtMs;
    private long countdownStartAtMs;
    private long lastCountdownSecond = -1;

    private boolean triggered = false;

    public RestartDataHolder(Config config) {
        this.settings = config.settings;
        this.scheduler = config.scheduler;
        this.zoneId = ZoneId.of(scheduler.timeZone);
    }

    public boolean isEnableScript() {
        return settings.enableScriptFile;
    }

    public String getPathToScript() {
        return settings.pathToScript;
    }

    public long getNextRestartAtMs() {
        return nextRestartAtMs;
    }

    @SuppressWarnings("null")
    public final void recalcSchedule() {
        this.triggered = false;
        this.lastCountdownSecond = -1;
        this.warnAtMs.clear();

        List<LocalTime> localTimes = parseTimes(scheduler.restartTimes);
        if (localTimes.isEmpty()) {
            this.nextRestartAtMs = -1;
            this.countdownStartAtMs = -1;
            return;
        }

        long nowMs = System.currentTimeMillis();
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zoneId);
        ZonedDateTime next = null;

        for (LocalTime localTime : localTimes) {
            ZonedDateTime candidate = now.with(localTime);
            if (!candidate.isAfter(now)) 
                candidate = candidate.plusDays(1);

            if (next == null || candidate.isBefore(next)) 
                next = candidate;
        }

        assert next != null;
        this.nextRestartAtMs = next.toInstant().toEpochMilli();
        this.countdownStartAtMs = nextRestartAtMs - ((long) scheduler.countdownSeconds * 1000L);

        if (scheduler.warningMessages != null) {
            for (Map.Entry<Integer, String> entry : scheduler.warningMessages.entrySet()) {
                int secondsBefore = entry.getKey() == null ? 0 : entry.getKey();
                String msg = entry.getValue();
                long when = nextRestartAtMs - (secondsBefore * 1000L);
                warnAtMs.put(when, msg);
            }
        }
    }

    public void sendWarningsAndCountdown(MinecraftServer server) {
        if (nextRestartAtMs <= 0) return;

        long nowMs = System.currentTimeMillis();

        while (!warnAtMs.isEmpty() && nowMs >= warnAtMs.firstKey()) {
            String msg = warnAtMs.pollFirstEntry().getValue();
            if (msg != null && !msg.isBlank()) 
                server.getPlayerList().broadcastSystemMessage(Component.literal("[").append(COLOR_MOD_ID).append("] " + msg), false);
        }

        if (nowMs >= countdownStartAtMs && nowMs < nextRestartAtMs) {
            long secondsLeft = Math.max(0, (nextRestartAtMs - nowMs + 999) / 1000);
            if (secondsLeft != lastCountdownSecond) {
                lastCountdownSecond = secondsLeft;
                String text = String.format(scheduler.countdownMessage, secondsLeft);
                if (secondsLeft == 0) 
                    text = scheduler.disconnectMessage;

                server.getPlayerList().broadcastSystemMessage(Component.literal("[").append(COLOR_MOD_ID).append("] " + text), false);
            }
        }
    }

    public boolean shouldRestart() {
        if (triggered) return false;

        if (nextRestartAtMs <= 0) return false;

        long nowMs = System.currentTimeMillis();
        if (nowMs >= nextRestartAtMs) {
            triggered = true;
            return true;
        }

        return false;
    }

    private static List<LocalTime> parseTimes(List<String> raw) {
        if (raw == null) return List.of();

        return raw.stream().filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(RestartDataHolder::parseHHmm)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static LocalTime parseHHmm(String s) {
        try {
            String[] p = s.split(":");
            if (p.length != 2) 
                return null;

            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            return LocalTime.of(h, m);
        } catch (Exception exception) {
            return null;
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public String getTimeUntilRestartText() {
        return scheduler.timeUntilRestart;
    }

    public String getTimeUntilRestartDisableText() {
        return scheduler.timeUntilRestartDisabled;
    }
}
