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
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.KernelEventHandler;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.ProcessExitHandler;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.handlers.SoftIrqRaiseHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.IrqEntryHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.IrqExitHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.KvmEntryHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.KvmExitHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.PiSetprioHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.ProcessForkContainerHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.ProcessFreeHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SchedSwitchHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SchedWakeupHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SoftIrqEntryHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SoftIrqExitHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.StateDumpContainerHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SysEntryHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.handlers.SysExitHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.lxc.LxcModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.qemukvm.QemuKvmStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.qemukvm.QemuKvmVmModel;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.ImmutableMap;

/**
 * State provider for the Fused Virtual Machine analysis. It is based on the
 * version 9 of the kernel state provider.
 *
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
    /* We try to match with the version of the KernelStateProvider */
    private static final int VERSION = 11;

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    private final Map<String, KernelEventHandler> fEventNames;
    private final IKernelAnalysisEventLayout fLayout;
    private final KernelEventHandler fSysEntryHandler;
    private final KernelEventHandler fSysExitHandler;
    private final KernelEventHandler fKvmEntryHandler;
    private final KernelEventHandler fKvmExitHandler;
    /* The pcpus actually running a vm. */
    private final Map<Integer, Boolean> fCpusInVM;
    private QemuKvmVmModel fModel;
    private LxcModel fContainerModel;
    private int currentThreadNode; // quark to current thread node

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
//        fContainerModel = new LxcModel(experiment);
        fContainerModel = new LxcModel();
        fCpusInVM = new HashMap<>();

        fSysEntryHandler = new SysEntryHandler(fLayout, this);
        fSysExitHandler = new SysExitHandler(fLayout, this);
        fKvmEntryHandler = new KvmEntryHandler(fLayout, this);
        fKvmExitHandler = new KvmExitHandler(fLayout, this);

    }

    // ------------------------------------------------------------------------
    // Event names management
    // ------------------------------------------------------------------------

    private Map<String, KernelEventHandler> buildEventNames(IKernelAnalysisEventLayout layout) {
        ImmutableMap.Builder<String, KernelEventHandler> builder = ImmutableMap.builder();

        builder.put(layout.eventIrqHandlerEntry(), new IrqEntryHandler(layout, this));
        builder.put(layout.eventIrqHandlerExit(), new IrqExitHandler(layout, this));
        builder.put(layout.eventSoftIrqEntry(), new SoftIrqEntryHandler(layout, this));
        builder.put(layout.eventSoftIrqExit(), new SoftIrqExitHandler(layout, this));
        builder.put(layout.eventSoftIrqRaise(), new SoftIrqRaiseHandler(layout));
        builder.put(layout.eventSchedSwitch(), new SchedSwitchHandler(layout, this));
        builder.put(layout.eventSchedPiSetprio(), new PiSetprioHandler(layout, this));
        builder.put(layout.eventSchedProcessFork(), new ProcessForkContainerHandler(layout, this));
        builder.put(layout.eventSchedProcessExit(), new ProcessExitHandler(layout));
        builder.put(layout.eventSchedProcessFree(), new ProcessFreeHandler(layout, this));

        final String eventStatedumpProcessState = layout.eventStatedumpProcessState();
        if (eventStatedumpProcessState != null) {
            builder.put(eventStatedumpProcessState, new StateDumpContainerHandler(layout));
        }

        for (String eventSchedWakeup : layout.eventsSchedWakeup()) {
            builder.put(eventSchedWakeup, new SchedWakeupHandler(layout));
        }
        return NonNullUtils.checkNotNull(builder.build());
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

        Integer currentVCpu = -1;
        @SuppressWarnings("null")
        Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpu == null) {
            /* We couldn't find any CPU information, ignore this event */
            return;
        }

        VirtualMachine host = getCurrentMachine(event);

        String traceName = event.getTrace().getName();

        /*
         * Have the hypervisor models handle the event first.
         */
        fModel.handleEvent(event);


        // VirtualMachine container = getCurrentContainer(event);

        /*
         * Continue even if host is unknown if the event is required for
         * container analysis
         */
        if (host == null && !fContainerModel.getRequiredEvents().contains(event.getName())) {
            return;
        }

        if (host != null) {
            /* Associate the cpu to its machine */
            VirtualCPU.addVirtualCPU(host, cpu.longValue());
            if (host.isGuest()) {
                /*
                 * If the event is from a vm we have to find on which physical
                 * cpu it is running.
                 */
                currentVCpu = cpu;
                cpu = getPhysicalCPU(host, cpu);
            }
        }


        Boolean inVM = false;
        if (cpu != null) {
            inVM = fCpusInVM.get(cpu);
            if (inVM == null) {
                inVM = false;
                fCpusInVM.put(cpu, inVM);
            }
        }

        final String eventName = event.getName();
        final long ts = event.getTimestamp().getValue();

        try {
            final ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());

            if (cpu != null) {
                /* Shortcut for the "current CPU" attribute node */
                int currentCPUNode = ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), cpu.toString());

                /*
                 * Add in the state system the state of the cpu (in or out vm).
                 */
                int quarkCondition = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CONDITION);
                ITmfStateValue valueCondition = StateValues.CONDITION_UNKNOWN_VALUE;
                ITmfStateValue machineState = StateValues.MACHINE_UNKNOWN_VALUE;
                int quarkMachines = getNodeMachines(ss);
                int machineNameQuark = ss.getQuarkRelativeAndAdd(quarkMachines, traceName);
                if (inVM) {
                    valueCondition = StateValues.CONDITION_IN_VM_VALUE;
                    int quarkVCpu = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.VIRTUAL_CPU);
                    ITmfStateValue valueVCpu = TmfStateValue.newValueInt(currentVCpu);
                    ss.modifyAttribute(ts, valueVCpu, quarkVCpu);

                    /*
                     * This part is used to remember how many cpus a machine has
                     */
                    if (host != null && host.isGuest()) {
                        ss.getQuarkRelativeAndAdd(machineNameQuark, Attributes.CPUS, currentVCpu.toString());
                        machineState = StateValues.MACHINE_GUEST_VALUE;
                    }
                } else {
                    /*
                     * We still need to check here if we are a guest because the
                     * guest's trace can be longer than the host's and we might
                     * be in a vm even if inVM == false
                     */
                    if (host != null && host.isGuest()) {
                        ss.getQuarkRelativeAndAdd(machineNameQuark, Attributes.CPUS, currentVCpu.toString());
                        machineState = StateValues.MACHINE_GUEST_VALUE;
                    } else {
                        ss.getQuarkRelativeAndAdd(quarkMachines, traceName, Attributes.CPUS, cpu.toString());
                        machineState = StateValues.MACHINE_HOST_VALUE;
                    }

                    valueCondition = StateValues.CONDITION_OUT_VM_VALUE;
                }
                /* Add the role of the machine in the state system */
                if (ss.queryOngoingState(machineNameQuark).isNull()) {
                    ss.modifyAttribute(getStartTime(), machineState, machineNameQuark);
                }
                ss.modifyAttribute(ts, valueCondition, quarkCondition);

                // if (container != null) {
                // int quarkMachines = getNodeMachines(ss);
                // ss.getQuarkRelativeAndAdd(quarkMachines, traceName,
                // String.valueOf(container.getVmUid()), cpu.toString());
                // }
                /*
                 * Shortcut for the "current thread" attribute node. It requires
                 * querying the current CPU's current thread.
                 */
                int quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);

                ITmfStateValue value = ss.queryOngoingState(quark);
                int thread = value.isNull() ? -1 : value.unboxInt();

                currentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss, traceName), String.valueOf(thread));

                /* Set the name of the machine running on the cpu */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.MACHINE_NAME);
                value = TmfStateValue.newValueString(event.getTrace().getName());
                ss.modifyAttribute(ts, value, quark);
            }
            /*
             * Feed event to the history system if it's known to cause a state
             * transition.
             */
            KernelEventHandler handler = fEventNames.get(eventName);
            if (handler == null) {
                if (isSyscallExit(eventName)) {
                    handler = fSysExitHandler;
                } else if (isSyscallEntry(eventName)) {
                    handler = fSysEntryHandler;
                } else if (isKvmEntry(eventName)) {
                    handler = fKvmEntryHandler;
                } else if (isKvmExit(eventName)) {
                    handler = fKvmExitHandler;
                }
            }
            if (handler != null) {
                handler.handleEvent(ss, event);
            }

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

    public static int getNodeMachines(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.MACHINES);
    }

    public static int getCurrentCPUNode(Integer cpuNumber, ITmfStateSystemBuilder ss) {
        return ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), cpuNumber.toString());
    }

    public static int getCurrentThreadNode(Integer cpuNumber, ITmfStateSystemBuilder ss) throws AttributeNotFoundException {
        /*
         * Shortcut for the "current thread" attribute node. It requires
         * querying the current CPU's current thread.
         */
        int quark = ss.getQuarkRelativeAndAdd(getCurrentCPUNode(cpuNumber, ss), Attributes.CURRENT_THREAD);
        ITmfStateValue value = ss.queryOngoingState(quark);
        int thread = value.isNull() ? -1 : value.unboxInt();
        quark = ss.getQuarkRelativeAndAdd(getCurrentCPUNode(cpuNumber, ss), Attributes.MACHINE_NAME);
        value = ss.queryOngoingState(quark);
        String machineName = value.unboxStr();
        return ss.getQuarkRelativeAndAdd(getNodeThreads(ss, machineName), String.valueOf(thread));
    }

//    public @Nullable HostThread getCurrentHostThread(ITmfEvent event, long ts) {
//        /* Get the LTTng kernel analysis for the host */
//        String hostId = event.getTrace().getHostId();
//        @SuppressWarnings("null")
//        KernelAnalysisModule module = TmfExperimentUtils.getAnalysisModuleOfClassForHost(getTrace(), hostId, KernelAnalysisModule.class);
//        if (module == null) {
//            return null;
//        }
//
//        /* Get the CPU the event is running on */
//        @SuppressWarnings("null")
//        Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
//        if (cpu == null) {
//            /* We couldn't find any CPU information, ignore this event */
//            return null;
//        }
//        Integer currentTid = KernelThreadInformationProvider.getThreadOnCpu(module, cpu, ts);
//        if (currentTid == null) {
//            return null;
//        }
//        return new HostThread(hostId, currentTid);
//    }

    private boolean isSyscallEntry(String eventName) {
        return (eventName.startsWith(fLayout.eventSyscallEntryPrefix())
                || eventName.startsWith(fLayout.eventCompatSyscallEntryPrefix()));
    }

    private boolean isSyscallExit(String eventName) {
        return (eventName.startsWith(fLayout.eventSyscallExitPrefix()) ||
                eventName.startsWith(fLayout.eventCompatSyscallExitPrefix()));
    }

    public int getCurrentThreadNode() {
        return currentThreadNode;
    }

    public @Nullable Integer getPhysicalCPU(VirtualMachine host, Integer cpu) {
        // HostThread hostThread = fModel.getHostThreadFromVm(host);
        // if (hostThread == null) {
        // return null;
        // }
        VirtualCPU vcpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
        Long physCpu = fModel.getPhysicalCpuFromVcpu(host, vcpu);
        if (physCpu == null) {
            return null;
        }
        /* Replace the vcpu value by the physical one. */
        return physCpu.intValue();
    }

    public @Nullable VirtualMachine getCurrentMachine(ITmfEvent event) {
        return fModel.getCurrentMachine(event);
    }

    public @Nullable VirtualMachine getCurrentContainer(ITmfEvent event) {
        return fContainerModel.getCurrentMachine(event);
    }

    public void replaceValueCpusInVM(Integer cpu, boolean value) {
        fCpusInVM.replace(cpu, value);
    }

    public @Nullable VirtualMachine getVmFromHostThread(HostThread ht) {
        return fModel.getVmFromHostThread(ht);
    }

    public @Nullable VirtualCPU getVirtualCpu(HostThread ht) {
        return fModel.getVirtualCpu(ht);
    }

    public @Nullable VirtualCPU getVCpuEnteringHypervisorMode(ITmfEvent event, HostThread ht) {
        return fModel.getVCpuEnteringHypervisorMode(event, ht);
    }

    private static boolean isKvmEntry(String eventName) {
        return eventName.equals(QemuKvmStrings.KVM_ENTRY);
    }

    private static boolean isKvmExit(String eventName) {
        return eventName.equals(QemuKvmStrings.KVM_EXIT);
    }

    /**
     * Return the known machines
     *
     * @return The known machines
     */
    public Map<String, VirtualMachine> getKnownMachines() {
        return fModel.getKnownMachines();
    }

}