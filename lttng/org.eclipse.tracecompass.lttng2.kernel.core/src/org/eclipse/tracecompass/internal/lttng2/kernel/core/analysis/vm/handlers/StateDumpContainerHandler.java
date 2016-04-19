package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.LinuxValues;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class StateDumpContainerHandler extends VMKernelEventHandler {

    /**
     * @param layout
     */
    public StateDumpContainerHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        int layerNode = createLevels(ss, event);
        if (layerNode !=0) {
            fillLevel(ss, event, layerNode);
        }
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
        Integer cpu = FusedVMEventHandlerUtils.getCpu(event);
        ITmfEventField content = event.getContent();
        int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
        int vtid = ((Long) content.getField("vtid").getValue()).intValue(); //$NON-NLS-1$
        int nsLevel = ((Long) content.getField("ns_level").getValue()).intValue(); //$NON-NLS-1$
        long ts = event.getTimestamp().getValue();
        String machineName = event.getTrace().getName();
        String threadAttributeName = FusedVMEventHandlerUtils.buildThreadAttributeName(tid, cpu);
        if (threadAttributeName == null) {
            return 0;
        }
        int threadNode = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeThreads(ss), machineName, threadAttributeName);
        int layerNode = threadNode;
        int quark;
        ITmfStateValue value;
        for (int i = 0; i < nsLevel; i++) {
            /* While we can go deeper we create an other level */
            layerNode = ss.getQuarkRelativeAndAdd(layerNode, Attributes.VTID);
            if (i + 1 == nsLevel) {
                /*
                 * If the next layer is the last we can add the info contained
                 * in the event
                 */
                value = TmfStateValue.newValueInt(vtid);
                ss.modifyAttribute(ts, value, layerNode);
            }
            ss.getQuarkRelativeAndAdd(layerNode, Attributes.VPPID);
            quark = ss.getQuarkRelativeAndAdd(layerNode, Attributes.NS_LEVEL);
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
        Integer cpu = FusedVMEventHandlerUtils.getCpu(event);
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

        String threadAttributeName = FusedVMEventHandlerUtils.buildThreadAttributeName(tid, cpu);
        if (threadAttributeName == null) {
            return;
        }

        int threadNode = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeThreads(ss), machineName, threadAttributeName);

        /*
         * Set the max level, only at level 0. This can be useful to know the
         * depth of the hierarchy.
         */
        quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.NS_MAX_LEVEL);
        if (ss.queryOngoingState(quark).isNull()) {
            /*
             * Events are coming from the deepest layers first so no need to
             * update the ns_max_level.
             */
            value = TmfStateValue.newValueInt(nsLevel + 1);
            ss.modifyAttribute(ts, value, quark);
        }
        int maxLevel = ss.queryOngoingState(quark).unboxInt();

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
        quark = ss.getQuarkRelativeAndAdd(layerNode, Attributes.NS_LEVEL);
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueInt(nsLevel);
            ss.modifyAttribute(ts, value, quark);
        }

        /* Set the namespace identification number */
        quark = ss.getQuarkRelativeAndAdd(layerNode, Attributes.NS_INUM);
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueLong(nsInum);
            ss.modifyAttribute(ts, value, quark);
        }

        /* Save the namespace id somewhere so it can be reused */
        quark = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeMachines(ss), machineName, Attributes.CONTAINERS, Long.toString(nsInum));

        /* Save the tid in the container. We also keep the vtid */
        quark = ss.getQuarkRelativeAndAdd(quark, Attributes.THREADS, Integer.toString(tid));
        ss.modifyAttribute(ts, TmfStateValue.newValueInt(vtid), quark);

        if (nsLevel != maxLevel - 1) {
            /*
             * We are not at the deepest level. So this namespace is the father
             * of the namespace one layer deeper. We are going to tell him we
             * found his father. That will make him happy.
             */
            quark = ss.getQuarkRelativeAndAdd(layerNode, Attributes.VTID, Attributes.NS_INUM);
            Long childNSInum = ss.queryOngoingState(quark).unboxLong();
            if (childNSInum > 0) {
                quark = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeMachines(ss), machineName, Attributes.CONTAINERS, Long.toString(childNSInum), Attributes.PARENT);
                ss.modifyAttribute(ss.getStartTime(), TmfStateValue.newValueLong(nsInum), quark);
            }
        }

        if (nsLevel == 0) {
            /* Root namespace => no parent */
            quark = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeMachines(ss), machineName, Attributes.CONTAINERS, Long.toString(nsInum), Attributes.PARENT);
            ss.modifyAttribute(ss.getStartTime(), TmfStateValue.newValueLong(-1), quark);
        }

    }

}
