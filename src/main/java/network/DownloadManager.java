package network;

import logging.SimpleLogger;
import org.jetbrains.annotations.Nullable;

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
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadManager {

    private static final SimpleLogger logger = new SimpleLogger(true);

    private static final Semaphore semaphore = new Semaphore(64, true);
    private static final AtomicLong totalBytesDownloaded = new AtomicLong(0);
    private static final AtomicLong filesLeft = new AtomicLong(0);
    private static final AtomicLong totalStartTime = new AtomicLong(System.currentTimeMillis());

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


    public static void downloadFile(URI url, @Nullable String SHA1, Path destFolder, boolean createFolderFromURI) throws InterruptedException, MalformedURLException {
        semaphore.acquire();



        String fileName = url.toURL().getFile().replace("/", "");
        Path pathToFile = destFolder.resolve(fileName);
        if (createFolderFromURI) {
            pathToFile = destFolder.resolve(url.getPath().substring(1));
        }
        if (Files.exists(pathToFile)) return;



        try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(fileName);
             FileChannel fileChannel = fileOutputStream.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(2048);
            while (readableByteChannel.read(buffer) != -1) {
                buffer.flip();
                long bytesToWrite = buffer.remaining();
                fileChannel.write(buffer);
                buffer.clear();
                totalBytesDownloaded.addAndGet(bytesToWrite);
            }

            if (SHA1 == null || SHA1.isBlank()) {
                logger.error(new IllegalArgumentException("Empty hash."));
                Files.delete(pathToFile);
            } else {
                if (!checkFileHash(pathToFile, SHA1)) {
                    logger.warn("The provided hash does not match the hash of the file " + fileName + ", you can disable hash checking by using the argument --insecure.");
                    Files.delete(pathToFile);
                }
            }

        } catch (IOException e) {
            logger.error(e);
        } finally {
            semaphore.release();
            filesLeft.decrementAndGet();
        }
    }


    public static void downloadFiles(HashMap<URI, String> urls, Path destFolder, boolean createFoldersFromURI) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        filesLeft.addAndGet(urls.size());

        for (URI url : urls.keySet()) {
            executor.submit(() -> {
                try {
                    downloadFile(url, urls.get(url), destFolder, createFoldersFromURI);
                } catch (InterruptedException | MalformedURLException e) {
                    logger.error(e);
                }
            });
        }

        executor.shutdown();
        executor.close();
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
