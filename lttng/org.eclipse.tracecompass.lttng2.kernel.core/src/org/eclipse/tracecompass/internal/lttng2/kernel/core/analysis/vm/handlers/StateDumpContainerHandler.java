package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.LinuxValues;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVMInformationProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class StateDumpContainerHandler extends KernelEventHandler {

    /**
     * @param layout
     */
    public StateDumpContainerHandler(IKernelAnalysisEventLayout layout) {
        super(layout);
    }

    @Override
    public void handleEvent(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event) throws AttributeNotFoundException {
        int layerNode = createLevels(ss, event);
        fillLevel(ss, event, layerNode);
    }

    /**
     * Create all the levels of containers for a process inside the state
     * system.
     *
     * @param ss
     *            The state system
     * @param event
     *            The statedump_process event
     * @return The quark of the deepest level
     * @throws StateValueTypeException
     * @throws AttributeNotFoundException
     */
    public static int createLevels(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event) throws StateValueTypeException, AttributeNotFoundException {
        ITmfEventField content = event.getContent();
        int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
        int vtid = ((Long) content.getField("vtid").getValue()).intValue(); //$NON-NLS-1$
        int nsLevel = ((Long) content.getField("ns_level").getValue()).intValue(); //$NON-NLS-1$
        long ts = event.getTimestamp().getValue();
        String machineName = event.getTrace().getName();
        int threadNode = ss.getQuarkRelativeAndAdd(FusedVMInformationProvider.getNodeThreadsAndAdd(ss), machineName, String.valueOf(tid));
        int layerNode = threadNode;
        int quark;
        ITmfStateValue value;
        for (int i = 0; i < nsLevel; i++) {
            layerNode = ss.getQuarkRelativeAndAdd(layerNode, "VTID"); //$NON-NLS-1$
            if (i + 1 == nsLevel) {
                value = TmfStateValue.newValueInt(vtid);
                ss.modifyAttribute(ts, value, layerNode);
            }
            ss.getQuarkRelativeAndAdd(layerNode, "VPPID"); //$NON-NLS-1$
            quark = ss.getQuarkRelativeAndAdd(layerNode, "ns_level"); //$NON-NLS-1$
            if (ss.queryOngoingState(quark).isNull()) {
                /* If the value didn't exist previously, set it */
                value = TmfStateValue.newValueInt(i + 1);
                ss.modifyAttribute(ts, value, quark);
            }
        }
        return layerNode;
    }

    /**
     * Fill the first and last level of a thread node
     *
     * @param ss
     *            The state system
     * @param event
     *            The statedump_process event
     * @param layerNode
     *            The quark of the last level
     * @throws StateValueTypeException
     * @throws AttributeNotFoundException
     */
    public static void fillLevel(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event, int layerNode) throws StateValueTypeException, AttributeNotFoundException {
        ITmfEventField content = event.getContent();
        long ts = event.getTimestamp().getValue();
        int quark;
        ITmfStateValue value;
        String machineName = event.getTrace().getName();
        int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
        int pid = ((Long) content.getField("pid").getValue()).intValue(); //$NON-NLS-1$
        int ppid = ((Long) content.getField("ppid").getValue()).intValue(); //$NON-NLS-1$
        int status = ((Long) content.getField("status").getValue()).intValue(); //$NON-NLS-1$
        String name = (String) content.getField("name").getValue(); //$NON-NLS-1$
        int vtid = ((Long) content.getField("vtid").getValue()).intValue(); //$NON-NLS-1$
        int vpid = ((Long) content.getField("vpid").getValue()).intValue(); //$NON-NLS-1$
        int vppid = ((Long) content.getField("vppid").getValue()).intValue(); //$NON-NLS-1$
        int nsLevel = ((Long) content.getField("ns_level").getValue()).intValue(); //$NON-NLS-1$
        long nsInum = (Long) content.getField("ns_inum").getValue(); //$NON-NLS-1$

        int threadNode = ss.getQuarkRelativeAndAdd(FusedVMInformationProvider.getNodeThreadsAndAdd(ss), machineName, String.valueOf(tid));

        /*
         * Set the max level, only at level 0. This can be useful to know the
         * depth of the hierarchy.
         */
        quark = ss.getQuarkRelativeAndAdd(threadNode, "ns_max_level"); //$NON-NLS-1$
        if (ss.queryOngoingState(quark).isNull()) {
            /*
             * Events are coming from the deepest layers first so no need to
             * update the ns_max_level.
             */
            value = TmfStateValue.newValueInt(nsLevel + 1);
            ss.modifyAttribute(ts, value, quark);
        }

        /*
         * Set the process' status. Only for level 0.
         */
        quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.STATUS);
        if (ss.queryOngoingState(quark).isNull()) {
            switch (status) {
            case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT_CPU:
                value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                break;
            case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT:
                /*
                 * We have no information on what the process is waiting on
                 * (unlike a sched_switch for example), so we will use the
                 * WAIT_UNKNOWN state instead of the "normal" WAIT_BLOCKED
                 * state.
                 */
                value = StateValues.PROCESS_STATUS_WAIT_UNKNOWN_VALUE;
                break;
            default:
                value = StateValues.PROCESS_STATUS_UNKNOWN_VALUE;
            }
            ss.modifyAttribute(ts, value, quark);
        }

        /*
         * Set the process' name. Only for level 0.
         */
        quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.EXEC_NAME);
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueString(name);
            ss.modifyAttribute(ts, value, quark);
        }

        String attributePpid = Attributes.PPID;
        /* Prepare the level if we are not in the root namespace */
        if (nsLevel != 0) {
            attributePpid = "VPPID"; //$NON-NLS-1$
        }

        /* Set the process' PPID */
        quark = ss.getQuarkRelativeAndAdd(layerNode, attributePpid);
        ITmfStateValue valuePpid;
        if (ss.queryOngoingState(quark).isNull()) {
            if (vpid == vtid) {
                /* We have a process. Use the 'PPID' field. */
                value = TmfStateValue.newValueInt(vppid);
                valuePpid = TmfStateValue.newValueInt(ppid);
            } else {
                /*
                 * We have a thread, use the 'PID' field for the parent.
                 */
                value = TmfStateValue.newValueInt(vpid);
                valuePpid = TmfStateValue.newValueInt(pid);
            }
            ss.modifyAttribute(ts, value, quark);
            if (nsLevel != 0) {
                /* Set also for the root layer */
                quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.PPID);
                if (ss.queryOngoingState(quark).isNull()) {
                    ss.modifyAttribute(ts, valuePpid, quark);
                }
            }
        }

        /* Set the namespace level */
        quark = ss.getQuarkRelativeAndAdd(layerNode, "ns_level"); //$NON-NLS-1$
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueInt(nsLevel);
            ss.modifyAttribute(ts, value, quark);
        }

        /* Set the namespace identification number */
        quark = ss.getQuarkRelativeAndAdd(layerNode, "ns_inum"); //$NON-NLS-1$
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueLong(nsInum);
            ss.modifyAttribute(ts, value, quark);
        }

        /* Save the namespace id somewhere so it can be reused */
        quark = ss.getQuarkRelativeAndAdd(FusedVirtualMachineStateProvider.getNodeMachines(ss), Long.toString(nsInum));
        if (ss.queryOngoingState(quark).isNull()) {
            ITmfStateValue machineState = org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues.MACHINE_CONTAINER_VALUE;
            ss.modifyAttribute(event.getTrace().getStartTime().getValue(), machineState, quark);
        }

    }

}
