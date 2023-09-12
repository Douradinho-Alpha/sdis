package pt.tecnico.distledger.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.distledger.server.domain.UserServiceImpl;
import pt.tecnico.distledger.server.domain.AdminServiceImpl;
import pt.tecnico.distledger.server.domain.CrossServerServiceImpl;
import pt.tecnico.distledger.server.domain.ServerState;

import java.io.IOException;
public class ServerMain {
    private static final String HOST = "localhost";
    private static final int NSPORT = 5001;
    public static synchronized void main(String[] args) throws IOException, InterruptedException {
        System.out.println(ServerMain.class.getSimpleName());

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
        ServerState state = new ServerState(args[1], HOST, NSPORT);
        final BindableService user = new UserServiceImpl(state);
        final BindableService admin = new AdminServiceImpl(state);
        final BindableService cross = new CrossServerServiceImpl(state);

        Server server = null;
        try {

			server = ServerBuilder.forPort(port).addService(user).addService(admin).addService(cross).build();

			server.start();
            //host(qualifier):port
            state.register(args[1], HOST, args[0]);
			System.out.println("Server started");
			System.out.println();

			// Wait until server is terminated.
			System.out.println("Press enter to shutdown");
			System.in.read();
			server.shutdown();

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (server != null)
				server.shutdown();
                state.delete(args[1], HOST, args[0]);
		}
    }
}