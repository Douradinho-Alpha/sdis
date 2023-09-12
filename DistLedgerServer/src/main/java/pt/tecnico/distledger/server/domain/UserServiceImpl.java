package pt.tecnico.distledger.server.domain;

import io.grpc.stub.StreamObserver;
import static io.grpc.Status.INVALID_ARGUMENT;

import java.util.HashMap;
import java.util.Map;

import pt.tecnico.distledger.server.domain.operation.*;
import pt.tecnico.distledger.server.domain.operation.Operation;
import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.OperationType;
import pt.ulisboa.tecnico.distledger.contract.user.UserDistLedger.*;
import pt.ulisboa.tecnico.distledger.contract.user.UserServiceGrpc.*;

public class UserServiceImpl extends UserServiceImplBase {
	private ServerState server;

	/** Set flag to true to print debug messages.
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;

    /*
    TODO: The gRPC client-side logic should be here.
    This should include a method that builds a channel and stub,
    as well as individual methods for each remote operation of this service. */
	public UserServiceImpl(ServerState s) {
		this.server = s;
	}
	
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}

	private DistLedgerCommonDefinitions.Operation createOperationGRPC(Operation op) {
		DistLedgerCommonDefinitions.Operation.Builder opGRPC = DistLedgerCommonDefinitions.Operation.newBuilder();
		DistLedgerCommonDefinitions.VectorClock.Builder vectorGrpcBuilder = DistLedgerCommonDefinitions.VectorClock.newBuilder();
		DistLedgerCommonDefinitions.VectorClock tsGrpc = vectorGrpcBuilder.addAllTs(op.getTS().toList()).build();
		if(op instanceof CreateOp) {
			opGRPC.setType(OperationType.OP_CREATE_ACCOUNT).setTS(tsGrpc);
		}
		else if(op instanceof TransferOp) {
			opGRPC.setType(OperationType.OP_TRANSFER_TO).setTS(tsGrpc);
		}
		return opGRPC.build();
	}

    @Override
	public synchronized void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
        String account = request.getUserId();
		DistLedgerCommonDefinitions.VectorClock prevGrpc = request.getPrev();
		VectorClock prev = new VectorClock(prevGrpc.getTsList());
		
		debug("USI | CREATE account request received.");
        try {
			Operation op = server.createAccount(account, prev);
			debug("USI | Account " + account + " with ts " + op.getTS().toString() + " CREATED.");
			debug("USI | Creating request response...");
			CreateAccountResponse.Builder responseBuilder = CreateAccountResponse.newBuilder();
			if(op != null){
				responseBuilder.setOperationTs(createOperationGRPC(op));
			}
            CreateAccountResponse response = responseBuilder.build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("USI | Response sent!");

		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

    @Override
	public synchronized void transferTo(TransferToRequest request, StreamObserver<TransferToResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
        String accountFrom = request.getAccountFrom();
        String accountTo = request.getAccountTo();
        int amount = request.getAmount();
		DistLedgerCommonDefinitions.VectorClock prevGrpc = request.getPrev();
		VectorClock prev = new VectorClock(prevGrpc.getTsList());
        try {
			Operation op = server.transferTo(accountFrom, accountTo, amount, prev);
			TransferToResponse.Builder responseBuilder = TransferToResponse.newBuilder();
			if(op != null){
				responseBuilder.setOperationTs(createOperationGRPC(op));
				debug("USI | Account " + accountFrom + " TRANSFERRED TO " + accountTo + amount + " Escudos.");
			}
			debug("USI | Creating request response...");
			TransferToResponse response = responseBuilder.build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("USI | Response sent!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} 		
	}

    @Override
	public synchronized void balance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
		// StreamObserver is used to represent the gRPC stream between the server and
		// client in order to send the appropriate responses (or errors, if any occur).
		String account = request.getUserId();
		DistLedgerCommonDefinitions.VectorClock prevGrpc = request.getPrev();
		VectorClock prev = new VectorClock(prevGrpc.getTsList());
		Map<Integer,VectorClock> result = new HashMap<>();
		try {
			result = server.balance(account, prev);
			int balance = result.entrySet().stream().findFirst().get().getKey();
			DistLedgerCommonDefinitions.VectorClock.Builder vectorGrpcBuilder = DistLedgerCommonDefinitions.VectorClock.newBuilder();
			DistLedgerCommonDefinitions.VectorClock vectorGrpc = vectorGrpcBuilder.addAllTs(result.get(balance).toList()).build();

			debug("USI | Account " + account + " with prev " + prev.toString() + " has " + balance + " Escudos in BALANCE. Creating server response...");
			BalanceResponse response = BalanceResponse.newBuilder().setValue(balance).setNew(vectorGrpc).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
			debug("USI | Response received!");
		} catch (RuntimeException e) {
			responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
