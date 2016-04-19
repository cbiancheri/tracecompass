package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class SchedWakeupHandler extends VMKernelEventHandler {

    public SchedWakeupHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
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
        if (host != null && host.isGuest()) {
            Integer physicalCPU = sp.getPhysicalCPU(host, cpu);
            if (physicalCPU != null) {
                cpu = physicalCPU;
            }
        }
        String machineName = event.getTrace().getName();
        final int tid = ((Long) event.getContent().getField(getLayout().fieldTid()).getValue()).intValue();
        final int prio = ((Long) event.getContent().getField(getLayout().fieldPrio()).getValue()).intValue();

        String threadAttributeName = FusedVMEventHandlerUtils.buildThreadAttributeName(tid, cpu);
        if (threadAttributeName == null) {
            return;
        }

        final int threadNode = ss.getQuarkRelativeAndAdd(FusedVMEventHandlerUtils.getNodeThreads(ss), machineName, threadAttributeName);

        /*
         * The process indicated in the event's payload is now ready to run.
         * Assign it to the "wait for cpu" state, but only if it was not already
         * running.
         */
        int quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.STATUS);
        int status = ss.queryOngoingState(quark).unboxInt();
        ITmfStateValue value = null;
        long timestamp = FusedVMEventHandlerUtils.getTimestamp(event);
        if (status != StateValues.PROCESS_STATUS_RUN_SYSCALL &&
                status != StateValues.PROCESS_STATUS_RUN_USERMODE) {
            value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
            ss.modifyAttribute(timestamp, value, quark);
        }

        /*
         * When a user changes a threads prio (e.g. with pthread_setschedparam),
         * it shows in ftrace with a sched_wakeup.
         */
        quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.PRIO);
        value = TmfStateValue.newValueInt(prio);
        ss.modifyAttribute(timestamp, value, quark);
    }
}
