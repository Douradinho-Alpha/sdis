package pt.tecnico.distledger.namingserver;

import io.grpc.stub.StreamObserver;
import static io.grpc.Status.INVALID_ARGUMENT;

import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc.NamingServerServiceImplBase;

public class NamingServerServiceImpl extends NamingServerServiceImplBase{
	/** Set flag to true to print debug messages.
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;
    public NamingState server;

	public NamingServerServiceImpl(NamingState s) {
		this.server = s;
	}

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}
	
    @Override
	public synchronized void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		String name = request.getName();
    	String qualifier = request.getQualifier();
    	String address = request.getAddress();
		debug("NSI | Received REGISTER request.");
        try {
			// Send a single response through the stream.
			int index = server.register(name, qualifier, address);
			debug("NSI | Server registered. Preparing response...");
            RegisterResponse response = RegisterResponse.newBuilder().setIndex(index).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("NSI | Response sent!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public synchronized void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		String name = request.getName();
    	String address = request.getAddress();
		debug("NSI | Received DELETE request.");
		
        try {
			// Send a single response through the stream.
			server.delete(name, address);
			debug("NSI | Server deleted. Preparing response...");
            DeleteResponse response = DeleteResponse.newBuilder().build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("NSI | Response sent!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}


	@Override
	public synchronized void lookup(LookupRequest request, StreamObserver<LookupResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		String name = request.getName();
    	String qualifier = request.getQualifier();
		LookupResponse.Builder serverList = LookupResponse.newBuilder();
		debug("NSI | Received LOOKUP request.");
		for(ServerEntry server: server.lookup(name, qualifier)) {
			NamingServerDistLedger.ServerEntry.Builder entry = NamingServerDistLedger.ServerEntry.newBuilder();
			entry.setQualifier(server.getQualifier()).setAddress(server.getAddress());
			serverList.addServer(entry.build());
		}

		debug("NSI | Lookup completed. Preparing response...");
		// Send a single response through the stream.
		LookupResponse response = serverList.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
		debug("NSI | Response sent!");
	}
}