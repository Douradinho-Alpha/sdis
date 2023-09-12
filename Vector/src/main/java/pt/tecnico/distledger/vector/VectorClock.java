package pt.tecnico.distledger.vector;

import java.util.ArrayList;
import java.util.List;

public class VectorClock {
    private List<Integer> timeStamps;
    
    public VectorClock() {
        this.timeStamps = new ArrayList<>();
        this.timeStamps.add(0);
        this.timeStamps.add(0);
        this.timeStamps.add(0);
    }

    public VectorClock(List<Integer> list) {
        this.timeStamps = new ArrayList<>(list);
    }

    public Integer getTS(Integer i) {
        return this.timeStamps.get(i);
    }

    public void setTS(Integer i, Integer time) {
        this.timeStamps.set(i, time);
    }

    public List<Integer> toList() {
        return this.timeStamps;
    }

    public boolean GE(VectorClock v) {
        for (int i = 0; i < timeStamps.size(); i++) {
           if (getTS(i) < v.getTS(i)){
              return false;
            }
        }
        return true;
    }

    public void merge(VectorClock vector) {
        List<Integer> list = new ArrayList<>();
        for(int i = 0; i < this.timeStamps.size(); i++) {
            if(this.timeStamps.get(i) > vector.getTS(i)) {
                list.add(this.timeStamps.get(i));
            } else {
                list.add(i, vector.getTS(i));
            }
        }
        this.timeStamps = list;
    }

    public boolean isEqual(VectorClock vec) {
        return timeStamps.equals(vec.toList());
    }

    public String toString() {
        String res = "[";
        for (int i = 0; i < this.timeStamps.size(); i++) {
            res += this.getTS(i);
            if (i + 1 != this.timeStamps.size()) {
                res += ",";
            }
        }
        res += "]";
        return res;
    }
}
