package net.kaelos.ar.init;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.kaelos.ar.AutomaticRestart;
import net.kaelos.ar.data.holder.RestartDataHolder;
import net.minecraft.server.MinecraftServer;

public class SchedulerHandler {
    
    private static ScheduledFuture<?> warningTask = null;
    private static ScheduledFuture<?> restartTask = null;

    public static void startRestartScheduler(MinecraftServer server) {
        RestartDataHolder restartDataHolder = AutomaticRestart.restartDataHolder;
        
        if (restartDataHolder == null || !restartDataHolder.isEnableScript()) 
            return;

        long nextRestartMs = restartDataHolder.getNextRestartAtMs();
        if (nextRestartMs <= 0) {
            AutomaticRestart.LOGGER.warn("The restart time is not configured in the configuration file");
            return;
        }

        AutomaticRestart.LOGGER.info("The next restart is scheduled for {}", new Date(nextRestartMs));

        warningTask = AutomaticRestart.executorService.scheduleAtFixedRate(() -> server.execute(() -> {
            if (restartDataHolder != null) 
                restartDataHolder.sendWarningsAndCountdown(server);
        }), 0, 1, TimeUnit.SECONDS);
        
        schedulerExactRestart(server);
    }

    public static void schedulerExactRestart(MinecraftServer server) {
        RestartDataHolder restartDataHolder = AutomaticRestart.restartDataHolder;
        
        if (restartDataHolder == null || !restartDataHolder.isEnableScript()) 
            return;

        long nextRestartMs = restartDataHolder.getNextRestartAtMs();
        long nowMs = System.currentTimeMillis();
        long delayMs = nextRestartMs - nowMs;

        if (delayMs <= 0) {
            AutomaticRestart.LOGGER.warn("The restart time was missed");
            return;
        }

        AutomaticRestart.LOGGER.info("Exact restart scheduled in {} ms ({} seconds)", delayMs, delayMs / 1000);

        restartTask = AutomaticRestart.executorService.schedule(() -> server.execute(() -> {
            if (restartDataHolder != null && restartDataHolder.shouldRestart()) {
                AutomaticRestart.LOGGER.info("A scheduled restart is in progress");
                AutomaticRestart.requestRestart(server);
            }
        }), delayMs, TimeUnit.MILLISECONDS);
    }

    public static void cancelSchedulerTasks() {
        if (warningTask != null) {
            warningTask.cancel(false);
            warningTask = null;
        }

        if (restartTask != null) {
            restartTask.cancel(false);
            restartTask = null;
        }
    }
}
