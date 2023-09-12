package pt.tecnico.distledger.server.domain;

import io.grpc.stub.StreamObserver;
import pt.tecnico.distledger.server.domain.operation.*;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.LedgerState;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.OperationType;

import static io.grpc.Status.INVALID_ARGUMENT;

import pt.ulisboa.tecnico.distledger.contract.admin.AdminDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.admin.AdminServiceGrpc.*;


public class AdminServiceImpl extends AdminServiceImplBase {
    private ServerState server;
	/** Set flag to true to print debug messages.
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}
	private static final String ACTIVE = "active";
    private static final String INACTIVE = "inactive";

	public AdminServiceImpl(ServerState s) {
		this.server = s;
	}

    @Override
	public synchronized void activate(ActivateRequest request, StreamObserver<ActivateResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		// Error handling.
		try {
			// Send a single response through the stream.
			server.setState(ACTIVE);
			debug("ASI | ACTIVATE request received. Preparing response!");
            ActivateResponse response = ActivateResponse.newBuilder().build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("ASI | Response sent!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public synchronized void deactivate(DeactivateRequest request, StreamObserver<DeactivateResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		// Error handling.
		try {
			debug("ASI | DEACTIVATE request received.");
			// Send a single response through the stream.
			server.setState(INACTIVE);
			debug("ASI | Preparing response!");
            DeactivateResponse response = DeactivateResponse.newBuilder().build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("ASI | Response sent!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
	
    @Override
	public synchronized void getLedgerState(getLedgerStateRequest request, StreamObserver<getLedgerStateResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
        LedgerState.Builder ls = LedgerState.newBuilder();
		debug("ASI | Ledger State request received.");
		for(Operation ledger: server.getLedgerState()) {
			DistLedgerCommonDefinitions.Operation.Builder op = DistLedgerCommonDefinitions.Operation.newBuilder();
			if(ledger instanceof CreateOp) {
				op.setType(OperationType.OP_CREATE_ACCOUNT).setUserId(ledger.getAccount());
				debug("State " + ledger.getStable());
			}
			else if(ledger instanceof TransferOp) {
				op.setType(OperationType.OP_TRANSFER_TO).setUserId(ledger.getAccount()).setDestUserId(((TransferOp) ledger).getDestAccount()).setAmount(((TransferOp) ledger).getAmount());
				debug("State " + ledger.getStable());
			}
			ls.addLedger(op);
		}

		debug("ASI | Ledger done. Preparing response!");
        // Send a single response through the stream.
        getLedgerStateResponse response = getLedgerStateResponse.newBuilder().setLedgerState(ls).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
		debug("ASI | Response sent!");
	}
	
    @Override
	public synchronized void gossip(GossipRequest request, StreamObserver<GossipResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		// Error handling.
		try {
			// Send a single response through the stream.
			server.gossip();
            GossipResponse response = GossipResponse.newBuilder().build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}