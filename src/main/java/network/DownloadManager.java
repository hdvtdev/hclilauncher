package network;

import logging.SimpleLogger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadManager {

    protected static boolean insecure = false;

    private static final SimpleLogger logger = new SimpleLogger(true);

    private static final Semaphore semaphore = new Semaphore(64, true);
    private static final AtomicLong totalBytesDownloaded = new AtomicLong(0);
    public static final AtomicLong filesLeft = new AtomicLong(0);
    private static final AtomicLong totalStartTime = new AtomicLong(System.currentTimeMillis());
    public static final AtomicReference<String> lastDownloadedFile = new AtomicReference<>("none");

    public static double getCurrentDownloadSpeed() {

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - totalStartTime.get();

        if (elapsedTime <= 0) {
            return 0.0;
        }

        long totalBytes = totalBytesDownloaded.get();
        double speedBps = totalBytes / (elapsedTime / 1000.0);
        return speedBps / (1024.0 * 1024.0);
    }


    public static void downloadFile(URI uri, String SHA1, Path rootFolder, boolean createFoldersFromURI) throws InterruptedException, IOException {
        semaphore.acquire();

        if (filesLeft.get() <= 0) {
            filesLeft.incrementAndGet();
        }

        Path targetPath = createFoldersFromURI ? rootFolder.resolve(uri.getPath().substring(1)) : rootFolder.resolve(Path.of(uri.getPath()).getFileName());
        String fileName = targetPath.getFileName().toString();
        boolean checkHash = !insecure && !SHA1.equals("UNPROVIDED");

        if (Files.exists(targetPath) && (checkHash || checkFileHash(targetPath, SHA1))) {
            filesLeft.decrementAndGet();
            semaphore.release();
            return;
        }

        Files.createDirectories(targetPath.getParent());

        try (ReadableByteChannel readableByteChannel = Channels.newChannel(uri.toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(targetPath.toFile());
             FileChannel fileChannel = fileOutputStream.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (readableByteChannel.read(buffer) != -1) {
                buffer.flip();
                long bytesToWrite = buffer.remaining();
                fileChannel.write(buffer);
                buffer.clear();
                totalBytesDownloaded.addAndGet(bytesToWrite);
            }

            if (checkHash) {
                if (SHA1.isBlank()) {
                    logger.error(new IllegalArgumentException("Empty hash."));
                    Files.delete(targetPath);
                } else {
                    if (!checkFileHash(targetPath, SHA1)) {
                        logger.warn("The provided hash does not match the hash of the file " + fileName + ", you can disable hash checking by using the argument --insecure.");
                        Files.delete(targetPath);
                    }
                }
            } else {
                logger.warn("Ignoring SHA1 hash check for this file " + fileName);
            }

        } catch (IOException e) {
            logger.error(e);
        } finally {
            semaphore.release();
            filesLeft.decrementAndGet();
            lastDownloadedFile.set(fileName);
        }
    }

    public static Map<URI, String> check(Map<URI, String> toCheck, Path rootFolder, boolean uriFolder) {

        Map<URI, String> badHash = new HashMap<>();

        for (URI key : toCheck.keySet()) {

            Path targetPath = uriFolder ? rootFolder.resolve(key.getPath().substring(1)) : rootFolder.resolve(Path.of(key.getPath()).getFileName());
            if (!checkFileHash(targetPath, toCheck.get(key))) {
                badHash.put(key, toCheck.get(key));
            }

        }

        return badHash;
    }


    public static void downloadFiles(Map<URI, String> urls, Path destFolder, boolean createFoldersFromURI) {

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             ScheduledExecutorService scheduledThread = Executors.newSingleThreadScheduledExecutor()) {
            filesLeft.addAndGet(urls.size());

            scheduledThread.scheduleAtFixedRate(() -> {
                System.out.print("\r" + getCurrentDownloadSpeed() + "MB/s, Files left: " + filesLeft.get() + ", Latest downloaded file: " + lastDownloadedFile.get());
                if (filesLeft.get() == 0) {
                    executor.shutdownNow();
                    totalBytesDownloaded.set(0);
                    totalStartTime.set(0);
                    scheduledThread.shutdownNow();
                }
            }, 500, 500, TimeUnit.MILLISECONDS);


            for (URI url : urls.keySet()) {
                executor.submit(() -> {
                    try {
                        downloadFile(url, urls.get(url), destFolder, createFoldersFromURI);
                    } catch (InterruptedException | IOException e) {
                        logger.error(e);
                    }
                });
            }

            if (!scheduledThread.awaitTermination(15, TimeUnit.SECONDS)) {
                downloadFiles(check(urls, destFolder, createFoldersFromURI), destFolder, createFoldersFromURI);
            }
            System.out.println("\nDownload finished");


        } catch (InterruptedException e) {
            logger.error(e);
        }

    }


    public static boolean checkFileHash(Path filePath, String expectedHash) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");

            try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(filePath)) {
                ByteBuffer buffer = ByteBuffer.allocate(2048);
                while (fileChannel.read(buffer) != -1) {
                    buffer.flip();
                    messageDigest.update(buffer);
                    buffer.clear();
                }
            }

            byte[] fileHashBytes = messageDigest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : fileHashBytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString().equalsIgnoreCase(expectedHash);
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error(e);
        }

        return false;
    }

}
