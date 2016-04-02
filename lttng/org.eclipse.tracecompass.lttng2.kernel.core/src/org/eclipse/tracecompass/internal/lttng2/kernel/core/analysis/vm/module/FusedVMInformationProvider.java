package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;

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

    public static int getNodeThreadsAndAdd(ITmfStateSystemBuilder ssq) {
        return ssq.getQuarkAbsoluteAndAdd(Attributes.THREADS);
    }

    public static int getNodeThreads(ITmfStateSystem ssq) {
        try {
            return ssq.getQuarkAbsolute(Attributes.THREADS);
        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
    }

    public static @Nullable ITmfStateValue getTypeMachine(ITmfStateSystem ssq, String machineName) {
        int quark;
        try {
            quark = ssq.getQuarkAbsolute(Attributes.MACHINES, machineName);
            return ssq.querySingleState(ssq.getStartTime(), quark).getStateValue();
        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (StateSystemDisposedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

}
