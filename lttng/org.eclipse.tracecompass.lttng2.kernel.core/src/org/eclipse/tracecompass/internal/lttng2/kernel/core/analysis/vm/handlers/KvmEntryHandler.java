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
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class KvmEntryHandler extends VMKernelEventHandler {

    public KvmEntryHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
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

        ITmfStateValue value = ss.queryOngoingState(quark);
        int thread = value.isNull() ? -1 : value.unboxInt();

        VirtualMachine host = sp.getCurrentMachine(event);
        if (host == null) {
            return;
        }

        /* We are entering a vm. */
        sp.replaceValueCpusInVM(cpu, true);

        /* Add the condition in_vm in the state system. */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
        value = StateValues.CONDITION_IN_VM_VALUE;
        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        ss.modifyAttribute(timestamp, value, quark);

        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);

        /* Get the host CPU doing the kvm_entry. */
        VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        /*
         * Saves the state. Will be restored after a kvm_exit.
         */
        ITmfStateValue ongoingState = ss.queryOngoingState(quark);
        if (ongoingState != null) {
            hostCpu.setCurrentState(ongoingState);
        }
        /*
         * Get the host thread to get the right virtual machine.
         */
        HostThread ht = new HostThread(event.getTrace().getHostId(), thread);
        VirtualMachine virtualMachine = sp.getVmFromHostThread(ht);
        if (virtualMachine == null) {
            return;
        }

        VirtualCPU vcpu = sp.getVirtualCpu(ht);
        if (vcpu == null) {
            return;
        }

        Integer currentVCpu = vcpu.getCpuId().intValue();

        /*
         * If not already done, associate the TID in the host corresponding to
         * the vCPU inside the state system.
         */
        int quarkMachines = FusedVirtualMachineStateProvider.getNodeMachines(ss);
        int quarkVCPU = ss.getQuarkRelativeAndAdd(quarkMachines, virtualMachine.getTraceName(), vcpu.getCpuId().toString());
        if (ss.queryOngoingState(quarkVCPU).isNull()) {
            ss.modifyAttribute(timestamp, TmfStateValue.newValueInt(thread), quarkVCPU);
        }


        /* Set the value of the vcpu that is going to run. */
        int quarkVCpu = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.VIRTUAL_CPU);
        ITmfStateValue valueVCpu = TmfStateValue.newValueInt(currentVCpu);
        ss.modifyAttribute(timestamp, valueVCpu, quarkVCpu);

        /*
         * Set the name of the VM that will run just after the kvm_entry
         */
        int machineNameQuark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.MACHINE_NAME);
        value = TmfStateValue.newValueString(virtualMachine.getTraceName());
        ss.modifyAttribute(timestamp, value, machineNameQuark);

        /*
         * When the states of the vm and the host are the same the transition is
         * not detected by the view so we add a false state that lasts 1 ns to
         * make the transition visible. TODO: Find a better way to handle this
         * problem.
         */
        /*
         * Then the current state of the vm is restored.
         */
        if (hostCpu.getCurrentState() == vcpu.getCurrentState()) {
            value = StateValues.CPU_STATUS_IN_VM_VALUE;
            ss.modifyAttribute(timestamp, value, quark);
            value = vcpu.getCurrentState();
            ss.modifyAttribute(timestamp + 1, value, quark);
        } else {
            value = vcpu.getCurrentState();
            ss.modifyAttribute(timestamp, value, quark);
        }

        /*
         * Save the current thread of the host that was running.
         */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
        ongoingState = ss.queryOngoingState(quark);
        if (ongoingState != null) {
            hostCpu.setCurrentThread(ongoingState);
        }
        /* Restore the thread of the VM that was running. */
        value = vcpu.getCurrentThread();
        ss.modifyAttribute(timestamp, value, quark);

    }

}