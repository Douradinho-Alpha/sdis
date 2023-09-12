package pt.tecnico.distledger.server.domain.operation;

import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;

public class TransferOp extends Operation {
    private String destAccount;
    private int amount;

    public TransferOp(String fromAccount, String destAccount, int amount, VectorClock prev) {
        super(fromAccount, prev);
        this.destAccount = destAccount;
        this.amount = amount;
    }

    public TransferOp(DistLedgerCommonDefinitions.Operation op) {
        super(op);
        this.destAccount = op.getDestUserId();
        this.amount = op.getAmount();
    }

    public String getDestAccount() {
        return destAccount;
    }

    public void setDestAccount(String destAccount) {
        this.destAccount = destAccount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "From: " + getAccount() + " To: " + getDestAccount() + "\nStable: " + getStable() + "\nPrev: " + getPrev().toString() + "\nTS: " + getTS().toString();
    }

}
