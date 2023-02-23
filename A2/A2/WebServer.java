import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class WebServer {
  private final String rootDirectory;
  private final int port;

  public WebServer(String rootDirectory, int port) {
    this.rootDirectory = rootDirectory;
    this.port = port;
  }

  public void start() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(this.port)) {
      System.out.println("Server is running on port " + this.port);
      while (true) {
        Socket socket = serverSocket.accept();
        
        // Create a separate thread for each request
        Thread thread = new Thread(() -> {
          Request request = new Request(rootDirectory);
          try {
            request.handleRequest(socket);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
        thread.start();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    WebServer server = new WebServer(".", 9090);
    server.start();
  }
}
