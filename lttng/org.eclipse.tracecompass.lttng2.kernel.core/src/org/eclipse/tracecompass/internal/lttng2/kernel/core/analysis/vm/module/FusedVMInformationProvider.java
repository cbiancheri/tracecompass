package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;

public class FusedVMInformationProvider {

    private FusedVMInformationProvider() {
    }

    public static List<String> getMachinesTraced(ITmfStateSystem ssq) {
        List<String> list = new LinkedList<>();
        List<Integer> machinesQuarks = ssq.getQuarks(Attributes.MACHINES, "*"); //$NON-NLS-1$
        for (Integer machineQuark : machinesQuarks) {
            String machineName = ssq.getAttributeName(machineQuark);
            list.add(machineName);
        }
        return list;
    }

    public static Integer getNbCPUs(ITmfStateSystem ssq, String machineName) {
        List<Integer> vCpuquarks = ssq.getQuarks(Attributes.MACHINES, machineName, "*"); //$NON-NLS-1$
        return vCpuquarks.size();
    }

}
