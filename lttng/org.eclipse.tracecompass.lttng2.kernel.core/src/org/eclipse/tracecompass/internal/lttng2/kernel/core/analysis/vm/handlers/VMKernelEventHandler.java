package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public abstract class VMKernelEventHandler {
    private final IKernelAnalysisEventLayout fLayout;
    private FusedVirtualMachineStateProvider stateProvider;

    public VMKernelEventHandler(@NonNull IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        fLayout = layout;
        stateProvider = sp;
    }

    public FusedVirtualMachineStateProvider getStateProvider() {
        return stateProvider;
    }


    /**
     * Get the analysis layout
     *
     * @return the analysis layout
     */
    protected IKernelAnalysisEventLayout getLayout() {
        return fLayout;
    }

    /**
     * Handle a specific kernel event.
     *
     * @param ss
     *            the state system to write to
     * @param event
     *            the event
     * @throws AttributeNotFoundException
     *             if the attribute is not yet create
     */
    public abstract void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException;


}
