package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class KvmExitHandler extends VMKernelEventHandler {

    public KvmExitHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(@NonNull ITmfStateSystemBuilder ss, @NonNull ITmfEvent event) throws AttributeNotFoundException {
        Integer cpu = KernelEventHandlerUtils.getCpu(event);
        if (cpu == null) {
            return;
        }
        FusedVirtualMachineStateProvider sp = getStateProvider();
        int currentCPUNode = FusedVirtualMachineStateProvider.getCurrentCPUNode(cpu, ss);
        /*
         * Shortcut for the "current thread" attribute node. It requires
         * querying the current CPU's current thread.
         */
        int quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);

        ITmfStateValue value;

        VirtualMachine host = sp.getCurrentMachine(event);
        if (host == null) {
            return;
        }

        sp.replaceValueCpusInVM(cpu, false);
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);

        /* Get the host CPU doing the kvm_exit. */
        VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        /*
         * Get the host thread to get the right virtual machine.
         */
        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        HostThread ht = sp.getCurrentHostThread(event, timestamp);
        if (ht == null) {
            return;
        }
        VirtualCPU vcpu = sp.getVCpuEnteringHypervisorMode(event, ht);
        if (vcpu == null) {
            return;
        }

        /* Save the state of the VCpu. */
        ITmfStateValue ongoingState = ss.queryOngoingState(quark);
        if (ongoingState != null) {
            vcpu.setCurrentState(ongoingState);
        }

        /*
         * When the states of the vm and the host are the same the
         * transition is not detected by the view so we add a false
         * state that lasts 1 ns to make the transition visible.
         * TODO: Find a better way to handle this problem.
         */
        /* Then the current state of the host is restored. */
        if (hostCpu.getCurrentState() == vcpu.getCurrentState()) {
            value = StateValues.CPU_STATUS_IN_VM_VALUE;
            ss.modifyAttribute(timestamp - 1, value, quark);
            value = hostCpu.getCurrentState();
            ss.modifyAttribute(timestamp, value, quark);
        } else {
            value = hostCpu.getCurrentState();
            ss.modifyAttribute(timestamp, value, quark);
        }

        /*
         * Save the current thread of the vm that was running.
         */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
        ongoingState = ss.queryOngoingState(quark);
        if (ongoingState != null) {
            vcpu.setCurrentThread(ongoingState);
        }

        /* Restore the thread of the host that was running. */
        value = hostCpu.getCurrentThread();
        ss.modifyAttribute(timestamp, value, quark);

        /* Add the condition out_vm in the state system. */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
        value = StateValues.CONDITION_OUT_VM_VALUE;
        ss.modifyAttribute(timestamp, value, quark);


    }

}