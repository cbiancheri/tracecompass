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
        cpus.add(new Processor(cpu));
    }

    public Boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(Boolean b) {
        highlighted = b;
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

    @Override
    public String toString() {
        return machineName;
    }
}
