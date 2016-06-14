package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class SoftIrqExitHandler extends VMKernelEventHandler {

    public SoftIrqExitHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        Integer cpu = FusedVMEventHandlerUtils.getCpu(event);
        if (cpu == null) {
            return;
        }
        FusedVirtualMachineStateProvider sp = getStateProvider();
        VirtualMachine host = sp.getCurrentMachine(event);
        VirtualCPU cpuObject = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        if (host != null && host.isGuest()) {
            Integer physicalCPU = sp.getPhysicalCPU(host, cpu);
            if (physicalCPU != null) {
                cpu = physicalCPU;
            } else {
                return;
            }
        }

        Integer softIrqId = ((Long) event.getContent().getField(getLayout().fieldVec()).getValue()).intValue();
        int currentThreadNode = FusedVMEventHandlerUtils.getCurrentThreadNode(cpu, ss);
        /* Put this SoftIRQ back to inactive (= -1) in the resource tree */
        int quark = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeSoftIRQs(cpu, ss), softIrqId.toString());
        long timestamp = FusedVMEventHandlerUtils.getTimestamp(event);

        if (isSoftIrqRaised(ss.queryOngoingState(quark))) {
            ss.modifyAttribute(timestamp, StateValues.SOFT_IRQ_RAISED_VALUE, quark);
        } else {
            ss.modifyAttribute(timestamp, TmfStateValue.nullValue(), quark);
        }
        List<Integer> softIrqs = ss.getSubAttributes(ss.getParentAttributeQuark(quark), false);
        /* Only set status to running and no exit if ALL softirqs are exited. */
        for (Integer softIrq : softIrqs) {
            @NonNull ITmfStateValue irqStateValue = ss.queryOngoingState(softIrq);
            if (!irqStateValue.isNull()) { // && !(irqStateValue.unboxInt() == StateValues.CPU_STATUS_IRQ)) {
                return;
            }
        }

        /* Set the previous process back to running */
        FusedVMEventHandlerUtils.setProcessToRunning(timestamp, currentThreadNode, ss);

        /* Set the CPU status back to "busy" or "idle" */
//        FusedVMEventHandlerUtils.cpuExitInterrupt(timestamp, cpu, ss);
        quark = ss.getQuarkRelativeAndAdd(FusedVirtualMachineStateProvider.getCurrentCPUNode(cpu, ss), Attributes.STATUS);
        ITmfStateValue value = cpuObject.getStateBeforeIRQ();
        ss.modifyAttribute(timestamp, value, quark);

    }

    /**
     * This checks if the running <stong>bit</strong> is set
     *
     * @param state
     *            the state to check
     * @return true if in a softirq. The softirq may be pre-empted by an irq
     */
    private static boolean isSoftIrqRaised(@Nullable ITmfStateValue state) {
        return (state != null &&
                !state.isNull() &&
                (state.unboxInt() & StateValues.CPU_STATUS_SOFT_IRQ_RAISED) == StateValues.CPU_STATUS_SOFT_IRQ_RAISED);
    }

}
