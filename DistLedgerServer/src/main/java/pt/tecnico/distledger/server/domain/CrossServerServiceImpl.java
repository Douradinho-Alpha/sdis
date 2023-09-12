package pt.tecnico.distledger.server.domain;

import io.grpc.stub.StreamObserver;
import static io.grpc.Status.INVALID_ARGUMENT;

import java.util.ArrayList;
import java.util.List;
import pt.tecnico.distledger.server.domain.operation.CreateOp;
import pt.tecnico.distledger.server.domain.operation.Operation;
import pt.tecnico.distledger.server.domain.operation.TransferOp;
import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.LedgerState;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.OperationType;
import pt.ulisboa.tecnico.distledger.contract.distledgerserver.CrossServerDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.distledgerserver.DistLedgerCrossServerServiceGrpc.DistLedgerCrossServerServiceImplBase;

public class CrossServerServiceImpl extends DistLedgerCrossServerServiceImplBase {
	private ServerState server;

	private static final boolean DEBUG_FLAG = true;

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}
	public CrossServerServiceImpl(ServerState s) {
		this.server = s;
	}

	private List<Operation> createLedger(LedgerState ledgerGrpc) {
		List<Operation> ledger = new ArrayList<>();
		for(DistLedgerCommonDefinitions.Operation opGrpc: ledgerGrpc.getLedgerList()) {
			Operation op = null;
			if(opGrpc.getType() == OperationType.OP_CREATE_ACCOUNT) {
				op = new CreateOp(opGrpc);
			}
			else if(opGrpc.getType() == OperationType.OP_TRANSFER_TO) {
				op = new TransferOp(opGrpc);
			}
			ledger.add(op);
		}

		return ledger;
	}

    @Override
	public synchronized void propagateState(PropagateStateRequest request, StreamObserver<PropagateStateResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// server in order to send the appropriate responses (or errors, if any occur).
		try {
			debug("CSI | Propagate request received.");
			DistLedgerCommonDefinitions.LedgerState ledgerGrpc = request.getState();
			DistLedgerCommonDefinitions.VectorClock vectorGrpc = request.getReplicaTS();
			List<Operation> ledger = createLedger(ledgerGrpc);
			VectorClock vector = new VectorClock(vectorGrpc.getTsList());
			debug("CSI | Propagate ACTIVATED.");

			server.updateLedger(ledger, vector);

			debug("CSI | Propagate DEACTIVATE. Preparing a response to request...");
            PropagateStateResponse response = PropagateStateResponse.newBuilder().build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("CSI | Response sent.");

		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
