package org.eclipse.tracecompass.tmf.ui.widgets.timegraph;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Cedric Biancheri
 * @since 2.0
 *
 */
public class Machine {
    private String machineName;
    private Boolean highlighted;
    private Set<Processor> cpus = new HashSet<>();

    public Machine(String name) {
        machineName = name;
        highlighted = true;
    }

    public String getMachineName() {
        return machineName;
    }

    public void addCpu(String cpu) {
        cpus.add(new Processor(cpu, this));
    }

    public Boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlightedWithAllCpu(Boolean b) {
        highlighted = b;
        for (Processor p : cpus) {
            p.setHighlighted(b);
        }
    }

    public void setHighlighted(Boolean b) {
        highlighted = b;
    }

    public void setHighlightedCpu(int cpu, Boolean b) {
        for (Processor p : getCpus()) {
            if (Integer.parseInt(p.getNumber()) == cpu) {
                p.setHighlighted(b);
                if (b) {
                    setHighlighted(b);
                } else {
                    setHighlighted(isOneCpuHighlighted());
                }
            }
        }
    }

    public Set<Processor> getCpus() {
        return cpus;
    }

    public Boolean isCpuHighlighted(String p) {
        for(Processor proc : cpus) {
            if (p.equals(proc.toString())) {
                return proc.isHighlighted();
            }
        }
        return false;
    }

    public Boolean isCpuHighlighted(int p) {
        for(Processor proc : cpus) {
            if (p == Integer.parseInt(proc.toString())) {
                return proc.isHighlighted();
            }
        }
        return false;
    }

    public Boolean areAllCpusHighlighted() {
        Boolean res = true;
        for (Processor p : getCpus()) {
            res &= p.isHighlighted();
        }
        return res;
    }

    public Boolean areAllCpusNotHighlighted(){
        Boolean res = true;
        for (Processor p : getCpus()) {
            res &= !p.isHighlighted();
        }
        return res;
    }

    public Boolean isOneCpuHighlighted(){
        Boolean res = false;
        for (Processor p : getCpus()) {
            if (p.isHighlighted()) {
                return true;
            }
        }
        return res;
    }

    public Boolean isGrayed(){
        return !areAllCpusHighlighted() && isOneCpuHighlighted();
    }

    @Override
    public String toString() {
        return machineName;
    }
}
