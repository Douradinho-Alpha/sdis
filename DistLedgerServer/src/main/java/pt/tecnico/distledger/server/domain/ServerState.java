package pt.tecnico.distledger.server.domain;

import pt.tecnico.distledger.server.domain.operation.CreateOp;
import pt.tecnico.distledger.server.domain.operation.Operation;
import pt.tecnico.distledger.server.domain.operation.TransferOp;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.LedgerState;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.OperationType;
import pt.ulisboa.tecnico.distledger.contract.distledgerserver.DistLedgerCrossServerServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.distledgerserver.CrossServerDistLedger.PropagateStateRequest;
import pt.ulisboa.tecnico.distledger.contract.distledgerserver.DistLedgerCrossServerServiceGrpc.DistLedgerCrossServerServiceBlockingStub;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.namingserver.NamingServerServiceGrpc.NamingServerServiceBlockingStub;
import pt.tecnico.distledger.vector.VectorClock;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;



public class ServerState {
    private List<Operation> ledger;
    private Map<String, Integer> accounts;
    private String state;
    private String qual;
    private NamingServerServiceGrpc.NamingServerServiceBlockingStub stubNS;
    private ConcurrentHashMap<String, DistLedgerCrossServerServiceBlockingStub> stubs;
    private VectorClock ValueTS;
    private VectorClock ReplicaTS;
    private int index;
    private static final int ZERO = 0;
    private static final int BROKER_BALANCE = 1000;
    private static final String ACTIVE = "active";
    private static final String INACTIVE = "inactive";
    private static final String BROKER_NAME = "broker";
    private static final String SERVICE = "Distledger";

    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}
    
    /* TODO: Here should be declared all the server state attributes
         as well as the methods to access and interact with the state. */
    public ServerState(String qualifier, String host, int port) {
        this.ledger = new ArrayList<>();
        this.accounts = new HashMap<String, Integer>();
        this.stubs = new ConcurrentHashMap<>();
        this.ValueTS = new VectorClock();
        this.ReplicaTS = new VectorClock();
        this.createBroker();
        this.setState(ACTIVE);
        this.qual = qualifier;
        createNamingServerStub(host, port);
        lookup(SERVICE);
    }

    private void createNamingServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channelNS = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        this.stubNS = NamingServerServiceGrpc.newBlockingStub(channelNS);
    }

    public VectorClock getValueTS() {
        return ValueTS;
    }

    public VectorClock getReplicaTS() {
        return ReplicaTS;
    }

    public List<Operation> getLedgerState() {
        List<Operation> ledgerCopy = new ArrayList<>(this.ledger);
        return ledgerCopy;
    }

    public String getQualifier() {
        return qual;
    }

    public synchronized void addOperation(Operation op) {
        if(!ledger.stream().anyMatch(operation -> operation.getPrev().isEqual(op.getPrev()))) {
            this.ledger.add(op);
        }
    }

    public Map<Integer, VectorClock> balance(String userId, VectorClock prev) {
        Map<Integer, VectorClock> result = new HashMap<>();
        if(this.getState().equals(INACTIVE)) {
            return result;
        }
        else if(this.accounts.get(userId) == null){
            return result;
        } 
        else if(!getValueTS().GE(prev)) { // if(ValueTS < prev)    
            throw new RuntimeException("Server does not have an updated value!");
        }
        
        result.put(accounts.get(userId), getValueTS());
        return result;
    }

    public synchronized Operation createAccount(String userId, VectorClock prev) {
        CreateOp op = null;
        VectorClock ts = new VectorClock(prev.toList());
        if(this.getState().equals(INACTIVE)) {
            return op;
        }
        else if(this.accounts.get(userId) != null){
            return op;
        }

        this.ReplicaTS.setTS(this.index, this.ReplicaTS.getTS(this.index) + 1);
        ts.setTS(this.index, this.ReplicaTS.getTS(this.index));
        op = new CreateOp(userId, prev);
        op.setTS(ts);
        if(getValueTS().GE(prev)) {
            op.stable();
            getValueTS().merge(op.getTS());
            this.accounts.put(userId, ZERO);
            debug("Created " + userId + " account!");
        } 
        this.addOperation(op);
        return op;
    }

    public synchronized boolean createAccount(CreateOp op) {
        VectorClock ts = new VectorClock(op.getTS().toList());
        if(this.getState().equals(INACTIVE)) {
            return false;
        }
        else if(this.accounts.get(op.getAccount()) != null){
            return false;
        }

        this.ReplicaTS.setTS(this.index, this.ReplicaTS.getTS(this.index) + 1);
        ts.setTS(this.index, this.ReplicaTS.getTS(this.index));
        op.setTS(ts);
        if(getValueTS().GE(op.getPrev())) {
            this.accounts.put(op.getAccount(), ZERO);
            debug("Created " + op.getAccount() + " account!");
        } 
        return true;
    }


    public synchronized Operation transferTo(String userFrom, String userTo, int amount, VectorClock prev) {
        TransferOp op = null;
        VectorClock ts = new VectorClock(prev.toList());
        if(this.getState().equals(INACTIVE)) {
            return op;
        }
        else if(this.accounts.get(userFrom) == null) {
            return op;
        }
        else if(this.accounts.get(userFrom) - amount < 0) {
            return op;
        }
        else if(this.accounts.get(userTo) == null) {
            return op;
        }
        else if(userFrom.equals(userTo)) {
            return op;
        }
        else if(amount < 1) {
            return op;
        }
        this.ReplicaTS.setTS(this.index, this.ReplicaTS.getTS(this.index) + 1);
        ts.setTS(this.index, this.ReplicaTS.getTS(this.index));
        op = new TransferOp(userFrom, userTo, amount, prev);
        op.setTS(ts);
        if(getValueTS().GE(prev)) {
            op.stable();
            getValueTS().merge(op.getTS());
            this.accounts.replace(userFrom, this.accounts.get(userFrom) - amount);
            this.accounts.replace(userTo, this.accounts.get(userTo) + amount);
            debug("SS | " + userFrom + " TRANSFERRED " + amount + " to "+ userTo);
        }
        this.addOperation(op);
        return op;
    }

    public synchronized boolean transferTo(TransferOp op) {
        VectorClock ts = new VectorClock(op.getTS().toList());
        if(this.getState().equals(INACTIVE)) {
            return false;
        }
        else if(this.accounts.get(op.getAccount()) == null) {
            return false;
        }
        else if(this.accounts.get(op.getAccount()) - op.getAmount() < 0) {
            return false;
        }
        else if(this.accounts.get(op.getDestAccount()) == null) {
            return false;
        }
        else if(op.getAccount().equals(op.getDestAccount())) {
            return false;
        }
        else if(op.getAmount() < 1) {
            return false;
        } 
        else if(ledger.stream().anyMatch(operation -> operation.getPrev().isEqual(op.getPrev()))) {
            return false;
        }
        this.ReplicaTS.setTS(this.index, this.ReplicaTS.getTS(this.index) + 1);
        ts.setTS(this.index, this.ReplicaTS.getTS(this.index));
        op.setTS(ts);
        if(getValueTS().GE(op.getPrev())) {
            this.accounts.replace(op.getAccount(), this.accounts.get(op.getAccount()) - op.getAmount());
            this.accounts.replace(op.getDestAccount(), this.accounts.get(op.getDestAccount()) + op.getAmount());
            debug("SS | " + op.getAccount() + " TRANSFERRED " + op.getAmount() + " to "+ op.getDestAccount());
        } 
        this.addOperation(op);
        return true;
    }

    public synchronized void setState(String s) {
        if(this.getState() != null && this.getState().equals(s) && this.getState().equals(ACTIVE)) {
            throw new RuntimeException("Server is already active!");
        }
        else if(this.getState() != null && this.getState().equals(s) && this.getState().equals(INACTIVE)) {
            throw new RuntimeException("Server is already inactive!");
        }
        else {
            debug("SS | Server state set as: " + s);
            this.state = s;
        }
    }

    public String getState() {
        return this.state;
    }

    public synchronized void createBroker() {
        this.accounts.put(BROKER_NAME, BROKER_BALANCE);
    }

    private NamingServerServiceBlockingStub getStubNS() {
        return stubNS;
    }

    public synchronized void register(String qualifier, String host, String port) {
        String address = host + ":" + port;
        try {
            this.index = this.getStubNS().register(RegisterRequest.newBuilder().setName(SERVICE).setQualifier(qualifier).setAddress(address).build()).getIndex();
            debug("SS | Register request sent");
        } catch (StatusRuntimeException e) {
            System.out.println("Caught exception with description: " + e.getStatus().getDescription());
        }
    }

    public synchronized void delete(String qualifier, String host, String port) {
        String address = host + ":" + port;
        try {
            debug("SS | Delete request sent");
            this.getStubNS().delete(DeleteRequest.newBuilder().setName(SERVICE).setAddress(address).build());
            ((ManagedChannel) this.getStubNS().getChannel()).shutdownNow();
        } catch (StatusRuntimeException e) {
            System.out.println("Caught exception with description: " + e.getStatus().getDescription());
        }
    }

    private DistLedgerCrossServerServiceBlockingStub createServerStub(String host, int port) {
        // Channel is the abstraction to connect to a service endpoint.
        // Let us use plaintext communication because we do not have certificates.
        final String target = host + ":" + port;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        debug("SS | Server created with Host " + host + " Port " + port);
        // It is up to the client to determine whether to block the call.
        // Here we create a blocking stub, but an async stub,
        // or an async stub with Future are always possible.
        return DistLedgerCrossServerServiceGrpc.newBlockingStub(channel);
    }

    private DistLedgerCrossServerServiceBlockingStub findAddressAndCreateServerStub(ServerEntry server) {
        String address = server.getAddress();
        String[] split = address.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);
        return createServerStub(host, port);
    }

    public synchronized DistLedgerCrossServerServiceBlockingStub lookup(String name, String qualifier) {
        List<ServerEntry> servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).setQualifier(qualifier).build()).getServerList();
        if(servers.isEmpty()) {
            throw new RuntimeException("That service or server doesn't exist!");
        }
        debug("List of servers for the qualifier " + qualifier + ":\n" + servers.toString() + "\n");
        DistLedgerCrossServerServiceBlockingStub stub = findAddressAndCreateServerStub(servers.get(0)); 
        this.stubs.put(qualifier, stub);
        return stub;
    }

    public synchronized void lookup(String name) {
        List<ServerEntry> servers = this.getStubNS().lookup(LookupRequest.newBuilder().setName(name).build()).getServerList();
        debug("List of servers:\n" + servers.toString());
        for(ServerEntry s: servers) {
            this.stubs.put(s.getQualifier(), findAddressAndCreateServerStub(s));
        }
    }

    /*
    private DistLedgerCrossServerServiceBlockingStub getStubByQualifier(String qualifier) {
        DistLedgerCrossServerServiceBlockingStub stub = this.stubs.get(qualifier);
        if(stub == null) {
            throw new RuntimeException("Server " + qualifier +" doesn't exist!");
        }
        return stub;
    }

    private boolean catchServerExceptions(Exception e, String qualifier){
        if(e instanceof StatusRuntimeException) {
            throw new  RuntimeException(((StatusRuntimeException) e).getStatus().getDescription());
        } else {
            debug("Looking up for potencial new servers!");
            lookup(SERVICE, qualifier);
            if(getStubByQualifier(qualifier) == null) {
                throw new RuntimeException(((RuntimeException) e).getMessage());
            } 
            return true;   
        }
    }
     */

    public synchronized void gossip() {
        lookup(SERVICE);
        DistLedgerCrossServerServiceBlockingStub stub;
        debug("Inicio do gossip no serverState");
        for(String qualifier: this.stubs.keySet()) {
            if(!qualifier.equals(this.qual)) {
                stub = this.stubs.get(qualifier);
                propagateState(stub);
            }
        }
        debug("Fim do gossip no serverState");
    }

    private boolean propagateState(DistLedgerCrossServerServiceBlockingStub stub) {
        LedgerState.Builder ls = LedgerState.newBuilder();
        DistLedgerCommonDefinitions.Operation.Builder op = DistLedgerCommonDefinitions.Operation.newBuilder();

        for(Operation operation: getLedgerState()) {     
            DistLedgerCommonDefinitions.VectorClock.Builder prevGrpcBuilder = DistLedgerCommonDefinitions.VectorClock.newBuilder();   
		    DistLedgerCommonDefinitions.VectorClock prevGrpc = prevGrpcBuilder.addAllTs(operation.getPrev().toList()).build();
            DistLedgerCommonDefinitions.VectorClock.Builder tsGrpcBuilder = DistLedgerCommonDefinitions.VectorClock.newBuilder();
            DistLedgerCommonDefinitions.VectorClock tsGrpc = tsGrpcBuilder.addAllTs(operation.getTS().toList()).build();
            if(operation instanceof CreateOp) {
                op.setType(OperationType.OP_CREATE_ACCOUNT).setUserId(operation.getAccount()).setPrevTS(prevGrpc).setTS(tsGrpc);
            }
            else if(operation instanceof TransferOp) {
                op.setType(OperationType.OP_TRANSFER_TO).setUserId(operation.getAccount()).setDestUserId(((TransferOp) operation).getDestAccount()).setAmount(((TransferOp) operation).getAmount()).setPrevTS(prevGrpc).setTS(tsGrpc);
            }
            ls.addLedger(op);
        }
        LedgerState ledger = ls.build();
        DistLedgerCommonDefinitions.VectorClock.Builder vectorGrpcBuilder = DistLedgerCommonDefinitions.VectorClock.newBuilder();
		DistLedgerCommonDefinitions.VectorClock vectorGrpc = vectorGrpcBuilder.addAllTs(getReplicaTS().toList()).build();
    
        stub.propagateState(PropagateStateRequest.newBuilder().setState(ledger).setReplicaTS(vectorGrpc).build());
        return true;
    }

    private boolean executeOperation(Operation operation) {
        if(operation instanceof CreateOp) {
            return createAccount((CreateOp) operation);
        } 
        else if(operation instanceof TransferOp) {
            return transferTo((TransferOp) operation);
        }
        return false;
    }

    public synchronized void updateLedger(List<Operation> ledgerOther, VectorClock repOther) {
        for(Operation opOther: ledgerOther) {
            Operation operation = opOther;
            debug("OpTS: " + operation.getTS().toList().toString() + " <= " + getReplicaTS().toList().toString() + " :ReplicaTS" + " --> (" + getReplicaTS().GE(operation.getTS()) + ")" );
            if(getReplicaTS().GE(operation.getTS())) {
                continue;
            }
            //op.ts <= b.repTS continue
            debug("FOR NUMBER 1");
            debug("OpPrev: " + operation.getPrev().toList().toString() + " <= " + getValueTS().toList().toString() + " :ValueTS" + " --> (" + getValueTS().GE(operation.getPrev()) + ")" );
            if(getValueTS().GE(operation.getPrev())) {
                operation.stable();
                executeOperation(operation);
                getValueTS().merge(operation.getTS());    
            } else {
                operation.notStable();
            }
            this.addOperation(operation);
        }
        getReplicaTS().merge(repOther);
        debug("Merge( " + getReplicaTS().toList().toString() + ", " + repOther.toList().toString()+ " ) = " + getReplicaTS().toList().toString());
        for(Operation operation: getLedgerState()) {
            debug("FOR NUMBER 2");
            debug("OpPrev: " + operation.getPrev().toList().toString() + " <= " + getValueTS().toList().toString() + " :ValueTS" + " --> (" + getValueTS().GE(operation.getPrev()) + ")" );
            if(getValueTS().GE(operation.getPrev()) && operation.getStable() == false) {
                operation.stable();
                executeOperation(operation);
                getValueTS().merge(operation.getTS());    
            }
        }
    } 
}
