package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.LinuxValues;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandler;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class StateDumpHandler extends KernelEventHandler {

    private List<VirtualThread> virtualThreads;

    private class VirtualThread {
        public final int fVtid;
        public final int fVpid;
        public final int fVppid;
        public final int fNsLevel;
        public final long fNsInum;

        public VirtualThread(int vtid, int vpid, int vppid, int nsLevel, long nsInum) {
            fVtid = vtid;
            fVpid = vpid;
            fVppid = vppid;
            fNsLevel = nsLevel;
            fNsInum = nsInum;
        }
    }

    public StateDumpHandler(IKernelAnalysisEventLayout layout) {
        super(layout);
        virtualThreads = new ArrayList<>();
    }

    @Override
    public void handleEvent(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event) throws AttributeNotFoundException {
        final long ts = event.getTimestamp().getValue();
        ITmfEventField content = event.getContent();
        String machineName = event.getTrace().getName();
        ITmfStateValue value;
        int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
        int status = ((Long) content.getField("status").getValue()).intValue(); //$NON-NLS-1$
        String name = (String) content.getField("name").getValue(); //$NON-NLS-1$
        int vtid = ((Long) content.getField("vtid").getValue()).intValue(); //$NON-NLS-1$
        int vpid = ((Long) content.getField("vpid").getValue()).intValue(); //$NON-NLS-1$
        int vppid = ((Long) content.getField("vppid").getValue()).intValue(); //$NON-NLS-1$
        int nsLevel = ((Long) content.getField("ns_level").getValue()).intValue(); //$NON-NLS-1$
        long nsInum = (Long) content.getField("ns_inum").getValue(); //$NON-NLS-1$
        /*
         * "mode" could be interesting too, but it doesn't seem to be populated
         * with anything relevant for now.
         */

        virtualThreads.add(0, new VirtualThread(vtid, vpid, vppid, nsLevel, nsInum));
        if (nsLevel != 0) {
            return;
        }

        System.err.println("Adding name of: " + tid + "\tMachine: " + machineName);
        int curThreadNode = ss.getQuarkRelativeAndAdd(KernelEventHandlerUtils.getNodeThreads(ss), machineName, String.valueOf(tid));
        /* Set the process' name. Only for level 0. */
        int quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.EXEC_NAME);
        if (ss.queryOngoingState(quark).isNull()) {
            /* If the value didn't exist previously, set it */
            value = TmfStateValue.newValueString(name);
            ss.modifyAttribute(ts, value, quark);
        }
        /* Set the process' status. Only for level 0. */
        quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.STATUS);
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

        /* We go through all the namespaces */
        for (VirtualThread vt : virtualThreads) {
            System.err.println("\tInfo of TID: " + vt.fVtid + " Level: " + vt.fNsLevel);
            String attributePpid = Attributes.PPID;
            /* Prepare the level if we are not in the root namespave */
            if (vt.fNsLevel != 0) {
                attributePpid = "VPPID";
                /* Create the node */
                curThreadNode = ss.getQuarkRelativeAndAdd(curThreadNode, "VTID");
                /* Set the VTID */
                value = TmfStateValue.newValueInt(vt.fVtid);
                ss.modifyAttribute(ts, value, curThreadNode);
            }

            /* Set the process' PPID */
            quark = ss.getQuarkRelativeAndAdd(curThreadNode, attributePpid);
            if (ss.queryOngoingState(quark).isNull()) {
                if (vt.fVpid == vt.fVtid) {
                    /* We have a process. Use the 'PPID' field. */
                    value = TmfStateValue.newValueInt(vt.fVppid);
                } else {
                    /*
                     * We have a thread, use the 'PID' field for the parent.
                     */
                    value = TmfStateValue.newValueInt(vt.fVpid);
                }
                ss.modifyAttribute(ts, value, quark);
            }
            /* TODO: create attributes */
            /* Set the namespace level */
            quark = ss.getQuarkRelativeAndAdd(curThreadNode, "ns_level");
            if (ss.queryOngoingState(quark).isNull()) {
                /* If the value didn't exist previously, set it */
                value = TmfStateValue.newValueInt(vt.fNsLevel);
                ss.modifyAttribute(ts, value, quark);
            }
            /* Set the namespace identification number */
            quark = ss.getQuarkRelativeAndAdd(curThreadNode, "ns_inum");
            if (ss.queryOngoingState(quark).isNull()) {
                /* If the value didn't exist previously, set it */
                value = TmfStateValue.newValueLong(vt.fNsInum);
                ss.modifyAttribute(ts, value, quark);
            }
        }
        /*
         * All events for one thread processed, clear the list for this thread
         */
        virtualThreads.clear();

    }

}
