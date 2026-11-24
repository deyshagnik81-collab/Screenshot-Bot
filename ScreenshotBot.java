import java.io.ByteArrayOutputStream;
		import java.io.IOException;
		import java.io.InputStream;
		import java.net.URI;
		import java.net.http.HttpClient;
		import java.net.http.HttpRequest;
		import java.net.http.HttpResponse;
		import java.nio.charset.StandardCharsets;
		import java.nio.file.*;
		import java.time.Duration;
public class screenshotbot {


		// TODO Auto-generated method stub
		

	

		    private static final String BOT_TOKEN = "Write Your Bot Token Here";
		    private static final String CHAT_ID = "Write the iD";

		    // Windows default: C:\Users\YourName\Pictures\Screenshots
		    // macOS default: replace "Pictures", "Screenshots" with "Desktop"
		    private static final Path SCREENSHOT_DIR = Paths.get(
		   "C:/Screenshots"
		    );

		    public static void main(String[] args) throws Exception {
		        if (!Files.isDirectory(SCREENSHOT_DIR)) {
		            System.err.println("Folder not found: " + SCREENSHOT_DIR);
		            return;
		        }

		        HttpClient client = HttpClient.newBuilder()
		                .connectTimeout(Duration.ofSeconds(10))
		                .build();

		        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
		            SCREENSHOT_DIR.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
		            System.out.println("Watching for new screenshots in: " + SCREENSHOT_DIR);

		            while (true) {
		                WatchKey key = watcher.take(); // Sleeps until OS notifies of a new file

		                for (WatchEvent<?> event : key.pollEvents()) {
		                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
		                        continue;
		                    }

		                    Path fileName = (Path) event.context();
		                    String lower = fileName.toString().toLowerCase();
		                    if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
		                        continue;
		                    }

		                    Path fullPath = SCREENSHOT_DIR.resolve(fileName);
		                    if (waitForFileReady(fullPath)) {
		                        sendToTelegram(client, fullPath);
		                    }
		                }

		                if (!key.reset()) {
		                    break;
		                }
		            }
		        }
		    }

		    // Waits until the OS finishes writing the screenshot and releases the file lock
		    private static boolean waitForFileReady(Path file) throws InterruptedException {
		        long previousSize = -1;
		        for (int attempt = 0; attempt < 20; attempt++) {
		            Thread.sleep(250);
		            if (!Files.exists(file)) {
		                return false;
		            }
		            try {
		                long currentSize = Files.size(file);
		                if (currentSize > 0 && currentSize == previousSize) {
		                    try (InputStream ignored = Files.newInputStream(file)) {
		                        return true; // File size is stable and readable
		                    }
		                }
		                previousSize = currentSize;
		            } catch (IOException ignored) {
		                // OS is still writing; retry on next loop
		            }
		        }
		        return false;
		    }

		    private static void sendToTelegram(HttpClient client, Path filePath) {
		        try {
		            String boundary = "----ScreenshotBot" + System.currentTimeMillis();
		            String crlf = "\r\n";

		            ByteArrayOutputStream body = new ByteArrayOutputStream();

		            // 1. Add chat_id field
		            body.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
		            body.write(("Content-Disposition: form-data; name=\"chat_id\"" + crlf + crlf)
		                    .getBytes(StandardCharsets.UTF_8));
		            body.write((CHAT_ID + crlf).getBytes(StandardCharsets.UTF_8));

		            // 2. Add image file field
		            body.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
		            body.write(("Content-Disposition: form-data; name=\"photo\"; filename=\""
		                    + filePath.getFileName() + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
		            body.write(("Content-Type: application/octet-stream" + crlf + crlf)
		                    .getBytes(StandardCharsets.UTF_8));
		            body.write(Files.readAllBytes(filePath));
		            body.write(crlf.getBytes(StandardCharsets.UTF_8));

		            // 3. Closing boundary
		            body.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));

		            HttpRequest request = HttpRequest.newBuilder()
		                    .uri(URI.create("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto"))
		                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
		                    .timeout(Duration.ofSeconds(30))
		                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
		                    .build();

		            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		            if (response.statusCode() == 200) {
		                System.out.println("Sent: " + filePath.getFileName());
		            } else {
		                System.err.println("Telegram error (" + response.statusCode() + "): " + response.body());
		            }
		        } catch (Exception e) {
		            System.err.println("Failed to send " + filePath.getFileName() + ": " + e.getMessage());
		        }
		    }
		}



