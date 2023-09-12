package pt.tecnico.distledger.adminclient.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.distledger.contract.admin.AdminServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.admin.AdminDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.admin.AdminServiceGrpc.AdminServiceBlockingStub;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc.NamingServerServiceBlockingStub;

import java.util.ArrayList;import java.util.List;import java.util.concurrent.ConcurrentHashMap;

public class AdminService{
    private static final String SERVICE = "Distledger";
    private ConcurrentHashMap<String, AdminServiceBlockingStub> stubs;
    private NamingServerServiceGrpc.NamingServerServiceBlockingStub stubNS;

    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}

    /* TODO: The gRPC client-side logic should be here.
        This should include a method that builds a channel and stub,
        as well as individual methods for each remote operation of this service. */
    public AdminService(String host, int port) {
        CreateNamingServerStub(host, port);
        this.stubs = new ConcurrentHashMap<>();
        lookup(SERVICE);
    }

    private void CreateNamingServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channelNS = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        this.stubNS = NamingServerServiceGrpc.newBlockingStub(channelNS);
        debug("ASI | Naming Server \tHost: "+host+"\tPort: " + port + "\tCREATED");
    }

    private AdminServiceBlockingStub createServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

        debug("ASI | Server CREATED with\tHost: "+host+"\tPort: " + port);
        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        return AdminServiceGrpc.newBlockingStub(channel);
    }

    private NamingServerServiceBlockingStub getStubNS() {
        return stubNS;
    }

    private AdminServiceBlockingStub findAddressAndCreateServerStub(NamingServerDistLedger.ServerEntry server) {
        String address = server.getAddress();
        String[] split = address.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);
        debug("ASI | Want to create a Server stub? Use this:\nHost: "+host+"\tPort: "+port);
        return createServerStub(host, port);
    }

    public synchronized void lookup(String name, String qualifier) {
        List<ServerEntry> servers = new ArrayList<>();
        servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).setQualifier(qualifier).build()).getServerList();
        debug("ASI | List of servers for the qualifier " + qualifier + ":\n" + servers.toString());
        if(servers.isEmpty()) {
            throw new RuntimeException("That service or server doesn't exist!");
        }
        AdminServiceBlockingStub stub = findAddressAndCreateServerStub(servers.get(0)); 
        this.stubs.put(qualifier, stub);
    }

    public synchronized void lookup(String name) {
        List<NamingServerDistLedger.ServerEntry> servers = new ArrayList<>();
        servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).build()).getServerList();
        debug("ASI | List of servers:\n" + servers.toString());
        if(servers.isEmpty()) {
            throw new RuntimeException("That service or server doesn't exist!");
        }
        for(NamingServerDistLedger.ServerEntry s: servers) {
            this.stubs.put(s.getQualifier(), findAddressAndCreateServerStub(s));
        }
    }

    private AdminServiceBlockingStub getStubByQualifier(String qualifier) {
        AdminServiceBlockingStub stub = this.stubs.get(qualifier);
        if(stub == null) {
            throw new RuntimeException("Server " + qualifier +" doesn't exist!");
        }
        return stub;
    }

    private void catchServerExceptions(Exception e, String qualifier){
        if(e instanceof StatusRuntimeException) {
            System.out.println("Caught exception with description: " + ((StatusRuntimeException) e).getStatus().getDescription());
        } else {
            System.out.println("Caught exception with description: " + ((RuntimeException) e).getMessage());
            debug("ASI | Looking up for potencial new servers!");
            lookup(SERVICE, qualifier);
        }
    }

    public boolean activate(String qualifier) {
        try {
            getStubByQualifier(qualifier).activate(ActivateRequest.newBuilder().build());
            debug("ASI | Server Activated");
        } catch (Exception e) {
            catchServerExceptions(e, qualifier);
            return false;
        }
        return true;
    }

    public boolean deactivate(String qualifier) {
        try {
            this.getStubByQualifier(qualifier).deactivate(DeactivateRequest.newBuilder().build());
            debug("ASI | Server Deactivated");
        } catch (Exception e) {
            catchServerExceptions(e, qualifier);
            return false;
        }
        return true;
    }

    public String getLedgerState(String qualifier) {
        return this.getStubByQualifier(qualifier).getLedgerState(getLedgerStateRequest.newBuilder().build()).toString();
    }

    public boolean gossip(String qualifier) {
        try {
            this.getStubByQualifier(qualifier).gossip(GossipRequest.newBuilder().build());
        } catch (StatusRuntimeException e) {
            catchServerExceptions(e, qualifier);
            return false;
        }
        return true;
    }
}

