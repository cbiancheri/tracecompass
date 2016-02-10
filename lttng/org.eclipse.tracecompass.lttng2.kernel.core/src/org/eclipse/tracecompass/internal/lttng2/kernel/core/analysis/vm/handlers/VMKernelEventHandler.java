package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;

public abstract class VMKernelEventHandler extends KernelEventHandler {
    private FusedVirtualMachineStateProvider stateProvider;

    public VMKernelEventHandler(@NonNull IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout);
        stateProvider = sp;
    }

    public FusedVirtualMachineStateProvider getStateProvider() {
        return stateProvider;
    }

}
