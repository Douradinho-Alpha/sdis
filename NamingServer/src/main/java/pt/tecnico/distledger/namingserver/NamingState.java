package pt.tecnico.distledger.namingserver;
import java.util.ArrayList;
import java.util.List;

public class NamingState {
    private NamingServer ns;
    /** Set flag to true to print debug messages. 
	 * The flag can be set using the -Ddebug command line option. */
	private static final boolean DEBUG_FLAG = true;
    private List<Integer> indexes;

    public NamingState() {
        this.ns = new NamingServer();
        this.indexes = new ArrayList<>();
    }

	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.println(debugMessage);
	}
    

    public synchronized int register(String name, String qualifier, String address) {
        int index = -1;
        for(int i = 0; i < indexes.size(); i++) {
            if(indexes.get(i) == 0) {
                indexes.set(i, 1);
                index = i;
                break;
            }
        } 

        if(index == -1) { 
            index = indexes.size();
            indexes.add(1);
        }

        ServerEntry server = new ServerEntry(qualifier, address, index);
        if(ns.checkKey(name)){
            if(ns.getServiceEntry(name).getSetServerEntries().contains(server)) {
                throw new RuntimeException("Not possible to register the server!");
            } else {
                ns.getServiceEntry(name).addServerEntry(server);
                debug("NS | Registed server: " + qualifier + " with the following address: " + address);
            }
        } else {
            ServiceEntry service = new ServiceEntry(name, server);
            ns.addService(name, service);
            debug("NS | Added new service: " + name + "\nRegisted server: " + qualifier + " with the following address: " + address);
        }
        return index;
    } 
        
    public synchronized List<ServerEntry> lookup(String name, String qualifier) {
        if(!ns.checkKey(name)) {
            return new ArrayList<>();
        }
        else if(qualifier.isBlank()) {
            debug("NS | Returned list of servers that provide the service: " + name);
            return ns.getServiceEntry(name).getListServerEntries();
        }
        debug("NS | Returned list of addresses of the servers with the qualifier " + qualifier + " that provide the service: " + name);
        return ns.getServiceEntry(name).getServerEntriesByQualifier(qualifier);  
    }

    
    public synchronized void delete(String name, String address) {
        if(!ns.checkKey(name)) {
            throw new RuntimeException("Not possible to remove the server!");
        } else {
            ServerEntry server = ns.getServiceEntry(name).getServerEntryByAddress(address);
            if(server == null) {
                throw new RuntimeException("Not possible to remove the server!");
            } else {
                debug("NS | Deleted server: " + server.getQualifier() + " with the following address: " + address);
                indexes.set(server.getIndex(), 0);
                ns.getServiceEntry(name).removeServerEntry(server);
            }
        }
    }
}
