package pt.tecnico.distledger.namingserver;

public class ServerEntry {
    private String address;
    private String qualifier;
    private int index;

    public ServerEntry(String qualifier, String address, int index) {
        setAddress(address);
        setQualifier(qualifier);
    }

    public String getQualifier() {
        return qualifier;
    }

    public String getAddress() {
        return address;        
    }

    public int getIndex() {
        return index;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }
    
}