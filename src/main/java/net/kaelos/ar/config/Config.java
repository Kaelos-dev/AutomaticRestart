package net.kaelos.ar.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kaelos.ar.AutomaticRestart;
import net.kaelos.ar.data.holder.RestartDataHolder;

public class Config {
    
    public Settings settings = new Settings();
    public Scheduler scheduler = new Scheduler();

    public void setup() {
        AutomaticRestart.restartDataHolder = new RestartDataHolder(this);
    }
    
    public static class Settings {
        public boolean enableScriptFile = true;

        public String pathToScript = "start.sh";
    }

    public static class Scheduler {
        public String timeZone = "Europe/Moscow";

        public List<String> restartTimes = new ArrayList<>(List.of("00:00", "12:00"));

        public int countdownSeconds = 10;

        public Map<Integer, String> warningMessages = new HashMap<>() {{
            put(600, "The server will be rebooted in 10 minutes...");
            put(300, "The server will be rebooted in 5 minutes...");
            put(180, "The server will be rebooted in 3 minutes...");
            put(60, "The server will be rebooted in 1 minutes...");
        }};

        public String countdownMessage = "The server will reboot through %s seconds";

        public String disconnectMessage = "The server reboots, please wait a couple of minutes";

        public String timeUntilRestart = "The next reboot is scheduled for";

        public String timeUntilRestartDisabled = "Automatic server reboot disabled";
    }
}
