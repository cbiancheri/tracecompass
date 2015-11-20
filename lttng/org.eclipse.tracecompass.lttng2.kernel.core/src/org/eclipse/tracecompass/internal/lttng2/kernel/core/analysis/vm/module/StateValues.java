package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;

/**
 * State values that are used in the kernel event handler. It's much better to
 * use integer values whenever possible, since those take much less space in the
 * history file.
 *
 * @author Alexandre Montplaisir
 */
@SuppressWarnings("javadoc")
public interface StateValues {

    /* CPU Status */
    int CPU_STATUS_IDLE = 0;
    int CPU_STATUS_RUN_USERMODE = 1;
    int CPU_STATUS_RUN_SYSCALL = 2;
    int CPU_STATUS_IRQ = 3;
    int CPU_STATUS_SOFTIRQ = 4;
    int CPU_STATUS_IN_VM = 5;

    ITmfStateValue CPU_STATUS_IDLE_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IDLE);
    ITmfStateValue CPU_STATUS_RUN_USERMODE_VALUE = TmfStateValue.newValueInt(CPU_STATUS_RUN_USERMODE);
    ITmfStateValue CPU_STATUS_RUN_SYSCALL_VALUE = TmfStateValue.newValueInt(CPU_STATUS_RUN_SYSCALL);
    ITmfStateValue CPU_STATUS_IRQ_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IRQ);
    ITmfStateValue CPU_STATUS_SOFTIRQ_VALUE = TmfStateValue.newValueInt(CPU_STATUS_SOFTIRQ);
    ITmfStateValue CPU_STATUS_IN_VM_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IN_VM);

    /* CPU condition*/
    int CONDITION_IN_VM = 0;
    int CONDITION_OUT_VM = 1;

    ITmfStateValue CONDITION_IN_VM_VALUE = TmfStateValue.newValueInt(CONDITION_IN_VM);
    ITmfStateValue CONDITION_OUT_VM_VALUE = TmfStateValue.newValueInt(CONDITION_OUT_VM);

    /* Process status */
    int PROCESS_STATUS_UNKNOWN = 0;
    int PROCESS_STATUS_WAIT_BLOCKED = 1;
    int PROCESS_STATUS_RUN_USERMODE = 2;
    int PROCESS_STATUS_RUN_SYSCALL = 3;
    int PROCESS_STATUS_INTERRUPTED = 4;
    int PROCESS_STATUS_WAIT_FOR_CPU = 5;
    /**
     * @since 1.0
     */
    int PROCESS_STATUS_WAIT_UNKNOWN = 6;

    ITmfStateValue PROCESS_STATUS_UNKNOWN_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_UNKNOWN);
    /**
     * @since 1.0
     */
    ITmfStateValue PROCESS_STATUS_WAIT_UNKNOWN_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_UNKNOWN);
    ITmfStateValue PROCESS_STATUS_WAIT_BLOCKED_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_BLOCKED);
    ITmfStateValue PROCESS_STATUS_RUN_USERMODE_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_RUN_USERMODE);
    ITmfStateValue PROCESS_STATUS_RUN_SYSCALL_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_RUN_SYSCALL);
    ITmfStateValue PROCESS_STATUS_INTERRUPTED_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_INTERRUPTED);
    ITmfStateValue PROCESS_STATUS_WAIT_FOR_CPU_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_FOR_CPU);

    /* SoftIRQ-specific stuff. -1: null/disabled, >= 0: running on that CPU */
    int SOFT_IRQ_RAISED = -2;

    ITmfStateValue SOFT_IRQ_RAISED_VALUE = TmfStateValue.newValueInt(SOFT_IRQ_RAISED);
}
