import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Request extends Thread {
  private final String rootDirectory;

  // A map of file extensions to their corresponding content types
  private final Map<String, String> contentTypes = new HashMap<String, String>() {
    {
      put(".html", "text/html");
      put(".png", "image/png");
    }
  };

  public Request(String rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  // Handle an incoming request on the given socket
  public void handleRequest(Socket socket) throws IOException {
    try (InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream()) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

      // Read the first line of the request to get the method, path, and HTTP version
      String line = reader.readLine();
      if (line == null) {
        return;
      }
      String[] tokens = line.split(" ");
      if (tokens.length != 3) {
        return;
      }
  
      String path = tokens[1];

      // If the path is "/", serve the index.html file by default
      if (path.equals("/")) {
        path = "/index.html";
      } else if (path.startsWith("/a")) {

        // If the path starts with with a sub-directory, check if it corresponds to a
        // directory and serve the index.html file in that directory by default
        Path filePath = Paths.get(rootDirectory, "public", path.substring(1));
        if (Files.isDirectory(filePath)) {
          path = path + "/index.html";
        }
      }
      String fileName = path.substring(1); // remove leading slash to get filename
      Path filePath = Paths.get(rootDirectory, "public", fileName);

      // just to see from where the file is being served
      System.out.println("serving from: " + filePath.toAbsolutePath());


      // Check that the requested file is within the server's content directory
      // Preventing directory traversal
      if (!filePath.toAbsolutePath().normalize()
          .startsWith(Paths.get(rootDirectory).toAbsolutePath().normalize())) {
        writeErrorResponse(outputStream, "Forbidden", 403);
        return;
      }

      // If the requested file doesn't exist or is a directory, check if the path
      // corresponds to a special case (e.g., a redirect), and handle that case
      if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
        if (path.equals("/redirect")) {
          String redirectUrl = rootDirectory + "/302-redirect.png";
          String response = "HTTP/1.1 302 Found\r\n" +
              "Location: " + redirectUrl + "\r\n" +
              "\r\n";
          outputStream.write(response.getBytes());
          outputStream.flush();
        } else if (Files.isDirectory(filePath)) {
          Path indexFilePath = Paths.get(filePath.toString(), "index.html");
          if (Files.exists(indexFilePath)) {
            filePath = indexFilePath;
          } else {
            writeErrorResponse(outputStream, "Not found", 404);
            return;
          }
        } else {
          writeErrorResponse(outputStream, "Not found", 404);
          return;
        }
      }
      String extension = getFileExtension(filePath);
      String contentType = contentTypes.get(extension);
      if (contentType == null) {
        contentType = "application/octet-stream";
      }
      byte[] fileBytes = Files.readAllBytes(filePath);
      String response = "HTTP/1.1 200 OK\r\n" +
          "Content-Type: " + contentType + "\r\n" +
          "Content-Length: " + fileBytes.length + "\r\n" +
          "\r\n";
      outputStream.write(response.getBytes());
      outputStream.write(fileBytes);
      outputStream.flush();
    } catch (IOException e) {

      writeErrorResponse(socket.getOutputStream(), "Internal Server Error", 500);
      e.printStackTrace();
    } finally {
      socket.close();
    }
  }

  // writes an HTTP error response to the output stream with the given status code
  // and message
  private void writeErrorResponse(OutputStream outputStream, String message, int statusCode) throws IOException {
    String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n" +
        "Content-Type: text/plain\r\n" +
        "Content-Length: " + message.length() + "\r\n" +
        "\r\n" +
        message;
    outputStream.write(response.getBytes());
    outputStream.flush();
  }

  // extracts the file extension from a Path object representing a file by finding
  // the last occurrence of the '.' character in the file name and returning the
  // substring that follows it.
  // If the file name does not contain a '.', an empty string is returned.
  private String getFileExtension(Path path) {
    String fileName = path.getFileName().toString();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex == -1) {
      return "";
    }
    return fileName.substring(dotIndex);
  }
}
