package pt.tecnico.distledger.server.domain.operation;

import pt.tecnico.distledger.vector.VectorClock;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions;

public class Operation {
    private String account;
    private boolean stable;
    private VectorClock prev;
    private VectorClock TS;

    public Operation(String fromAccount, VectorClock prev) {
        this.account = fromAccount;
        this.stable = false;
        this.prev = new VectorClock(prev.toList());
    }

    public Operation(DistLedgerCommonDefinitions.Operation op) {
        this.account = op.getUserId();
        this.stable = op.getStable();
        this.prev = new VectorClock(op.getPrevTS().getTsList());
        this.TS = new VectorClock(op.getTS().getTsList());
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public boolean getStable() {
        return stable;
    }

    public VectorClock getPrev() {
        return prev;
    }

    public VectorClock getTS() {
        return TS;
    }

    public void stable() {
        this.stable = true;
    }

    public void notStable() {
        this.stable = false;
    }

    public void setTS(VectorClock ts) {
        this.TS = new VectorClock(ts.toList());
    }

    public void setPrev(VectorClock prev) {
        this.prev = new VectorClock(prev.toList());
    }

    public String toString() {
        return "Account: " + getAccount() + "\nStable: " + getStable() + "\nPrev: " + getPrev().toString() + "\nTS: " + getTS().toString();
    }

}
