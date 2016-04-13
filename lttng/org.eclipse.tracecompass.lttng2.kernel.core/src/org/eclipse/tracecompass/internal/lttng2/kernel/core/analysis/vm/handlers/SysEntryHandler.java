package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.tracecompass.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class SysEntryHandler extends VMKernelEventHandler {

    public SysEntryHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        Integer cpu = KernelEventHandlerUtils.getCpu(event);
        if (cpu == null) {
            return;
        }
        FusedVirtualMachineStateProvider sp = getStateProvider();
        VirtualMachine host = sp.getCurrentMachine(event);
        if (host != null && host.isGuest()) {
            Integer physicalCPU = sp.getPhysicalCPU(host, cpu);
            if (physicalCPU != null) {
                cpu = physicalCPU;
            }
        }
        /* Assign the new system call to the process */
        int currentThreadNode = FusedVirtualMachineStateProvider.getCurrentThreadNode(cpu, ss);
        int quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
        ITmfStateValue value = TmfStateValue.newValueString(event.getName());
        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        ss.modifyAttribute(timestamp, value, quark);

        /* Put the process in system call mode */
        quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
        value = StateValues.PROCESS_STATUS_RUN_SYSCALL_VALUE;
        ss.modifyAttribute(timestamp, value, quark);

        /* Put the CPU in system call (kernel) mode */
        int currentCPUNode = KernelEventHandlerUtils.getCurrentCPUNode(cpu, ss);
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
        value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
        ss.modifyAttribute(timestamp, value, quark);
    }

}
