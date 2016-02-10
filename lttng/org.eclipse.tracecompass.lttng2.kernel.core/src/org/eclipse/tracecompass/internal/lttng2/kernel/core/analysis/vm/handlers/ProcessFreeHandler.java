package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class ProcessFreeHandler extends VMKernelEventHandler {

    public ProcessFreeHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {

        Integer tid = ((Long) event.getContent().getField(getLayout().fieldTid()).getValue()).intValue();
        String machineName = event.getTrace().getName();
        /*
         * Remove the process and all its sub-attributes from the current state
         */
        int quark = ss.getQuarkRelativeAndAdd(KernelEventHandlerUtils.getNodeThreads(ss), machineName, tid.toString());
        ss.removeAttribute(KernelEventHandlerUtils.getTimestamp(event), quark);
    }
}
