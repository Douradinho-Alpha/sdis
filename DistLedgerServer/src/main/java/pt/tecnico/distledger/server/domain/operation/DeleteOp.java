package pt.tecnico.distledger.server.domain.operation;

import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;

public class DeleteOp extends Operation {

    public DeleteOp(String account, VectorClock prev) {
        super(account, prev);
    }

    public DeleteOp(DistLedgerCommonDefinitions.Operation op) {
        super(op);
    }
}
