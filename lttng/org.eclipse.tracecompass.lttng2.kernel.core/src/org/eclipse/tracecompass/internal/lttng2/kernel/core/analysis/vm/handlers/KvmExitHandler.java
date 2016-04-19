package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
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
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        Integer cpu = FusedVMEventHandlerUtils.getCpu(event);
        if (cpu == null) {
            return;
        }
        FusedVirtualMachineStateProvider sp = getStateProvider();
        int currentCPUNode = FusedVMEventHandlerUtils.getCurrentCPUNode(cpu, ss);
        int quark;

        ITmfStateValue value;

        VirtualMachine host = sp.getCurrentMachine(event);
        if (host == null) {
            return;
        }

        /* Getting out of a VM */
        sp.replaceValueCpusInVM(cpu, false);

        /* Get the host CPU doing the kvm_exit. */
        VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        /*
         * Get the host thread to get the right virtual machine.
         */
        long timestamp = FusedVMEventHandlerUtils.getTimestamp(event);
        value = hostCpu.getCurrentThread();
        HostThread ht = new HostThread(host.getHostId(), value.unboxInt());
        VirtualCPU vcpu = sp.getVirtualCpu(ht);
        if (vcpu == null) {
            return;
        }

        /* Save the state of the VCpu. */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
        ITmfStateValue ongoingState = ss.queryOngoingState(quark);
        vcpu.setCurrentState(ongoingState);

        /*
         * When the states of the vm and the host are the same the transition is
         * not detected by the view so we add a false state that lasts 1 ns to
         * make the transition visible. TODO: Find a better way to handle this
         * problem.
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
        vcpu.setCurrentThread(ongoingState);

        /* Restore the thread of the host that was running. */
        value = hostCpu.getCurrentThread();
        ss.modifyAttribute(timestamp, value, quark);

        /* Add the condition out_vm in the state system. */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
        value = StateValues.CONDITION_OUT_VM_VALUE;
        ss.modifyAttribute(timestamp, value, quark);

    }

}
