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
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class KvmEntryHandler extends VMKernelEventHandler {

    public KvmEntryHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
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
        long timestamp = FusedVMEventHandlerUtils.getTimestamp(event);
        ss.modifyAttribute(timestamp, value, quark);

        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);

        /* Get the host CPU doing the kvm_entry. */
        VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        /*
         * Saves the state. Will be restored after a kvm_exit.
         */
        ITmfStateValue ongoingState = ss.queryOngoingState(quark);
        hostCpu.setCurrentState(ongoingState);
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
        int quarkVCPUs = FusedVMEventHandlerUtils.getMachineCPUsNode(ss, virtualMachine.getTraceName());
        int quarkVCPU = ss.getQuarkRelativeAndAdd(quarkVCPUs, vcpu.getCpuId().toString());
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
         * Then the current state of the vm is restored.
         */
        value = vcpu.getCurrentState();
        ss.modifyAttribute(timestamp, value, quark);

        /*
         * Save the current thread of the host that was running.
         */
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
        ongoingState = ss.queryOngoingState(quark);
        hostCpu.setCurrentThread(ongoingState);
        /* Restore the thread of the VM that was running. */
        value = vcpu.getCurrentThread();
        ss.modifyAttribute(timestamp, value, quark);

    }

}
