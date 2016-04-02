package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;


public class Machine {

    private String machineName;
    private Boolean highlighted;
    private int alpha;
    private Set<Processor> cpus = new HashSet<>();
    private ITmfStateValue typeMachine;

    public Machine(String name) {
        machineName = name;
        highlighted = true;
    }

    public Machine(String name, Integer nbCPUs, ITmfStateValue type) {
        machineName = name;
        highlighted = true;
        alpha = FusedVMViewPresentationProvider.fHighlightAlpha;
        typeMachine = type;
        for (Integer i = 0; i < nbCPUs; i++) {
            cpus.add(new Processor(i.toString(), this));
        }
    }

    public String getMachineName() {
        return machineName;
    }

    public int getAlpha() {
        return alpha;
    }

    public ITmfStateValue getTypeMachine() {
        return typeMachine;
    }

    public void addCpu(String cpu) {
        cpus.add(new Processor(cpu, this));
    }

    public Boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlightedWithAllCpu(Boolean b) {
        highlighted = b;
        if (b) {
            alpha = FusedVMViewPresentationProvider.fHighlightAlpha;
        } else {
            alpha = FusedVMViewPresentationProvider.fDimAlpha;
        }
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
