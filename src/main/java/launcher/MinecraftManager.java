package launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import logging.SimpleLogger;
import network.DownloadManager;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MinecraftManager {

    private static final Gson gson = new Gson();
    private static final SimpleLogger logger = new SimpleLogger(true);

    private static final Path VERSIONS = Path.of("versions");

    private static final Path ASSETS = Path.of("assets");
    private static final Path LIBRARIES = Path.of("libraries");
    private static final Path INDEXES = ASSETS.resolve("indexes");
    private static final Path OBJECTS = ASSETS.resolve("objects");

    private static final URI MANIFEST = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest.json");
    private static final String RESOURCES = "https://resources.download.minecraft.net/";

    private String versionID;
    private final Path VERSION;


    public MinecraftManager(String versionID) {
        this.VERSION = VERSIONS.resolve(versionID);
        this.versionID = versionID;
    }

    public static void main(String... aaa) throws IOException, InterruptedException {

        MinecraftManager minecraftManager = new MinecraftManager("1.21.3");
        DownloadManager.downloadFile(minecraftManager.getAssetsIndexURI(), "UNPROVIDED", INDEXES, false);
        DownloadManager.downloadFiles(minecraftManager.getAssetsURIs(), OBJECTS, true);
    }

    /*
    public void downloadAll() {
        logger.info("Starting downloading minecraft " + versionID);
        DownloadManager.downloadFile(getVersionManifestURI(), VERSION, false);
        DownloadManager.downloadFile(getAssetsURI(), INDEXES, false);
        DownloadManager.downloadFiles(getLibrariesURIs(), LIBRARIES, true);
        DownloadManager.downloadFiles(getAssetsHashesURIs(), OBJECTS, true);
        DownloadManager.downloadFile(getClientURI(), VERSION, false);
        new File(VERSION.resolve(Path.of("client.jar")).toString()).renameTo(VERSION.resolve(Path.of(versionID + ".jar")).toFile());
        checkAssets();

    }

     */

    public static boolean launchMinecraft(String version) {



        return false;
    }


    private List<String> getLaunchArgs(String version) {

        return null;
    }


    public void checkAssets() {
        logger.info("Checking assets and downloading missing");
    }

    private Map<URI, String> getClientURI() {
        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        JsonObject client = jsonRoot.getAsJsonObject("downloads").getAsJsonObject("client");
        return Map.of(URI.create(client.get("url").getAsString()), client.get("sha1").getAsString());
    }

    public static Map<String, List<String>> getAvailableVersions() {

        Map<String, List<String>> versions = new HashMap<>();

        List<String> releases = new ArrayList<>();
        List<String> snapshots = new ArrayList<>();
        List<String> oldVersions = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(MANIFEST.toURL().openStream())) {

            JsonObject jsonRoot = gson.fromJson(reader, JsonObject.class);
            for (JsonElement version : jsonRoot.getAsJsonArray("versions")) {
                String type = version.getAsJsonObject().get("type").getAsString();
                if (type.equals("release")) {
                    releases.add(version.getAsJsonObject().get("id").getAsString());
                }
                if (type.equals("snapshot")) {
                    snapshots.add(version.getAsJsonObject().get("id").getAsString());
                }
                if (type.equals("old_alpha") || type.equals("old_beta")) {
                    oldVersions.add(version.getAsJsonObject().get("id").getAsString());
                }
            }
        } catch (IOException e) {
            logger.error(e);
        }

        versions.put("releases", releases);
        versions.put("snapshots", snapshots);
        versions.put("oldVersions", oldVersions);

        return versions;
    }


    private URI getVersionManifestURI() {

        try (InputStreamReader reader = new InputStreamReader(MANIFEST.toURL().openStream() )) {

            JsonObject jsonRoot = gson.fromJson(reader, JsonObject.class);
            JsonArray versions = jsonRoot.getAsJsonArray("versions");

            for (JsonElement version : versions) {
                if (version.getAsJsonObject().get("id").getAsString().equals(versionID)) {
                    return URI.create(version.getAsJsonObject().get("url").getAsString());
                }
            }

            versionID = jsonRoot.getAsJsonObject("latest").get("release").getAsString();

            for (JsonElement version : versions) {
                if (version.getAsJsonObject().get("id").getAsString().equals(versionID)) {
                    return URI.create(version.getAsJsonObject().get("url").getAsString());
                }
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return URI.create("404");
    }

    private Map<URI, String> getAssetsURIs() {

        Map<URI, String> uris = new HashMap<>();

        try (FileReader reader = new FileReader(INDEXES.resolve(Path.of(getVersionIndex() + ".json")).toFile())) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            root.getAsJsonObject("objects").entrySet().forEach(entry -> {
                String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
                uris.put(URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash), hash);
            });
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return uris;
    }

    private URI getAssetsIndexURI() {
        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return URI.create(jsonRoot.getAsJsonObject("assetIndex").get("url").getAsString());
    }


    private String getVersionIndex() {
        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return jsonRoot.getAsJsonPrimitive("assets").getAsString();
    }


    private Map<URI, String> getLibrariesURIs()  {

        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        Map<URI, String> librariesURIs = new HashMap<>();
        JsonArray librariesArray = jsonRoot.getAsJsonArray("libraries");

        for (JsonElement libraryElement : librariesArray) {
            JsonObject libraryObject = libraryElement.getAsJsonObject();
            JsonObject downloads = libraryObject.getAsJsonObject("downloads");

            if (downloads != null && downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                librariesURIs.put(URI.create(artifact.get("url").getAsString()), artifact.get("sha1").getAsString());
            }
        }

        return librariesURIs;
    }


    private String[] getOperatingSystemsNamesInverse() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return new String[]{"macos", "linux"};
        } else if (osName.contains("mac")) {
            return new String[]{"windows", "linux"};
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return new String[]{"windows", "macos"};
        } else {
            return new String[]{"unknown"};
        }
    }

}
