package net.kaelos.ar.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import net.kaelos.ar.AutomaticRestart;

public class ConfigManager {
    
    private static final String CONFIG_NAME = AutomaticRestart.MOD_ID + ".yml";

    public void writeConfig() throws IOException {
        FileOutputStream config = new FileOutputStream("./config/" + CONFIG_NAME);
        Objects.requireNonNull(AutomaticRestart.class.getResourceAsStream("/" + CONFIG_NAME)).transferTo(config);
        config.close();
    }

    public Config loadConfig() throws IOException {
        return new ObjectMapper(new YAMLFactory()).readValue(new File("./config/" + CONFIG_NAME), Config.class);
    }

    public void readConfig() throws IOException {
        if (!Files.isDirectory(Paths.get("./config"))) {
            Files.createDirectory(Paths.get("./config"));
        }

        if (!Files.exists(Paths.get("./config/" + CONFIG_NAME))) {
            writeConfig();
        }

        loadConfig().setup();
    }
}
