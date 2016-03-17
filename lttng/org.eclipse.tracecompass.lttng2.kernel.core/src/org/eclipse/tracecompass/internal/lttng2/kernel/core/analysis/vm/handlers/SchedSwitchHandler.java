package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandlerUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineStateProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.LinuxValues;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class SchedSwitchHandler extends VMKernelEventHandler {

    public SchedSwitchHandler(IKernelAnalysisEventLayout layout, FusedVirtualMachineStateProvider sp) {
        super(layout, sp);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        Integer cpu = KernelEventHandlerUtils.getCpu(event);
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
            }
        }

        ITmfEventField content = event.getContent();
        Integer prevTid = ((Long) content.getField(getLayout().fieldPrevTid()).getValue()).intValue();
        Long prevState = checkNotNull((Long) content.getField(getLayout().fieldPrevState()).getValue());
        String nextProcessName = checkNotNull((String) content.getField(getLayout().fieldNextComm()).getValue());
        Integer nextTid = ((Long) content.getField(getLayout().fieldNextTid()).getValue()).intValue();
        Integer nextPrio = ((Long) content.getField(getLayout().fieldNextPrio()).getValue()).intValue();
        String machineName = event.getTrace().getName();

        int nodeThreads = KernelEventHandlerUtils.getNodeThreads(ss);
        int formerThreadNode = ss.getQuarkRelativeAndAdd(nodeThreads, machineName, prevTid.toString());
        int newCurrentThreadNode = ss.getQuarkRelativeAndAdd(nodeThreads, machineName, nextTid.toString());

        long timestamp = KernelEventHandlerUtils.getTimestamp(event);
        /* Set the status of the process that got scheduled out. */
        setOldProcessStatus(ss, prevState, formerThreadNode, timestamp);

        /* Set the status of the new scheduled process */
        KernelEventHandlerUtils.setProcessToRunning(timestamp, newCurrentThreadNode, ss);

        /* Set the exec name of the new process */
        setNewProcessExecName(ss, nextProcessName, newCurrentThreadNode, timestamp);

        /* Set the current prio for the new process */
        setNewProcessPio(ss, nextPrio, newCurrentThreadNode, timestamp);

        /* Make sure the PPID and system_call sub-attributes exist */
        ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
        ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);

        /* Set the current scheduled process on the relevant CPU */
        int currentCPUNode = KernelEventHandlerUtils.getCurrentCPUNode(cpu, ss);
        ITmfStateValue stateProcess = setCpuProcess(ss, nextTid, timestamp, currentCPUNode);

        /* Set the status of the CPU itself */
        ITmfStateValue stateCpu = setCpuStatus(ss, nextTid, newCurrentThreadNode, timestamp, currentCPUNode);

        cpuObject.setCurrentState(stateCpu);
        cpuObject.setCurrentThread(stateProcess);
    }

    private static void setOldProcessStatus(ITmfStateSystemBuilder ss, Long prevState, Integer formerThreadNode, long timestamp) throws AttributeNotFoundException {
        ITmfStateValue value;
        /*
         * Empirical observations and look into the linux code have shown that
         * the TASK_STATE_MAX flag is used internally and |'ed with other
         * states, most often the running state, so it is ignored from the
         * prevState value.
         */
        int state = (int) (prevState & ~(LinuxValues.TASK_STATE_MAX));

        switch (state) {
        case LinuxValues.TASK_STATE_RUNNING:
            value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
            break;
        case LinuxValues.TASK_INTERRUPTIBLE:
        case LinuxValues.TASK_UNINTERRUPTIBLE:
            value = StateValues.PROCESS_STATUS_WAIT_BLOCKED_VALUE;
            break;
        case LinuxValues.TASK_DEAD:
            value = TmfStateValue.nullValue();
            break;
        default:
            value = StateValues.PROCESS_STATUS_WAIT_UNKNOWN_VALUE;
            break;
        }
        int quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.STATUS);
        ss.modifyAttribute(timestamp, value, quark);

    }

    private static ITmfStateValue setCpuStatus(ITmfStateSystemBuilder ss, Integer nextTid, Integer newCurrentThreadNode, long timestamp, int currentCPUNode) throws AttributeNotFoundException {
        int quark;
        ITmfStateValue value;
        if (nextTid > 0) {
            /* Check if the entering process is in kernel or user mode */
            quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
            ITmfStateValue splitValue;
            ITmfStateValue queryOngoingState = ss.queryOngoingState(quark);
            if (queryOngoingState.isNull()) {
                value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                splitValue = StateValues.CPU_STATUS_SWITCH_TO_USERMODE_VALUE;
            } else {
                value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                splitValue = StateValues.CPU_STATUS_SWITCH_TO_SYSCALL_VALUE;
            }
            quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
            /*
             * Add a state to split two consecutive same states.
             */
            /**
             * TODO: find a better way to do that. The answer should be in the view.
             */
            ss.modifyAttribute(timestamp, splitValue, quark);
            ss.modifyAttribute(timestamp + 1, value, quark);
        } else {
            value = StateValues.CPU_STATUS_IDLE_VALUE;
            quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
            ss.modifyAttribute(timestamp, value, quark);
        }
        return value;

    }

    private static ITmfStateValue setCpuProcess(ITmfStateSystemBuilder ss, Integer nextTid, long timestamp, int currentCPUNode) throws AttributeNotFoundException {
        int quark;
        ITmfStateValue value;
        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
        value = TmfStateValue.newValueInt(nextTid);
        ss.modifyAttribute(timestamp, value, quark);
        return value;
    }

    private static void setNewProcessPio(ITmfStateSystemBuilder ss, Integer nextPrio, Integer newCurrentThreadNode, long timestamp) throws AttributeNotFoundException {
        int quark;
        ITmfStateValue value;
        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PRIO);
        value = TmfStateValue.newValueInt(nextPrio);
        ss.modifyAttribute(timestamp, value, quark);
    }

    private static void setNewProcessExecName(ITmfStateSystemBuilder ss, String nextProcessName, Integer newCurrentThreadNode, long timestamp) throws AttributeNotFoundException {
        int quark;
        ITmfStateValue value;
        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.EXEC_NAME);
        value = TmfStateValue.newValueString(nextProcessName);
        ss.modifyAttribute(timestamp, value, quark);
    }

}
