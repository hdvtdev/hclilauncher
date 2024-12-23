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
    private Path VERSION;

    public MinecraftManager(String versionID) {
        this.VERSION = VERSIONS.resolve(versionID);
        this.versionID = versionID;
        getVersionManifestURI();
    }

    public void launch() {

        ProcessBuilder processBuilder = new ProcessBuilder(getLaunchArgs());

        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            System.out.println("Exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            logger.error(e);
        }


    }

    //FIXME: поток не успевает записать файл из-за этого происходит NullPointerException
    public void downloadAll() {
        logger.info("Starting downloading minecraft " + versionID);
        DownloadManager.downloadFile(getVersionManifestURI(), "UNPROVIDED", VERSION, false);
        DownloadManager.downloadFiles(getLibrariesURIs(), LIBRARIES, true);
        DownloadManager.downloadFile(getAssetsIndexURI(), "UNPROVIDED", INDEXES, false);
        DownloadManager.downloadFiles(getClientURI(), VERSION, false);

        //TODO
        //new File(VERSION.resolve(Path.of("client.jar")).toString()).renameTo(VERSION.resolve(Path.of(versionID + ".jar")).toFile());


    }



    public static boolean launchMinecraft(String version) {



        return false;
    }


    private List<String> getLaunchArgs() {

        List<String> args = new ArrayList<>();
        args.add("java");

        try (FileReader fileReader = new FileReader(VERSION.resolve(Path.of(versionID + ".json")).toFile())) {

            JsonObject root = gson.fromJson(fileReader, JsonObject.class);
            JsonArray jvmArguments = root.getAsJsonObject("arguments").getAsJsonArray("jvm");
            JsonArray gameArguments = root.getAsJsonObject("arguments").getAsJsonArray("game");

            for (JsonElement element : jvmArguments) {
                String arg = element.toString().replace("\"", "");
                if (arg.startsWith("-")) {
                    arg = arg.replace("${natives_directory}", VERSION.resolve(Path.of("natives")).toString());
                    if (!arg.contains("$")) {
                        args.add(arg);
                    }
                }
            }


            String separator = System.getProperty("os.name").contains("win") ? ";" : ":";
            StringBuilder cp = new StringBuilder();

            getLibrariesURIs().keySet().forEach(uri -> cp.append(LIBRARIES.resolve(uri.getPath().substring(1))).append(separator));
            cp.append(VERSION.resolve(versionID + ".jar")).append(separator);
            args.add(cp.deleteCharAt(cp.length() - 1).toString());

            args.add(root.get("mainClass").getAsString());

            for (JsonElement element : gameArguments) {
                String arg = element.toString().replace("\"", "");
                if (arg.startsWith("-") || arg.startsWith("$")) {
                    args.add(arg);
                }
            }

            //FIXME: ну эт костыль хд
            args.set(args.indexOf("${auth_player_name}"), "Hadvart_");
            args.set(args.indexOf("${version_name}"), versionID);
            args.set(args.indexOf("${game_directory}"), System.getProperty("user.dir"));
            args.set(args.indexOf("${assets_root}"), "assets");
            args.set(args.indexOf("${assets_index_name}"), "18");
            args.set(args.indexOf("${auth_uuid}"), String.valueOf(UUID.randomUUID()));
            args.set(args.indexOf("${auth_access_token}"), "00000000000000000000000000000000");
            args.set(args.indexOf("${clientid}"), "0000");
            args.set(args.indexOf("${auth_xuid}"), "0000");
            args.set(args.indexOf("${user_type}"), "mojang");
            args.set(args.indexOf("${version_type}"), "release");


        } catch (IOException e) {
            logger.error(e);
        }



        return args;
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
            this.VERSION = VERSIONS.resolve(versionID);

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
        String[] unusedOs = getOperatingSystemsNamesInverse();

        for (JsonElement libraryElement : librariesArray) {
            JsonObject libraryObject = libraryElement.getAsJsonObject();
            JsonObject downloads = libraryObject.getAsJsonObject("downloads");

            if (downloads != null && downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                String url = artifact.get("url").getAsString();

                if (!url.contains(unusedOs[0]) && !url.contains(unusedOs[1]) && !url.contains("arm")) {
                    librariesURIs.put(URI.create(url), artifact.get("sha1").getAsString());
                }

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
