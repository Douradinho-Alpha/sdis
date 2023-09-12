package pt.tecnico.distledger.namingserver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ServiceEntry {
    private String name;
    private Set<ServerEntry> serverEntries;

    public ServiceEntry(String name, ServerEntry server) {
        setName(name);
        this.serverEntries = new HashSet<ServerEntry>();
        addServerEntry(server);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName(String name) {
        return name;
    }

    public void addServerEntry(ServerEntry entry) {
        this.serverEntries.add(entry);
    }

    public Set<ServerEntry> getSetServerEntries() {
        return serverEntries;
    }

    public List<ServerEntry> getListServerEntries() {
        List<ServerEntry> list = new ArrayList<>();
        list.addAll(this.getSetServerEntries());
        return list;
    }

    public ServerEntry getServerEntryByAddress(String address) {
        for(ServerEntry server: this.getSetServerEntries()) {
            if(server.getAddress().equals(address))
                return server;
        }
        return null;
    }

    public List<ServerEntry> getServerEntriesByQualifier(String qualifier) {
        return getListServerEntries().stream().filter(s -> s.getQualifier().equals(qualifier)).toList();
    }

    public void removeServerEntry(ServerEntry server) {
        this.getSetServerEntries().remove(server);
    }
}