package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
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
        List<Integer> vCpuquarks = ssq.getQuarks(Attributes.MACHINES, machineName, Attributes.CPUS, "*"); //$NON-NLS-1$
        return vCpuquarks.size();
    }

    public static List<String> getMachineContainers(ITmfStateSystem ssq, String machineName) {
            List<String> containers = new LinkedList<>();
            List<Integer> containersQuark = ssq.getQuarks(Attributes.MACHINES, machineName, Attributes.CONTAINERS, "*");
            for (Integer containerQuark : containersQuark) {
                containers.add(ssq.getAttributeName(containerQuark));
            }
            return containers;
        }

    public static List<Integer> getMachineContainersQuarks(ITmfStateSystem ssq, String machineName) {
        return ssq.getQuarks(Attributes.MACHINES, machineName, Attributes.CONTAINERS, "*");
    }

    public static int getContainerQuark(ITmfStateSystem ssq, String machineName, String containerID) {
        try {
            return ssq.getQuarkAbsolute(Attributes.MACHINES, machineName, Attributes.CONTAINERS, containerID);
        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
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

    public static int saveContainerThreadID(ITmfStateSystemBuilder ss, int quark,int tid) {
        return ss.getQuarkRelativeAndAdd(quark, Attributes.THREADS, Integer.toString(tid));
    }

    public static int getMachineCPUsNode(ITmfStateSystemBuilder ssq, String machineName) {
        return ssq.getQuarkAbsoluteAndAdd(Attributes.MACHINES, machineName, Attributes.CPUS);
    }

    public static int getNodeIRQs(ITmfStateSystemBuilder ssq) {
        return ssq.getQuarkAbsoluteAndAdd(Attributes.IRQS);
    }

    public static int getNodeSoftIRQs(ITmfStateSystemBuilder ssq) {
        return ssq.getQuarkAbsoluteAndAdd(Attributes.SOFT_IRQS);
    }

    public static int getNodeNsInum(ITmfStateSystem ssq, long time, String machineName, Integer threadID) throws AttributeNotFoundException, StateSystemDisposedException {
        int quark = ssq.getQuarkRelative(FusedVMInformationProvider.getNodeThreads(ssq), machineName, Integer.toString(threadID), Attributes.NS_MAX_LEVEL);
        ITmfStateInterval interval = ssq.querySingleState(time, quark);
        quark = ssq.getQuarkRelative(FusedVMInformationProvider.getNodeThreads(ssq), machineName, Integer.toString(threadID));
        int nsMaxLevel = interval.getStateValue().unboxInt();
        for (int i = 1; i < nsMaxLevel; i++) {
            quark = ssq.getQuarkRelative(quark, Attributes.VTID);
        }
        return ssq.getQuarkRelative(quark, Attributes.NS_INUM);
    }

    public static Long getParentContainer(ITmfStateSystem ssq, int containerQuark) {
        int parentContainerIDQuark;
        Long parentContainerID = null;
        try {
            parentContainerIDQuark = ssq.getQuarkRelative(containerQuark, Attributes.PARENT);
            parentContainerID = ssq.querySingleState(ssq.getStartTime(), parentContainerIDQuark).getStateValue().unboxLong();

        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (StateSystemDisposedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return parentContainerID;
    }

    public static List<String> getPCpusUsedByMachine(ITmfStateSystem ssq, String machineName) {
        List<String> pcpus = new LinkedList<>();
        List<Integer> pCpuquarks = new LinkedList<>();
        ITmfStateValue type = getTypeMachine(ssq, machineName);
        if (type == null) {
            return pcpus;
        }
        if (type.unboxInt() == StateValues.MACHINE_GUEST) {
            pCpuquarks = ssq.getQuarks(Attributes.MACHINES, machineName, Attributes.PCPUS, "*"); //$NON-NLS-1$
        } else if (type.unboxInt() == StateValues.MACHINE_HOST) {
            pCpuquarks = ssq.getQuarks(Attributes.MACHINES, machineName, Attributes.CPUS, "*"); //$NON-NLS-1$
        }
        for (Integer quark : pCpuquarks) {
            pcpus.add(ssq.getAttributeName(quark));
        }
        return pcpus;
    }

    public static List<String> getPCpusUsedByContainer(ITmfStateSystem ssq, int quark) {
        List<String> pcpus = new LinkedList<>();
        List<Integer> pCpusQuarks = ssq.getQuarks(quark, Attributes.PCPUS, "*");
        for(int pCpuqQuark : pCpusQuarks) {
            pcpus.add(ssq.getAttributeName(pCpuqQuark));
        }
        return pcpus;
    }

}
