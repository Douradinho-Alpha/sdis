package pt.tecnico.distledger.server.domain.operation;

import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;

public class CreateOp extends Operation {

    public CreateOp(String account, VectorClock prev) {
        super(account, prev);
    }

    public CreateOp(DistLedgerCommonDefinitions.Operation op) {
        super(op);
    }

}
