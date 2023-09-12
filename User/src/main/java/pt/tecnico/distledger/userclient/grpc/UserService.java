package pt.tecnico.distledger.userclient.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.distledger.contract.user.UserDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.user.UserServiceGrpc.UserServiceBlockingStub;
import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger.LookupRequest;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger.ServerEntry;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc.NamingServerServiceBlockingStub;
import pt.ulisboa.tecnico.distledger.contract.user.UserServiceGrpc;

public class UserService { 
    private NamingServerServiceBlockingStub stubNS;
    private ConcurrentHashMap<String, UserServiceBlockingStub> stubs;
    private static final String SERVICE = "Distledger";
    private static final int ERROR = -1;
    private VectorClock prev;

    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;

    /*
    TODO: The gRPC client-side logic should be here.
    This should include a method that builds a channel and stub,
    as well as individual methods for each remote operation of this service. */
    public UserService(String host, int port) {
        CreateNamingServerStub(host, port);
        this.stubs = new ConcurrentHashMap<>();
        this.prev = new VectorClock();
        lookup(SERVICE);
    }

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}

    private void CreateNamingServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channelNS = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        debug("US | Connected to the naming server.");
        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        this.stubNS = NamingServerServiceGrpc.newBlockingStub(channelNS);
    }

    private NamingServerServiceBlockingStub getStubNS() {
        return stubNS;
    }

    private UserServiceBlockingStub findAddressAndCreateServerStub(ServerEntry server) {
        String address = server.getAddress();
        String[] split = address.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);
        return createServerStub(host, port);
    }

    public synchronized void lookup(String name, String qualifier) {
        List<ServerEntry> servers = new ArrayList<>();
        servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).setQualifier(qualifier).build()).getServerList();
        debug("US | List of servers for the qualifier " + qualifier + ":\n" + servers.toString());
        if(servers.isEmpty()) {
            throw new RuntimeException("That service or server doesn't exist!");
        }
        UserServiceBlockingStub stub = findAddressAndCreateServerStub(servers.get(0)); 
        this.stubs.put(qualifier, stub);
    }

    public synchronized void lookup(String name) {
        List<ServerEntry> servers = new ArrayList<>();
        servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).build()).getServerList();
        if(servers.isEmpty()) {
            throw new RuntimeException("That service or server doesn't exist!");
        }
        for(ServerEntry s: servers) {
            this.stubs.put(s.getQualifier(), findAddressAndCreateServerStub(s));
        }
    }

    private UserServiceBlockingStub createServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    
        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        return UserServiceGrpc.newBlockingStub(channel);
    }

    private UserServiceBlockingStub getStubByQualifier(String qualifier) {
        UserServiceBlockingStub stub = this.stubs.get(qualifier);
        if(stub == null) {
            throw new RuntimeException("Server " + qualifier +" doesn't exist!");
        }
        return stub;
    }


    private void catchServerExceptions(Exception e, String qualifier){
        if(e instanceof StatusRuntimeException) {
            throw new  RuntimeException(((StatusRuntimeException) e).getStatus().getDescription());
        } else {
            System.out.println("Caught exception with description: " + ((RuntimeException) e).getMessage());

            debug("US | Looking up for potencial new servers!");
            lookup(SERVICE, qualifier);
            throw new RuntimeException(((RuntimeException) e).getMessage());
        }
    }

    public synchronized boolean createAccount(String qualifier, String userId) {
        DistLedgerCommonDefinitions.VectorClock.Builder ts = DistLedgerCommonDefinitions.VectorClock.newBuilder();
        ts.addAllTs(this.prev.toList());
        try {
            debug("US | Sent a Create Account request for " + userId + " to server " + qualifier);
            CreateAccountResponse response = getStubByQualifier(qualifier).createAccount(CreateAccountRequest.newBuilder().setUserId(userId).setPrev(ts).build());
            if(!(response.getOperationTs().getTS().getTsList().isEmpty())) {
                this.prev.merge(new VectorClock(response.getOperationTs().getTS().getTsList()));
                debug("US | Received a Create Account response for " + userId + " to server " + qualifier);
            }
        } catch (Exception e) {
            catchServerExceptions(e, qualifier);
            return false;
        } 
        return true;
    }

    public synchronized boolean transferTo(String qualifier, String userFrom, String userTo, int amount) {
        DistLedgerCommonDefinitions.VectorClock.Builder ts = DistLedgerCommonDefinitions.VectorClock.newBuilder();
        ts.addAllTs(this.prev.toList());
        try {
            TransferToResponse response = getStubByQualifier(qualifier).transferTo(TransferToRequest.newBuilder().setAccountFrom(userFrom).setAccountTo(userTo).setAmount(amount).setPrev(ts).build());
            if(!(response.getOperationTs().getTS().getTsList().isEmpty())) {
                this.prev.merge(new VectorClock(response.getOperationTs().getTS().getTsList()));
                debug("US | Sent a TransferTo request from " + userFrom + " to " + userTo + " of " + amount + " Escudos to server " + qualifier);
            }
        } catch (Exception e) {
            catchServerExceptions(e, qualifier);
            return false;
        } 
        return true;
    }

    public synchronized int balance(String qualifier, String userId) {
        int res;
        DistLedgerCommonDefinitions.VectorClock.Builder ts = DistLedgerCommonDefinitions.VectorClock.newBuilder();
        ts.addAllTs(this.prev.toList());
        try {
            debug("US | Sent a Balance request of " + userId + " to server " + qualifier);
            BalanceResponse response = getStubByQualifier(qualifier).balance(BalanceRequest.newBuilder().setUserId(userId).setPrev(ts).build());
            this.prev.merge(new VectorClock(response.getNew().getTsList())); 
            res = response.getValue();
            debug("US | Received Balance of " + userId + " with prev " + this.prev);
        } catch (Exception e) {
            catchServerExceptions(e, qualifier);
            return ERROR;
        } 
        return res;
    }
}
