/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Cédric Biancheri - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelThreadInformationProvider;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.qemukvm.QemuKvmStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.qemukvm.QemuKvmVmModel;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperimentUtils;

import com.google.common.collect.ImmutableMap;

/**
 * @author Cedric Biancheri
 *
 */
public class FusedVirtualMachineStateProvider extends AbstractTmfStateProvider {
    // ------------------------------------------------------------------------
    // Static fields
    // ------------------------------------------------------------------------

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static final int VERSION = 9;

    private static final int IRQ_HANDLER_ENTRY_INDEX = 1;
    private static final int IRQ_HANDLER_EXIT_INDEX = 2;
    private static final int SOFT_IRQ_ENTRY_INDEX = 3;
    private static final int SOFT_IRQ_EXIT_INDEX = 4;
    private static final int SOFT_IRQ_RAISE_INDEX = 5;
    private static final int SCHED_SWITCH_INDEX = 6;
    private static final int SCHED_PROCESS_FORK_INDEX = 7;
    private static final int SCHED_PROCESS_EXIT_INDEX = 8;
    private static final int SCHED_PROCESS_FREE_INDEX = 9;
    private static final int STATEDUMP_PROCESS_STATE_INDEX = 10;
    private static final int SCHED_WAKEUP_INDEX = 11;
    private static final int SCHED_PI_SETPRIO_INDEX = 12;

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    private final Map<String, Integer> fEventNames;
    private final IKernelAnalysisEventLayout fLayout;
    /* The pcpus actually running a vm. */
    private final Map<Integer, Boolean> fCpusInVM;
    private QemuKvmVmModel fModel;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Instantiate a new state provider plugin.
     *
     * @param experiment
     *            The experiment that will be analyzed.
     *
     * @param layout
     *            The event layout to use for this state provider. Usually
     *            depending on the tracer implementation.
     */
    public FusedVirtualMachineStateProvider(TmfExperiment experiment, IKernelAnalysisEventLayout layout) {
        super(experiment, "Virtual Machine State Provider"); //$NON-NLS-1$
        fLayout = layout;
        fEventNames = buildEventNames(layout);
        fModel = new QemuKvmVmModel(experiment);
        fCpusInVM = new HashMap<>();
    }

    // ------------------------------------------------------------------------
    // Event names management
    // ------------------------------------------------------------------------

    private static Map<String, Integer> buildEventNames(IKernelAnalysisEventLayout layout) {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();

        builder.put(layout.eventIrqHandlerEntry(), IRQ_HANDLER_ENTRY_INDEX);
        builder.put(layout.eventIrqHandlerExit(), IRQ_HANDLER_EXIT_INDEX);
        builder.put(layout.eventSoftIrqEntry(), SOFT_IRQ_ENTRY_INDEX);
        builder.put(layout.eventSoftIrqExit(), SOFT_IRQ_EXIT_INDEX);
        builder.put(layout.eventSoftIrqRaise(), SOFT_IRQ_RAISE_INDEX);
        builder.put(layout.eventSchedSwitch(), SCHED_SWITCH_INDEX);
        builder.put(layout.eventSchedPiSetprio(), SCHED_PI_SETPRIO_INDEX);
        builder.put(layout.eventSchedProcessFork(), SCHED_PROCESS_FORK_INDEX);
        builder.put(layout.eventSchedProcessExit(), SCHED_PROCESS_EXIT_INDEX);
        builder.put(layout.eventSchedProcessFree(), SCHED_PROCESS_FREE_INDEX);

        final String eventStatedumpProcessState = layout.eventStatedumpProcessState();
        if (eventStatedumpProcessState != null) {
            builder.put(eventStatedumpProcessState, STATEDUMP_PROCESS_STATE_INDEX);
        }

        for (String eventSchedWakeup : layout.eventsSchedWakeup()) {
            builder.put(eventSchedWakeup, SCHED_WAKEUP_INDEX);
        }

        return checkNotNull(builder.build());
    }

    // ------------------------------------------------------------------------
    // IStateChangeInput
    // ------------------------------------------------------------------------

    @Override
    public TmfExperiment getTrace() {
        ITmfTrace trace = super.getTrace();
        if (trace instanceof TmfExperiment) {
            return (TmfExperiment) trace;
        }
        throw new IllegalStateException("FusedVirtualMachineStateProvider: The associated trace should be an experiment"); //$NON-NLS-1$
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public void assignTargetStateSystem(ITmfStateSystemBuilder ssb) {
        /* We can only set up the locations once the state system is assigned */
        super.assignTargetStateSystem(ssb);
    }

    @Override
    public FusedVirtualMachineStateProvider getNewInstance() {
        return new FusedVirtualMachineStateProvider(this.getTrace(), fLayout);
    }

    @Override
    protected void eventHandle(@Nullable ITmfEvent event) {
        if (event == null) {
            return;
        }

        VirtualMachine host = fModel.getCurrentMachine(event);
        if (host == null) {
            /* We don't know where that event is coming from yet. */
            return;
        }

        String traceName = host.getTraceName();

        Integer cpu;
        Integer currentVCpu = -1;
        cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpu == null) {
            /* We couldn't find any CPU information, ignore this event */
            return;
        }

        /* Have the hypervisor models handle the event first */
        fModel.handleEvent(event);

        if (host.isGuest()) {
            /*
             * If the event is from a vm we have to find on which physical cpu
             * it is running.
             */
            HostThread hostThread = fModel.getHostThreadFromVm(host);
            if (hostThread == null) {
                return;
            }
            VirtualCPU vcpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
            Long physCpu = fModel.getPhysicalCpuFromVcpu(host, vcpu);
            if (physCpu == null) {
                return;
            }
            /* Replace the vcpu value by the physical one. */
            currentVCpu = cpu;
            cpu = physCpu.intValue();
        }

        Boolean inVM = fCpusInVM.get(cpu);
        if (inVM == null) {
            inVM = false;
            fCpusInVM.put(cpu, inVM);
        }

        final String eventName = event.getName();
        final long ts = event.getTimestamp().getValue();

        try {
            final ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());

            /* Shortcut for the "current CPU" attribute node */
            int currentCPUNode = ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), cpu.toString());

            /* Add in the state system the state of the cpu (in or out vm). */
            int quarkCondition = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
            ITmfStateValue valueCondition;
            if (inVM) {
                valueCondition = StateValues.CONDITION_IN_VM_VALUE;
                int quarkVCpu = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.VIRTUAL_CPU);
                ITmfStateValue valueVCpu = TmfStateValue.newValueInt(currentVCpu);
                ss.modifyAttribute(ts, valueVCpu, quarkVCpu);

                if (host.isGuest()) {
                    int quarkMachines = getNodeMachines(ss);
                    ss.getQuarkRelativeAndAdd(quarkMachines, traceName, cpu.toString());
                }
            } else {
                int quarkMachines = getNodeMachines(ss);
                ss.getQuarkRelativeAndAdd(quarkMachines, traceName, cpu.toString());

                valueCondition = StateValues.CONDITION_OUT_VM_VALUE;
            }
            ss.modifyAttribute(ts, valueCondition, quarkCondition);
            /*
             * Shortcut for the "current thread" attribute node. It requires
             * querying the current CPU's current thread.
             */
            int quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);

            ITmfStateValue value = ss.queryOngoingState(quark);
            int thread = value.isNull() ? -1 : value.unboxInt();

            final int currentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), String.valueOf(thread));

            /* Set the name of the machine running on the cpu */
            quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.MACHINE_NAME);
            value = TmfStateValue.newValueString(event.getTrace().getName());
            ss.modifyAttribute(ts, value, quark);

            /*
             * Feed event to the history system if it's known to cause a state
             * transition.
             */
            Integer idx = fEventNames.get(eventName);
            int intval = (idx == null ? -1 : idx.intValue());
            switch (intval) {

            case IRQ_HANDLER_ENTRY_INDEX: {
                Integer irqId = ((Long) event.getContent().getField(fLayout.fieldIrq()).getValue()).intValue();

                /*
                 * Mark this IRQ as active in the resource tree. The state value
                 * = the CPU on which this IRQ is sitting
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeIRQs(ss), irqId.toString());
                value = TmfStateValue.newValueInt(cpu.intValue());
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the running process to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_INTERRUPTED_VALUE;
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the CPU to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                value = StateValues.CPU_STATUS_IRQ_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case IRQ_HANDLER_EXIT_INDEX: {
                Integer irqId = ((Long) event.getContent().getField(fLayout.fieldIrq()).getValue()).intValue();

                /* Put this IRQ back to inactive in the resource tree */
                quark = ss.getQuarkRelativeAndAdd(getNodeIRQs(ss), irqId.toString());
                value = TmfStateValue.nullValue();
                ss.modifyAttribute(ts, value, quark);

                /* Set the previous process back to running */
                setProcessToRunning(ss, ts, currentThreadNode);

                /* Set the CPU status back to running or "idle" */
                cpuExitInterrupt(ss, ts, currentCPUNode, currentThreadNode);
            }
                break;

            case SOFT_IRQ_ENTRY_INDEX: {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /*
                 * Mark this SoftIRQ as active in the resource tree. The state
                 * value = the CPU on which this SoftIRQ is processed
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = TmfStateValue.newValueInt(cpu.intValue());
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the running process to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_INTERRUPTED_VALUE;
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the CPU to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                value = StateValues.CPU_STATUS_SOFTIRQ_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case SOFT_IRQ_EXIT_INDEX: {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /*
                 * Put this SoftIRQ back to inactive (= -1) in the resource tree
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = TmfStateValue.nullValue();
                ss.modifyAttribute(ts, value, quark);

                /* Set the previous process back to running */
                setProcessToRunning(ss, ts, currentThreadNode);

                /* Set the CPU status back to "busy" or "idle" */
                cpuExitInterrupt(ss, ts, currentCPUNode, currentThreadNode);
            }
                break;

            case SOFT_IRQ_RAISE_INDEX:
            /* Fields: int32 vec */
            {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /*
                 * Mark this SoftIRQ as *raised* in the resource tree. State
                 * value = -2
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = StateValues.SOFT_IRQ_RAISED_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case SCHED_SWITCH_INDEX: {

                ITmfEventField content = event.getContent();
                Integer prevTid = ((Long) content.getField(fLayout.fieldPrevTid()).getValue()).intValue();
                Long prevState = (Long) content.getField(fLayout.fieldPrevState()).getValue();
                String nextProcessName = (String) content.getField(fLayout.fieldNextComm()).getValue();
                Integer nextTid = ((Long) content.getField(fLayout.fieldNextTid()).getValue()).intValue();
                Integer nextPrio = ((Long) content.getField(fLayout.fieldNextPrio()).getValue()).intValue();

                Integer formerThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), prevTid.toString());
                Integer newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), nextTid.toString());

                /*
                 * Empirical observations and look into the linux code have
                 * shown that the TASK_STATE_MAX flag is used internally and
                 * |'ed with other states, most often the running state, so it
                 * is ignored from the prevState value.
                 */
                prevState = prevState & ~(LinuxValues.TASK_STATE_MAX);

                /* Set the status of the process that got scheduled out. */
                switch (prevState.intValue()) {
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

                quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);

                /* Set the status of the new scheduled process */
                setProcessToRunning(ss, ts, newCurrentThreadNode);

                /* Set the exec name of the new process */
                quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.EXEC_NAME);
                value = TmfStateValue.newValueString(nextProcessName);
                ss.modifyAttribute(ts, value, quark);

                /* Set the current prio for the new process */
                quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(nextPrio);
                ss.modifyAttribute(ts, value, quark);

                /* Make sure the PPID and system_call sub-attributes exist */
                ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);

                /* Set the current scheduled process on the relevant CPU */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
                value = TmfStateValue.newValueInt(nextTid);
                ss.modifyAttribute(ts, value, quark);

                /* Set the status of the CPU itself */
                if (nextTid > 0) {
                    /*
                     * Check if the entering process is in kernel or user mode
                     */
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                    if (ss.queryOngoingState(quark).isNull()) {
                        value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    } else {
                        value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                    }
                } else {
                    value = StateValues.CPU_STATUS_IDLE_VALUE;
                }
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case SCHED_PI_SETPRIO_INDEX: {
                ITmfEventField content = event.getContent();
                Integer tid = ((Long) content.getField(fLayout.fieldTid()).getValue()).intValue();
                Integer prio = ((Long) content.getField(fLayout.fieldNewPrio()).getValue()).intValue();

                Integer updateThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), tid.toString());

                /* Set the current prio for the new process */
                quark = ss.getQuarkRelativeAndAdd(updateThreadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(prio);
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case SCHED_PROCESS_FORK_INDEX: {
                ITmfEventField content = event.getContent();
                // String parentProcessName = (String)
                // event.getFieldValue("parent_comm");
                String childProcessName = (String) content.getField(fLayout.fieldChildComm()).getValue();
                // assert ( parentProcessName.equals(childProcessName) );

                Integer parentTid = ((Long) content.getField(fLayout.fieldParentTid()).getValue()).intValue();
                Integer childTid = ((Long) content.getField(fLayout.fieldChildTid()).getValue()).intValue();

                Integer parentTidNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), parentTid.toString());
                Integer childTidNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), childTid.toString());

                /* Assign the PPID to the new process */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.PPID);
                value = TmfStateValue.newValueInt(parentTid);
                ss.modifyAttribute(ts, value, quark);

                /* Set the new process' exec_name */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.EXEC_NAME);
                value = TmfStateValue.newValueString(childProcessName);
                ss.modifyAttribute(ts, value, quark);

                /* Set the new process' status */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                ss.modifyAttribute(ts, value, quark);

                /*
                 * Set the process' syscall name, to be the same as the parent's
                 */
                quark = ss.getQuarkRelativeAndAdd(parentTidNode, Attributes.SYSTEM_CALL);
                value = ss.queryOngoingState(quark);
                if (value.isNull()) {
                    /*
                     * Maybe we were missing info about the parent? At least we
                     * will set the child right. Let's suppose "sys_clone".
                     */
                    value = TmfStateValue.newValueString(fLayout.eventSyscallEntryPrefix() + IKernelAnalysisEventLayout.INITIAL_SYSCALL_NAME);
                }
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.SYSTEM_CALL);
                ss.modifyAttribute(ts, value, quark);
            }
                break;

            case SCHED_PROCESS_EXIT_INDEX:
                break;

            case SCHED_PROCESS_FREE_INDEX: {
                Integer tid = ((Long) event.getContent().getField(fLayout.fieldTid()).getValue()).intValue();
                /*
                 * Remove the process and all its sub-attributes from the
                 * current state
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), tid.toString());
                ss.removeAttribute(ts, quark);
            }
                break;

            case STATEDUMP_PROCESS_STATE_INDEX:
            /* LTTng-specific */
            {
                ITmfEventField content = event.getContent();
                int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
                int pid = ((Long) content.getField("pid").getValue()).intValue(); //$NON-NLS-1$
                int ppid = ((Long) content.getField("ppid").getValue()).intValue(); //$NON-NLS-1$
                int status = ((Long) content.getField("status").getValue()).intValue(); //$NON-NLS-1$
                String name = (String) content.getField("name").getValue(); //$NON-NLS-1$
                /*
                 * "mode" could be interesting too, but it doesn't seem to be
                 * populated with anything relevant for now.
                 */

                int curThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), String.valueOf(tid));

                /* Set the process' name */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.EXEC_NAME);
                if (ss.queryOngoingState(quark).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueString(name);
                    ss.modifyAttribute(ts, value, quark);
                }

                /* Set the process' PPID */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.PPID);
                if (ss.queryOngoingState(quark).isNull()) {
                    if (pid == tid) {
                        /* We have a process. Use the 'PPID' field. */
                        value = TmfStateValue.newValueInt(ppid);
                    } else {
                        /*
                         * We have a thread, use the 'PID' field for the parent.
                         */
                        value = TmfStateValue.newValueInt(pid);
                    }
                    ss.modifyAttribute(ts, value, quark);
                }

                /* Set the process' status */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.STATUS);
                if (ss.queryOngoingState(quark).isNull()) {
                    switch (status) {
                    case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT_CPU:
                        value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                        break;
                    case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT:
                        /*
                         * We have no information on what the process is waiting
                         * on (unlike a sched_switch for example), so we will
                         * use the WAIT_UNKNOWN state instead of the "normal"
                         * WAIT_BLOCKED state.
                         */
                        value = StateValues.PROCESS_STATUS_WAIT_UNKNOWN_VALUE;
                        break;
                    default:
                        value = StateValues.PROCESS_STATUS_UNKNOWN_VALUE;
                    }
                    ss.modifyAttribute(ts, value, quark);
                }
            }
                break;

            case SCHED_WAKEUP_INDEX: {
                final int tid = ((Long) event.getContent().getField(fLayout.fieldTid()).getValue()).intValue();
                final int prio = ((Long) event.getContent().getField(fLayout.fieldPrio()).getValue()).intValue();
                final int threadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), String.valueOf(tid));

                /*
                 * The process indicated in the event's payload is now ready to
                 * run. Assign it to the "wait for cpu" state, but only if it
                 * was not already running.
                 */
                quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.STATUS);
                int status = ss.queryOngoingState(quark).unboxInt();

                if (status != StateValues.PROCESS_STATUS_RUN_SYSCALL &&
                        status != StateValues.PROCESS_STATUS_RUN_USERMODE) {
                    value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                }

                /*
                 * When a user changes a threads prio (e.g. with
                 * pthread_setschedparam), it shows in ftrace with a
                 * sched_wakeup.
                 */
                quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(prio);
                ss.modifyAttribute(ts, value, quark);
            }
                break;
            default:
            /* Other event types not covered by the main switch */
            {
                if (eventName.startsWith(fLayout.eventSyscallEntryPrefix())
                        || eventName.startsWith(fLayout.eventCompatSyscallEntryPrefix())) {

                    /* Assign the new system call to the process */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
                    value = TmfStateValue.newValueString(eventName);
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the process in system call mode */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                    value = StateValues.PROCESS_STATUS_RUN_SYSCALL_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the CPU in system call (kernel) mode */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                } else if (eventName.startsWith(fLayout.eventSyscallExitPrefix())) {

                    /* Clear the current system call on the process */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
                    value = TmfStateValue.nullValue();
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the process' status back to user mode */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                    value = StateValues.PROCESS_STATUS_RUN_USERMODE_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the CPU's status back to user mode */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                } else if (eventName.equals(QemuKvmStrings.KVM_ENTRY)) {
                    /* We are entering a vm. */
                    fCpusInVM.replace(cpu, true);

                    /* Add the condition in_vm in the state system. */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
                    value = StateValues.CONDITION_IN_VM_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);

                    /* Get the host CPU doing the kvm_entry. */
                    VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
                    /* Saves the state. Will be restored after a kvm_exit. */
                    ITmfStateValue ongoingState = ss.queryOngoingState(quark);
                    if (ongoingState != null) {
                        hostCpu.setCurrentState(ongoingState);
                    }
                    /* Get the host thread to get the right virtual machine. */
                    HostThread ht = new HostThread(event.getTrace().getHostId(), thread);
                    VirtualMachine virtualMachine = fModel.getVmFromHostThread(ht);
                    if (virtualMachine == null) {
                        return;
                    }

                    VirtualCPU vcpu = fModel.getVirtualCpu(ht);
                    if (vcpu == null) {
                        return;
                    }

                    currentVCpu = vcpu.getCpuId().intValue();
                    /* Set the value of the vcpu that is going to run. */
                    int quarkVCpu = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.VIRTUAL_CPU);
                    ITmfStateValue valueVCpu = TmfStateValue.newValueInt(currentVCpu);
                    ss.modifyAttribute(ts, valueVCpu, quarkVCpu);

                    /*
                     * Set the name of the VM that will run just after the
                     * kvm_entry
                     */
                    int machineNameQuark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.MACHINE_NAME);
                    value = TmfStateValue.newValueString(virtualMachine.getTraceName());
                    ss.modifyAttribute(ts, value, machineNameQuark);

                    /*
                     * When the state of the vm and the host are the same the
                     * transition is not detected by the view so we add a false
                     * state that lasts 1 ns to make the transition visible.
                     * TODO: Find a better way to handle this problem.
                     */
                    /*
                     * Then the current state of the vm is restored.
                     */
                    if (hostCpu.getCurrentState() == vcpu.getCurrentState()) {
                        value = StateValues.CPU_STATUS_IN_VM_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                        value = vcpu.getCurrentState();
                        ss.modifyAttribute(ts + 1, value, quark);
                    } else {
                        value = vcpu.getCurrentState();
                        ss.modifyAttribute(ts, value, quark);
                    }

                    /* Save the current thread of the host that was running. */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
                    ongoingState = ss.queryOngoingState(quark);
                    if (ongoingState != null) {
                        hostCpu.setCurrentThread(ongoingState);
                    }
                    /* Restore the thread of the VM that was running. */
                    value = vcpu.getCurrentThread();
                    ss.modifyAttribute(ts, value, quark);

                } else if (eventName.equals(QemuKvmStrings.KVM_EXIT)) {
                    /* We are exiting a vm. */
                    fCpusInVM.replace(cpu, false);
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);

                    /* Get the host CPU doing the kvm_exit. */
                    VirtualCPU hostCpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
                    /* Get the host thread to get the right virtual machine. */
                    HostThread ht = getCurrentHostThread(event, ts);
                    if (ht == null) {
                        return;
                    }
                    VirtualCPU vcpu = fModel.getVCpuEnteringHypervisorMode(event, ht);
                    if (vcpu == null) {
                        return;
                    }

                    /* Save the state of the VCpu. */
                    ITmfStateValue ongoingState = ss.queryOngoingState(quark);
                    if (ongoingState != null) {
                        vcpu.setCurrentState(ongoingState);
                    }

                    /*
                     * When the state of the vm and the host are the same the
                     * transition is not detected by the view so we add a false
                     * state that lasts 1 ns to make the transition visible.
                     * TODO: Find a better way to handle this problem.
                     */
                    /* Then the current state of the host is restored. */
                    if (hostCpu.getCurrentState() == vcpu.getCurrentState()) {
                        value = StateValues.CPU_STATUS_IN_VM_VALUE;
                        ss.modifyAttribute(ts - 1, value, quark);
                        value = hostCpu.getCurrentState();
                        ss.modifyAttribute(ts, value, quark);
                    } else {
                        value = hostCpu.getCurrentState();
                        ss.modifyAttribute(ts, value, quark);
                    }

                    /* Save the current thread of the vm that was running. */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
                    ongoingState = ss.queryOngoingState(quark);
                    if (ongoingState != null) {
                        vcpu.setCurrentThread(ongoingState);
                    }

                    /* Restore the thread of the host that was running. */
                    value = hostCpu.getCurrentThread();
                    ss.modifyAttribute(ts, value, quark);

                    /* Add the condition out_vm in the state system. */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
                    value = StateValues.CONDITION_OUT_VM_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                }

            }
                break;
            } // End of big switch

        } catch (AttributeNotFoundException ae) {
            /*
             * This would indicate a problem with the logic of the manager here,
             * so it shouldn't happen.
             */
            ae.printStackTrace();

        } catch (TimeRangeException tre) {
            /*
             * This would happen if the events in the trace aren't ordered
             * chronologically, which should never be the case ...
             */
            System.err.println("TimeRangeExcpetion caught in the state system's event manager."); //$NON-NLS-1$
            System.err.println("Are the events in the trace correctly ordered?"); //$NON-NLS-1$
            tre.printStackTrace();

        } catch (StateValueTypeException sve) {
            /*
             * This would happen if we were trying to push/pop attributes not of
             * type integer. Which, once again, should never happen.
             */
            sve.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // Convenience methods for commonly-used attribute tree locations
    // ------------------------------------------------------------------------

    private static int getNodeCPUs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.CPUS);
    }

    private static int getNodeThreads(ITmfStateSystemBuilder ssb, String machineName) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.THREADS, machineName);
    }

    private static int getNodeIRQs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.RESOURCES, Attributes.IRQS);
    }

    private static int getNodeSoftIRQs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.RESOURCES, Attributes.SOFT_IRQS);
    }

    private static int getNodeMachines(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.MACHINES);
    }

    // ------------------------------------------------------------------------
    // Advanced state-setting methods
    // ------------------------------------------------------------------------

    /**
     * When we want to set a process back to a "running" state, first check its
     * current System_call attribute. If there is a system call active, we put
     * the process back in the syscall state. If not, we put it back in user
     * mode state.
     */
    private static void setProcessToRunning(ITmfStateSystemBuilder ssb, long ts, int currentThreadNode)
            throws AttributeNotFoundException, TimeRangeException,
            StateValueTypeException {
        int quark;
        ITmfStateValue value;

        quark = ssb.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
        if (ssb.queryOngoingState(quark).isNull()) {
            /* We were in user mode before the interruption */
            value = StateValues.PROCESS_STATUS_RUN_USERMODE_VALUE;
        } else {
            /* We were previously in kernel mode */
            value = StateValues.PROCESS_STATUS_RUN_SYSCALL_VALUE;
        }
        quark = ssb.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
        ssb.modifyAttribute(ts, value, quark);
    }

    /**
     * Similar logic as above, but to set the CPU's status when it's coming out
     * of an interruption.
     */
    private static void cpuExitInterrupt(ITmfStateSystemBuilder ssb, long ts,
            int currentCpuNode, int currentThreadNode)
                    throws StateValueTypeException, AttributeNotFoundException,
                    TimeRangeException {
        int quark;
        ITmfStateValue value;

        quark = ssb.getQuarkRelativeAndAdd(currentCpuNode, Attributes.CURRENT_THREAD);
        if (ssb.queryOngoingState(quark).unboxInt() > 0) {
            /* There was a process on the CPU */
            quark = ssb.getQuarkRelative(currentThreadNode, Attributes.SYSTEM_CALL);
            if (ssb.queryOngoingState(quark).isNull()) {
                /* That process was in user mode */
                value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
            } else {
                /* That process was in a system call */
                value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
            }
        } else {
            /* There was no real process scheduled, CPU was idle */
            value = StateValues.CPU_STATUS_IDLE_VALUE;
        }
        quark = ssb.getQuarkRelativeAndAdd(currentCpuNode, Attributes.STATUS);
        ssb.modifyAttribute(ts, value, quark);
    }

    private @Nullable HostThread getCurrentHostThread(ITmfEvent event, long ts) {
        /* Get the LTTng kernel analysis for the host */
        String hostId = event.getTrace().getHostId();
        KernelAnalysisModule module = TmfExperimentUtils.getAnalysisModuleOfClassForHost(getTrace(), hostId, KernelAnalysisModule.class);
        if (module == null) {
            return null;
        }

        /* Get the CPU the event is running on */
        Integer cpu;
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            /* We couldn't find any CPU information, ignore this event */
            return null;
        }
        cpu = (Integer) cpuObj;

        Integer currentTid = KernelThreadInformationProvider.getThreadOnCpu(module, cpu, ts);
        if (currentTid == null) {
            return null;
        }
        return new HostThread(hostId, currentTid);
    }

}