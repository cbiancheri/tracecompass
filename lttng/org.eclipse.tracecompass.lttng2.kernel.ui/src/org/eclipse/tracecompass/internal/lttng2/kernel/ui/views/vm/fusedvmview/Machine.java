package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;

public class Machine {

    private String machineName;
    private Machine host = null;
    private Boolean highlighted;
    private int alpha;
    private Set<Processor> cpus = new HashSet<>();
    private Set<Machine> containers = new HashSet<>();
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

    public static Machine createContainer(String name, Machine host) {
        Machine container = new Machine(name, 0, StateValues.MACHINE_CONTAINER_VALUE);
        container.setHost(host);
        return container;
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

    public void setHost(Machine h) {
        host = h;
    }

    public Machine getHost() {
        return host;
    }

    public Set<Machine> getContainers() {
        return containers;
    }

    public void addCpu(String cpu) {
        cpus.add(new Processor(cpu, this));
    }

    public void addContainer(Machine machine) {
        if (machine.getTypeMachine() != StateValues.MACHINE_CONTAINER_VALUE) {
            return;
        }
        containers.add(machine);
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
        for (Processor proc : cpus) {
            if (p.equals(proc.toString())) {
                return proc.isHighlighted();
            }
        }
        return false;
    }

    public Boolean isCpuHighlighted(int p) {
        for (Processor proc : cpus) {
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

    public Boolean areAllCpusNotHighlighted() {
        Boolean res = true;
        for (Processor p : getCpus()) {
            res &= !p.isHighlighted();
        }
        return res;
    }

    public Boolean isOneCpuHighlighted() {
        Boolean res = false;
        for (Processor p : getCpus()) {
            if (p.isHighlighted()) {
                return true;
            }
        }
        return res;
    }

    public Boolean isContainerHighlighted(String c) {
        for (Machine container : getContainers()) {
            if (c.equals(container.getMachineName())) {
                return container.isHighlighted();
            }
        }
        return false;
    }

    public Boolean isContainerHighlighted(long c) {
        for (Machine container : getContainers()) {
            if (c == Long.parseLong(container.getMachineName())) {
                return container.isHighlighted();
            }
        }
        return false;
    }

    public Boolean areAllContainersHighlighted() {
        Boolean res = true;
        for (Machine container : getContainers()) {
            res &= container.isHighlighted();
        }
        return res;
    }

    public Boolean areAllContainersNotHighlighted() {
        Boolean res = true;
        for (Machine container : getContainers()) {
            res &= !container.isHighlighted();
        }
        return res;
    }

    public Boolean isOneContainerHighlighted() {
        Boolean res = false;
        for (Machine container : getContainers()) {
            if (container.isHighlighted()) {
                return true;
            }
        }
        return res;
    }

    public Boolean cpusNodeIsGrayed() {
        return !areAllCpusHighlighted() && isOneCpuHighlighted();
    }

    public Boolean containersNodeIsGrayed() {
        return !areAllContainersHighlighted() && isOneContainerHighlighted();
    }

    public Boolean isGrayed() {
        return !(areAllCpusHighlighted() && areAllContainersHighlighted()) && (isOneCpuHighlighted() || isOneContainerHighlighted());
    }

    public Boolean isChecked() {
        return isOneContainerHighlighted() || isOneCpuHighlighted();
    }

    @Override
    public String toString() {
        return machineName;
    }
}
