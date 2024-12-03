package launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import logging.SimpleLogger;
import network.DownloadManager;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

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

    public ChorusMinecraftManager(String versionID) {
        this.versionID = versionID;
        this.VERSION = VERSIONS.resolve(versionID);
    }

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

    public static boolean launchMinecraft(String version) {

        if (!getAvailableVersions().contains(version)) {
            return false;
        }


        if (false) {}

        return false;
    }


    private List<String> getLaunchArgs(String version) {

        return null;
    }


    public void checkAssets() {
        logger.info("Checking assets and downloading missing");
        if (DownloadManager.getAsyncValue()) {
            DownloadManager.manuallySetAsync(false);
            DownloadManager.downloadFiles(getAssetsHashesURIs(), OBJECTS, true);
            DownloadManager.manuallySetAsync(true);
        } else {
            DownloadManager.downloadFiles(getAssetsHashesURIs(), OBJECTS, true);
        }

    }

    private URI getClientURI() {
        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }


        return URI.create(jsonRoot.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString());
    }

    public static List<String> getAvailableVersions() {

        List<String> versionsList = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(MANIFEST.toURL().openStream() )) {

            JsonObject jsonRoot = gson.fromJson(reader, JsonObject.class);
            JsonArray versions = jsonRoot.getAsJsonArray("versions");
            versions.forEach(jsonElement -> versionsList.add(jsonElement.getAsJsonObject().get("id").getAsString()));

        } catch (IOException e) {
            logger.error(e);
        }

        return versionsList;

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

    private URI getAssetsURI() {
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


    private List<URI> getLibrariesURIs()  {

        JsonObject jsonRoot = new JsonObject();

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toString())) {
            jsonRoot = gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        List<URI> librariesURIs = new ArrayList<>();
        JsonArray librariesArray = jsonRoot.getAsJsonArray("libraries");


        for (JsonElement libraryElement : librariesArray) {
            JsonObject libraryObject = libraryElement.getAsJsonObject();
            JsonObject downloads = libraryObject.getAsJsonObject("downloads");

            if (downloads != null && downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                String url = artifact.get("url").getAsString();
                librariesURIs.add(URI.create(url));
            }
        }

        librariesURIs.removeIf(uri -> {
            for (String substring : getOperatingSystemsNamesInverse()) {
                if (uri.toString().contains(substring)) {
                    return true;
                }
            }
            return false;
        });


        return librariesURIs;

    }

    private List<URI> getAssetsHashesURIs() {
        List<URI> hashes = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();

        try (FileReader reader = new FileReader(INDEXES.resolve(Path.of(getVersionIndex() + ".json")).toString())) {
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
            JsonObject objects = jsonObject.getAsJsonObject("objects");

            for (Map.Entry<String, ?> entry : objects.entrySet()) {
                JsonObject item = (JsonObject) entry.getValue();
                if (item.has("hash")) {
                    String hash = item.get("hash").getAsString();

                    if (!seenHashes.contains(hash)) {
                        seenHashes.add(hash);
                        hashes.add(URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return hashes;
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
