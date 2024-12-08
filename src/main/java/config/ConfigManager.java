package config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import logging.SimpleLogger;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;

public class ConfigManager {

    private final Path pathToConfig;
    private final SimpleLogger logger = new SimpleLogger(true);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager(Path pathToConfig) {
        this.pathToConfig = pathToConfig;
    }

    public void setProperty(String property, String value) {
        try (FileWriter fileWriter = new FileWriter(pathToConfig.toFile())) {
            fileWriter.write(gson.toJson(Map.of(property, value)));
        } catch (IOException e) {
            logger.error(e);
        }
    }

    public String getProperty(String property) {
        try (FileReader fileReader = new FileReader(pathToConfig.toFile())) {
            JsonObject root = gson.fromJson(fileReader, JsonObject.class);
            return root.get(property).getAsString();
        } catch (IOException e) {
            logger.error(e);
        }
        logger.warn("Property " + property + " does not exist in file " + pathToConfig);
        return "property does not exist";
    }

    public void createDefaultConfig(String configName) {

        configName = configName.startsWith("/") ? configName : "/" + configName;

        try (InputStream inputStream = ConfigManager.class.getResourceAsStream(configName)) {
            if (inputStream == null) {
                logger.error(new FileNotFoundException(configName + " does not exist"));
                return;
            }
            File file = new File(Path.of(System.getProperty("user.dir")).resolve(configName).toString());
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error(e);
        }

    }







}
