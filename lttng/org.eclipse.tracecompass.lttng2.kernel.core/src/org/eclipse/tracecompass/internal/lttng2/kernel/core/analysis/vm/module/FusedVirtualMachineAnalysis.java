package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Messages;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisRequirement;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

/**
 * @author Cedric Biancheri
 *
 */
public class FusedVirtualMachineAnalysis extends TmfStateSystemAnalysisModule {

    /**
     * The file name of the History Tree
     */
    public static final String HISTORY_TREE_FILE_NAME = "stateHistory.ht"; //$NON-NLS-1$

    /** The ID of this analysis module */
    public static final String ID = "org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineAnalysis"; //$NON-NLS-1$

    /*
     * TODO: Decide which events should be mandatory for the analysis, once the
     * appropriate error messages and session setup are in place.
     */
    // private static final ImmutableSet<String> REQUIRED_EVENTS =
    // ImmutableSet.of();
    //
    // private static final ImmutableSet<String> OPTIONAL_EVENTS =
    // ImmutableSet.of(
    // FIXME These cannot be declared statically anymore, they depend on
    // the OriginTracer of the kernel trace.
    // LttngStrings.EXIT_SYSCALL,
    // LttngStrings.IRQ_HANDLER_ENTRY,
    // LttngStrings.IRQ_HANDLER_EXIT,
    // LttngStrings.SOFTIRQ_ENTRY,
    // LttngStrings.SOFTIRQ_EXIT,
    // LttngStrings.SOFTIRQ_RAISE,
    // LttngStrings.SCHED_PROCESS_FORK,
    // LttngStrings.SCHED_PROCESS_EXIT,
    // LttngStrings.SCHED_PROCESS_FREE,
    // LttngStrings.SCHED_SWITCH,
    // LttngStrings.STATEDUMP_PROCESS_STATE,
    // LttngStrings.SCHED_WAKEUP,
    // LttngStrings.SCHED_WAKEUP_NEW,
    //
    // /* FIXME Add the prefix for syscalls */
    // LttngStrings.SYSCALL_PREFIX
    // );

    /** The requirements as an immutable set */
    private static final Set<TmfAnalysisRequirement> REQUIREMENTS;

    static {
        // /* initialize the requirement: domain and events */
        // TmfAnalysisRequirement domainReq = new
        // TmfAnalysisRequirement(SessionConfigStrings.CONFIG_ELEMENT_DOMAIN);
        // domainReq.addValue(SessionConfigStrings.CONFIG_DOMAIN_TYPE_KERNEL,
        // ValuePriorityLevel.MANDATORY);
        //
        // TmfAnalysisRequirement eventReq = new
        // TmfAnalysisRequirement(SessionConfigStrings.CONFIG_ELEMENT_EVENT,
        // REQUIRED_EVENTS, ValuePriorityLevel.MANDATORY);
        // eventReq.addValues(OPTIONAL_EVENTS, ValuePriorityLevel.OPTIONAL);
        //
        // REQUIREMENTS = checkNotNull(ImmutableSet.of(domainReq, eventReq));
        REQUIREMENTS = checkNotNull(Collections.EMPTY_SET);
    }

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        ITmfTrace trace = checkNotNull(getTrace());
        IKernelAnalysisEventLayout layout;

        if (trace instanceof IKernelTrace) {
            layout = ((IKernelTrace) trace).getKernelEventLayout();
        } else if (trace.getChild(0) instanceof IKernelTrace) {
            layout = ((IKernelTrace) trace.getChild(0)).getKernelEventLayout();
        } else {
            /* Fall-back to the base LttngEventLayout */
            layout = IKernelAnalysisEventLayout.DEFAULT_LAYOUT;
        }
        if (!(trace instanceof TmfExperiment)) {
            throw new IllegalStateException();
        }

        return new FusedVirtualMachineStateProvider((TmfExperiment) trace, layout);
    }

    @Override
    @NonNull
    protected String getSsFileName() {
        return HISTORY_TREE_FILE_NAME;
    }

    @Override
    protected String getFullHelpText() {
        return NonNullUtils.nullToEmptyString(Messages.LttngKernelAnalysisModule_Help);
    }

    @Override
    public Iterable<TmfAnalysisRequirement> getAnalysisRequirements() {
        return REQUIREMENTS;
    }
}
