package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class ProcessForkContainerHandler extends VMKernelEventHandler {

    public ProcessForkContainerHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        ITmfEventField content = event.getContent();
        ITmfEventField field;
        String machineName = event.getTrace().getName();
        String childProcessName = (String) content.getField(getLayout().fieldChildComm()).getValue();
        long childVTID[] = {-1};
        field = content.getField("vtids"); //$NON-NLS-1$
        if (field != null) {
            childVTID = (long[]) field.getValue();
        }
        long childNSInum;
        field = content.getField("child_ns_inum"); //$NON-NLS-1$
        if (field == null) {
            childNSInum = -1;
        } else {
            childNSInum = (Long) field.getValue();
        }

        Integer parentTid = ((Long) content.getField(getLayout().fieldParentTid()).getValue()).intValue();
        Integer childTid = ((Long) content.getField(getLayout().fieldChildTid()).getValue()).intValue();

        Integer parentTidNode = ss.getQuarkRelativeAndAdd(KernelEventHandlerUtils.getNodeThreads(ss), machineName, parentTid.toString());
        Integer childTidNode = ss.getQuarkRelativeAndAdd(KernelEventHandlerUtils.getNodeThreads(ss), machineName, childTid.toString());

        /* Assign the PPID to the new process */
        int quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.PPID);
        ITmfStateValue value = TmfStateValue.newValueInt(parentTid);
        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        ss.modifyAttribute(timestamp, value, quark);

        /* Set the new process' exec_name */
        quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.EXEC_NAME);
        value = TmfStateValue.newValueString(childProcessName);
        ss.modifyAttribute(timestamp, value, quark);

        /* Set the new process' status */
        quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.STATUS);
        value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
        ss.modifyAttribute(timestamp, value, quark);

        /* Set the process' syscall name, to be the same as the parent's */
        quark = ss.getQuarkRelativeAndAdd(parentTidNode, Attributes.SYSTEM_CALL);
        value = ss.queryOngoingState(quark);
        if (value.isNull()) {
            /*
             * Maybe we were missing info about the parent? At least we will set
             * the child right. Let's suppose "sys_clone".
             */
            value = TmfStateValue.newValueString(getLayout().eventSyscallEntryPrefix() + IKernelAnalysisEventLayout.INITIAL_SYSCALL_NAME);
        }
        quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.SYSTEM_CALL);
        ss.modifyAttribute(timestamp, value, quark);
    }

}

