package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineAnalysis;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.Messages;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.Activator;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview.FusedVMViewEntry.Type;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITmfTimeGraphDrawingHelper;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.Resolution;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;

/**
 * @author Cedric Biancheri
 *
 */
public class FusedVMViewPresentationProvider extends TimeGraphPresentationProvider {

    private long fLastThreadId = -1;
    private Color fColorWhite;
    private Color fColorGray;
    private Integer fAverageCharWidth;
    private volatile Object selectedFusedVMViewEntry;
    private volatile Object selectedControlFLowViewEntry;
    private String selectedMachine;
    private Thread selectedThread;
    private int selectedCpu;
    private Set<Thread> highlightedTreads = new HashSet<>();
    private Map<String, Machine> highlightedMachines = new HashMap<>();
    private static int ponderation = 3;

    private class Thread {
        private String machineName;
        private int threadID;
        private String threadName;

        public Thread(String m, int t) {
            machineName = m;
            threadID = t;
            threadName = null;
        }

        public Thread(String m, int t, String n) {
            machineName = m;
            threadID = t;
            threadName = n;
        }

        public String getMachineName() {
            return machineName;
        }

        public int getThreadID() {
            return threadID;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Thread) {
                Thread t = (Thread) o;
                return (t.getMachineName().equals(machineName)) && (t.getThreadID() == threadID);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + machineName.hashCode();
            hash = hash * 31 + threadID;
            return hash;
        }

        /**
         * @return the threadName
         */
        public String getThreadName() {
            return threadName;
        }
    }

    private enum State {
        IDLE(new RGB(200, 200, 200)),
        IDLE_DIM(new RGB((200 + ponderation * 255) / (ponderation + 1), (200 + ponderation * 255) / (ponderation + 1), (200 + ponderation * 255) / (ponderation + 1))),
        USERMODE(new RGB(0, 200, 0)),
        USERMODE_DIM(new RGB((0 + ponderation * 255) / (ponderation + 1), (200 + ponderation * 255) / (ponderation + 1), (0 + ponderation * 255) / (ponderation + 1))),
        SYSCALL(new RGB(0, 0, 200)),
        SYSCALL_DIM(new RGB((0 + ponderation * 255) / (ponderation + 1), (0 + ponderation * 255) / (ponderation + 1), (200 + ponderation * 255) / (ponderation + 1))),
        IRQ(new RGB(200, 0, 100)),
        IRQ_DIM(new RGB((200 + ponderation * 255) / (ponderation + 1), (0 + ponderation * 255) / (ponderation + 1), (100 + ponderation * 255) / (ponderation + 1))),
        SOFT_IRQ(new RGB(200, 150, 100)),
        SOFT_IRQ_DIM(new RGB((200 + ponderation * 255) / (ponderation + 1), (150 + ponderation * 255) / (ponderation + 1), (100 + ponderation * 255) / (ponderation + 1))),
        IRQ_ACTIVE(new RGB(200, 0, 100)),
        SOFT_IRQ_RAISED(new RGB(200, 200, 0)),
        SOFT_IRQ_ACTIVE(new RGB(200, 150, 100)),
        IN_VM(new RGB(200, 0, 200)),
        IN_VM_DIM(new RGB((200 + ponderation * 255) / (ponderation + 1), 0, (200 + ponderation * 255) / (ponderation + 1)));

        public final RGB rgb;

        private State(RGB rgb) {
            this.rgb = rgb;
        }

        public static State highLightState(State state) {
            int n = state.ordinal();
            return State.values()[n - 1];
        }

    }

    /**
     * Default constructor
     */
    public FusedVMViewPresentationProvider() {
        super();
    }

    private static State[] getStateValues() {
        return State.values();
    }

    private State getEventState(TimeEvent event) {
        if (event.hasValue()) {
            FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();
            int value = event.getValue();

            if (entry.getType() == Type.CPU) {
                State state = null;
                if (value == StateValues.CPU_STATUS_IDLE) {
                    state = State.IDLE_DIM;
                } else if (value == StateValues.CPU_STATUS_RUN_USERMODE || value == StateValues.CPU_STATUS_SWITCH_TO_USERMODE) {
                    state = State.USERMODE_DIM;
                } else if (value == StateValues.CPU_STATUS_RUN_SYSCALL || value == StateValues.CPU_STATUS_SWITCH_TO_SYSCALL) {
                    state = State.SYSCALL_DIM;
                } else if (value == StateValues.CPU_STATUS_IRQ) {
                    state = State.IRQ_DIM;
                } else if (value == StateValues.CPU_STATUS_SOFTIRQ) {
                    state = State.SOFT_IRQ_DIM;
                } else if (value == StateValues.CPU_STATUS_IN_VM) {
                    state = State.IN_VM_DIM;
                }
                if (state != null) {
                    /* Add here your condition to highlight */
                    if (isMachineHighlighted(event) && isCpuHighlighted(event) || isProcessHighlighted(event)) {
                        return State.highLightState(state);
                    }
                    return state;
                }

            } else if (entry.getType() == Type.IRQ) {
                return State.IRQ_ACTIVE;
            } else if (entry.getType() == Type.SOFT_IRQ) {
                if (value == StateValues.SOFT_IRQ_RAISED) {
                    return State.SOFT_IRQ_RAISED;
                }
                return State.SOFT_IRQ_ACTIVE;
            }
        }
        return null;
    }

    @Override
    public int getStateTableIndex(ITimeEvent event) {
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.ordinal();
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public StateItem[] getStateTable() {
        State[] states = getStateValues();
        StateItem[] stateTable = new StateItem[states.length];
        for (int i = 0; i < stateTable.length; i++) {
            State state = states[i];
            stateTable[i] = new StateItem(state.rgb, state.toString());
        }
        return stateTable;
    }

    @Override
    public String getEventName(ITimeEvent event) {
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.toString();
        }
        if (event instanceof NullTimeEvent) {
            return null;
        }
        return Messages.FusedVMView_multipleStates;
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {

        Map<String, String> retMap = new LinkedHashMap<>();
        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {

            TimeEvent tcEvent = (TimeEvent) event;
            FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();

            if (tcEvent.hasValue()) {
                ITmfTrace exp = entry.getTrace();
                ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(exp, FusedVirtualMachineAnalysis.ID);
                if (ss == null) {
                    return retMap;
                }

                /* Here we get the name of the host or the vm. */
                int cpuQuark = entry.getQuark();
                String machineName = null;
                try {
                    ITmfStateInterval interval;
                    int machineNameQuark = ss.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
                    interval = ss.querySingleState(hoverTime, machineNameQuark);
                    ITmfStateValue value = interval.getStateValue();
                    machineName = value.unboxStr();
                    retMap.put(Messages.FusedVMView_attributeVirtualMachine, machineName);

                    int conditionQuark = ss.getQuarkRelative(cpuQuark, Attributes.CONDITION);
                    interval = ss.querySingleState(hoverTime, conditionQuark);
                    value = interval.getStateValue();
                    int condition = value.unboxInt();
                    if (condition == StateValues.CONDITION_IN_VM) {
                        int machineVCpuQuark = ss.getQuarkRelative(cpuQuark, Attributes.VIRTUAL_CPU);
                        interval = ss.querySingleState(hoverTime, machineVCpuQuark);
                        value = interval.getStateValue();
                        int vcpu = value.unboxInt();
                        retMap.put(Messages.FusedVMView_attributeVirtualCpu, String.valueOf(vcpu));
                    }

                } catch (AttributeNotFoundException e) {
                    // Activator.getDefault().logError("Error in
                    // FusedVMViewPresentationProvider", e); //$NON-NLS-1$
                } catch (StateSystemDisposedException e) {
                    /* Ignored */
                }

                // Check for IRQ or Soft_IRQ type
                if (entry.getType().equals(Type.IRQ) || entry.getType().equals(Type.SOFT_IRQ)) {

                    // Get CPU of IRQ or SoftIRQ and provide it for the tooltip
                    // display
                    int cpu = tcEvent.getValue();
                    if (cpu >= 0) {
                        retMap.put(Messages.FusedVMView_attributeCpuName, String.valueOf(cpu));
                    }
                }

                // Check for type CPU
                else if (entry.getType().equals(Type.CPU)) {
                    int status = tcEvent.getValue();

                    if (status == StateValues.CPU_STATUS_IRQ) {
                        // In IRQ state get the IRQ that caused the interruption
                        int cpu = entry.getId();

                        try {
                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
                            List<Integer> irqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.IRQS, "*"); //$NON-NLS-1$

                            for (int irqQuark : irqQuarks) {
                                if (fullState.get(irqQuark).getStateValue().unboxInt() == cpu) {
                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), irqQuark);
                                    if (!value.getStateValue().isNull()) {
                                        int irq = Integer.parseInt(ss.getAttributeName(irqQuark));
                                        retMap.put(Messages.FusedVMView_attributeIrqName, String.valueOf(irq));
                                    }
                                    break;
                                }
                            }
                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    } else if (status == StateValues.CPU_STATUS_SOFTIRQ) {
                        // In SOFT_IRQ state get the SOFT_IRQ that caused the
                        // interruption
                        int cpu = entry.getId();

                        try {
                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
                            List<Integer> softIrqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.SOFT_IRQS, "*"); //$NON-NLS-1$

                            for (int softIrqQuark : softIrqQuarks) {
                                if (fullState.get(softIrqQuark).getStateValue().unboxInt() == cpu) {
                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), softIrqQuark);
                                    if (!value.getStateValue().isNull()) {
                                        int softIrq = Integer.parseInt(ss.getAttributeName(softIrqQuark));
                                        retMap.put(Messages.FusedVMView_attributeSoftIrqName, String.valueOf(softIrq));
                                    }
                                    break;
                                }
                            }
                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    } else if (status == StateValues.CPU_STATUS_RUN_USERMODE || status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                        // In running state get the current tid

                        try {
                            retMap.put(Messages.FusedVMView_attributeHoverTime, Utils.formatTime(hoverTime, TimeFormat.CALENDAR, Resolution.NANOSEC));
                            cpuQuark = entry.getQuark();
                            int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                            ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                            if (!interval.getStateValue().isNull()) {
                                ITmfStateValue value = interval.getStateValue();
                                int currentThreadId = value.unboxInt();
                                retMap.put(Messages.FusedVMView_attributeTidName, Integer.toString(currentThreadId));
                                int execNameQuark = ss.getQuarkAbsolute(Attributes.THREADS, machineName, Integer.toString(currentThreadId), Attributes.EXEC_NAME);
                                interval = ss.querySingleState(hoverTime, execNameQuark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    retMap.put(Messages.FusedVMView_attributeProcessName, value.unboxStr());
                                }
                                if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                                    int syscallQuark = ss.getQuarkAbsolute(Attributes.THREADS, machineName, Integer.toString(currentThreadId), Attributes.SYSTEM_CALL);
                                    interval = ss.querySingleState(hoverTime, syscallQuark);
                                    if (!interval.getStateValue().isNull()) {
                                        value = interval.getStateValue();
                                        retMap.put(Messages.FusedVMView_attributeSyscallName, value.unboxStr());
                                    }
                                }
                            }

                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    }

                }
            }
        }

        return retMap;
    }

    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
        if (fColorGray == null) {
            fColorGray = gc.getDevice().getSystemColor(SWT.COLOR_GRAY);
        }
        if (fColorWhite == null) {
            fColorWhite = gc.getDevice().getSystemColor(SWT.COLOR_WHITE);
        }
        if (fAverageCharWidth == null) {
            fAverageCharWidth = gc.getFontMetrics().getAverageCharWidth();
        }

        ITmfTimeGraphDrawingHelper drawingHelper = getDrawingHelper();
        if (bounds.width <= fAverageCharWidth) {
            return;
        }

        if (!(event instanceof TimeEvent)) {
            return;
        }
        TimeEvent tcEvent = (TimeEvent) event;
        if (!tcEvent.hasValue()) {
            return;
        }

        FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();
        if (!entry.getType().equals(Type.CPU)) {
            return;
        }

        int status = tcEvent.getValue();
        if (status != StateValues.CPU_STATUS_RUN_USERMODE && status != StateValues.CPU_STATUS_RUN_SYSCALL) {
            return;
        }

        ITmfTrace exp = entry.getTrace();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(exp, FusedVirtualMachineAnalysis.ID);
        if (ss == null) {
            return;
        }
        long time = event.getTime();

        /* Here we get the name of the host or the vm. */
        int cpuQuark = entry.getQuark();
        String machineName = null;
        try {
            ITmfStateInterval interval;
            int machineNameQuark = ss.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
            interval = ss.querySingleState(time, machineNameQuark);
            ITmfStateValue value = interval.getStateValue();
            machineName = value.unboxStr();
        } catch (AttributeNotFoundException e) {
            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }

        try {
            while (time < event.getTime() + event.getDuration()) {
                cpuQuark = entry.getQuark();
                int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                ITmfStateInterval tidInterval = ss.querySingleState(time, currentThreadQuark);
                long startTime = Math.max(tidInterval.getStartTime(), event.getTime());
                int x = Math.max(drawingHelper.getXForTime(startTime), bounds.x);
                if (x >= bounds.x + bounds.width) {
                    break;
                }
                if (!tidInterval.getStateValue().isNull()) {
                    ITmfStateValue value = tidInterval.getStateValue();
                    int currentThreadId = value.unboxInt();
                    long endTime = Math.min(tidInterval.getEndTime() + 1, event.getTime() + event.getDuration());
                    int xForEndTime = drawingHelper.getXForTime(endTime);
                    if (xForEndTime > bounds.x) {
                        int width = Math.min(xForEndTime, bounds.x + bounds.width) - x - 1;
                        if (width > 0) {
                            String attribute = null;
                            int beginIndex = 0;
                            if (status == StateValues.CPU_STATUS_RUN_USERMODE && currentThreadId != fLastThreadId) {
                                attribute = Attributes.EXEC_NAME;
                            } else if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                                attribute = Attributes.SYSTEM_CALL;
                                /*
                                 * Remove the "sys_" or "syscall_entry_" or
                                 * similar from what we draw in the rectangle.
                                 * This depends on the trace's event layout.
                                 */
                                ITmfTrace trace = entry.getTrace();
                                if (trace instanceof IKernelTrace) {
                                    IKernelAnalysisEventLayout layout = ((IKernelTrace) trace).getKernelEventLayout();
                                    beginIndex = layout.eventSyscallEntryPrefix().length();
                                } else if (trace instanceof VirtualMachineExperiment) {
                                    IKernelAnalysisEventLayout layout = ((IKernelTrace) trace.getChild(0)).getKernelEventLayout();
                                    beginIndex = layout.eventSyscallEntryPrefix().length();
                                }
                            }
                            if (attribute != null) {
                                int quark = ss.getQuarkAbsolute(Attributes.THREADS, machineName, Integer.toString(currentThreadId), attribute);
                                ITmfStateInterval interval = ss.querySingleState(time, quark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    gc.setForeground(fColorWhite);
                                    int drawn = Utils.drawText(gc, value.unboxStr().substring(beginIndex), x + 1, bounds.y - 2, width, true, true);
                                    if (drawn > 0) {
                                        fLastThreadId = currentThreadId;
                                    }
                                }
                            }
                            if (xForEndTime < bounds.x + bounds.width) {
                                gc.setForeground(fColorGray);
                                gc.drawLine(xForEndTime, bounds.y + 1, xForEndTime, bounds.y + bounds.height - 2);
                            }
                        }
                    }
                }
                // make sure next time is at least at the next pixel
                time = Math.max(tidInterval.getEndTime() + 1, drawingHelper.getTimeAtX(x + 1));
            }
        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
    }

    @Override
    public void postDrawEntry(ITimeGraphEntry entry, Rectangle bounds, GC gc) {
        fLastThreadId = -1;
    }

    //
    // Functions used to decide if the area is highlighted or not.
    //

    /**
     * Says for a specific event if the related machine is highlighted
     */
    private boolean isMachineHighlighted(ITimeEvent event) {
        Map<String, Machine> map = getHighlightedMachines();
        FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();
        ITmfTrace trace = entry.getTrace();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(trace, FusedVirtualMachineAnalysis.ID);
        int cpuQuark = entry.getQuark();
        long time = event.getTime();
        if (ss == null) {
            return false;
        }

        String machineName = null;
        try {
            ITmfStateInterval interval;
            int machineNameQuark = ss.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
            interval = ss.querySingleState(time, machineNameQuark);
            ITmfStateValue value = interval.getStateValue();
            machineName = value.unboxStr();
        } catch (AttributeNotFoundException e) {
            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }

        Machine machine = map.get(machineName);
        if (machine == null) {
            return false;
        }
        Boolean res = machine.isHighlighted();
        return res;
    }

    /**
     * Says for a specific event if the related cpu is highlighted
     */
    private boolean isCpuHighlighted(ITimeEvent event) {
        Map<String, Machine> map = getHighlightedMachines();
        FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();
        ITmfTrace trace = entry.getTrace();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(trace, FusedVirtualMachineAnalysis.ID);
        int cpuQuark = entry.getQuark();
        long time = event.getTime();
        if (ss == null) {
            return false;
        }

        try {
            ITmfStateInterval interval;
            int machineNameQuark = ss.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
            interval = ss.querySingleState(time, machineNameQuark);
            ITmfStateValue value = interval.getStateValue();
            String machineName = value.unboxStr();

            int conditionQuark = ss.getQuarkRelative(cpuQuark, Attributes.CONDITION);
            interval = ss.querySingleState(time, conditionQuark);
            if (!interval.getStateValue().isNull()) {
                ITmfStateValue valueInVM = interval.getStateValue();
                int cpu;
                int inVM = valueInVM.unboxInt();
                if (inVM == StateValues.CONDITION_IN_VM) {
                    int machineVCpuQuark = ss.getQuarkRelative(cpuQuark, Attributes.VIRTUAL_CPU);
                    interval = ss.querySingleState(time, machineVCpuQuark);
                    value = interval.getStateValue();
                    cpu = value.unboxInt();
                } else {
                    cpu = Integer.parseInt(ss.getAttributeName(cpuQuark));
                }
                Machine machine = map.get(machineName);
                if (machine != null) {
                    return machine.isCpuHighlighted(Integer.toString(cpu));
                }
            }
        } catch (AttributeNotFoundException e) {
            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
        return false;
    }

    /**
     * Says for a specific event if the related process is highlighted
     */
    private boolean isProcessHighlighted(ITimeEvent event) {
        if (highlightedTreads.isEmpty()) {
            return false;
        }

        FusedVMViewEntry entry = (FusedVMViewEntry) event.getEntry();
        ITmfTrace trace = entry.getTrace();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(trace, FusedVirtualMachineAnalysis.ID);
        int cpuQuark = entry.getQuark();
        long time = event.getTime();
        if (ss == null) {
            return false;
        }
        try {
            ITmfStateInterval interval;
            int machineNameQuark = ss.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
            int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
            interval = ss.querySingleState(time, machineNameQuark);
            ITmfStateValue value = interval.getStateValue();
            String machineName = value.unboxStr();

            interval = ss.querySingleState(time, currentThreadQuark);
            value = interval.getStateValue();
            int currentThreadID = value.unboxInt();

            return highlightedTreads.contains(new Thread(machineName, currentThreadID));

        } catch (AttributeNotFoundException e) {
            Activator.getDefault().logError("Error in FusedVMViewPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }

        return false;
    }

    //
    // Getters, setter, and some short useful methods
    //

    /**
     * Sets the selected entry in FusedVM View
     *
     * @param o
     *            the FusedVMView entry selected
     */
    public void setSelectedFusedVMViewEntry(Object o) {
        selectedFusedVMViewEntry = o;
    }

    /**
     * Gets the selected entry in FusedVM View
     *
     * @return the selectedFusedVMViewEntry
     */
    public Object getSelectedFusedVMViewEntry() {
        return selectedFusedVMViewEntry;
    }

    /**
     * Sets the selected entry in Control Flow View
     *
     * @param o
     *            the ControlFLowView entry selected
     */
    public void setSelectedControlFlowViewEntry(Object o) {
        selectedControlFLowViewEntry = o;
    }

    /**
     * Gets the selected entry in Control Flow View
     *
     * @return the selectedControlFLowViewEntry
     */
    public Object getSelectedControlFlowViewEntry() {
        return selectedControlFLowViewEntry;
    }

    /**
     * Gets the selected machine
     *
     * @return the selectedMachine
     */
    public String getSelectedMachine() {
        return selectedMachine;
    }

    /**
     * Sets the selected machine
     *
     * @param machine
     *            the selectedMachine to set
     */
    public void setSelectedMachine(String machine) {
        selectedMachine = machine;
    }

    /**
     * Gets the selected cpu
     *
     * @return the selectedCpu
     */
    public int getSelectedCpu() {
        return selectedCpu;
    }

    /**
     * Sets the selected cpu
     *
     * @param cpu
     *            the selectedCpu to set
     */
    public void setSelectedCpu(int cpu) {
        selectedCpu = cpu;
    }

    /**
     * Gets the selected thread ID
     *
     * @return the selectedThreadID
     */
    public int getSelectedThreadID() {
        return selectedThread.getThreadID();
    }

    /**
     * Gets the selected thread name
     *
     * @return the selectedThread name;
     */
    public String getSelectedThreadName() {
        return selectedThread.getThreadName();
    }

    /**
     * @param machineName
     *            the machine name
     * @param threadID
     *            the tid of the thread
     * @param threadName
     *            the name of the thread
     *
     */
    public void setSelectedThread(String machineName, int threadID, String threadName) {
        selectedThread = new Thread(machineName, threadID, threadName);
    }

    /**
     * Gets the highlighted threads
     *
     * @return the highlightedTreads
     */
    public Set<Thread> getHighlightedTreads() {
        return highlightedTreads;
    }

    /**
     * @param machineName
     *            the machine name
     * @param tid
     *            the tid
     * @return true if the thread is contained in the list of highlighted
     *         threads
     */
    public boolean isThreadSelected(String machineName, int tid) {
        return highlightedTreads.contains(new Thread(machineName, tid));
    }

    /**
     * Adds the selected thread to the list of highlighted threads
     */
    public void addHighlightedThread() {
        highlightedTreads.add(selectedThread);
    }

    /**
     * Removes the selected thread of the list of highlighted threads
     */
    public void removeHighlightedThread() {
        highlightedTreads.remove(selectedThread);
    }

    public Map<String, Machine> getHighlightedMachines() {
        return highlightedMachines;
    }

    public void destroyHightlightedMachines() {
        highlightedMachines = new HashMap<>();
    }

}
