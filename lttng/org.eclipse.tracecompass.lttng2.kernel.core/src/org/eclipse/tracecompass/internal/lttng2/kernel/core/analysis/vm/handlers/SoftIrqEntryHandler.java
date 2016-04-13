package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVMInformationProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class SoftIrqEntryHandler extends VMKernelEventHandler {

    public SoftIrqEntryHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event) throws AttributeNotFoundException {
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

        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        Integer softIrqId = ((Long) event.getContent().getField(getLayout().fieldVec()).getValue()).intValue();
        int currentCPUNode = KernelEventHandlerUtils.getCurrentCPUNode(cpu, ss);
        int currentThreadNode = FusedVirtualMachineStateProvider.getCurrentThreadNode(cpu,ss);

        /*
         * Mark this SoftIRQ as active in the resource tree. The state value =
         * the CPU on which this SoftIRQ is processed
         */
        int quark = ss.getQuarkRelativeAndAdd(FusedVMInformationProvider.getNodeSoftIRQs(ss), softIrqId.toString());
        ITmfStateValue value = TmfStateValue.newValueInt(cpu.intValue());
        ss.modifyAttribute(timestamp, value, quark);

        /* Change the status of the running process to interrupted */
        quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
        value = StateValues.PROCESS_STATUS_INTERRUPTED_VALUE;
        ss.modifyAttribute(timestamp, value, quark);

        /* Change the status of the CPU to interrupted */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
        value = StateValues.CPU_STATUS_SOFTIRQ_VALUE;
        ss.modifyAttribute(timestamp, value, quark);

    }

}
