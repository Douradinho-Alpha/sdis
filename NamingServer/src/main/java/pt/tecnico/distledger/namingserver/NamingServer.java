package pt.tecnico.distledger.namingserver;

import java.util.Map;
import java.io.IOException;
import java.util.HashMap;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class NamingServer {
    private Map<String, ServiceEntry> map;
    
    public NamingServer() {
        this.map = new HashMap<String, ServiceEntry>();
    }

    public boolean checkKey(String name) {
        return this.map.containsKey(name);
    }

    public void addService(String name, ServiceEntry entry) {
        this.map.put(name, entry);
    }

    public ServiceEntry getServiceEntry(String name) {
        return map.get(name);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(NamingServer.class.getSimpleName());

        // receive and print arguments
        System.out.printf("Received %d arguments%n", args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.printf("arg[%d] = %s%n", i, args[i]);
        }

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            return;
        }

        final int port = Integer.parseInt(args[0]);
        NamingState state = new NamingState();
        final BindableService impl = new NamingServerServiceImpl(state);

        // Create a new server to listen on port
        Server server = ServerBuilder.forPort(port).addService(impl).build();
        
        // Start the server
        server.start();

        // Server threads are running in the background.
        System.out.println("Server started");

        // Do not exit the main thread. Wait until server is terminated.
        server.awaitTermination();
    }

}
