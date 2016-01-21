package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.analysis.os.linux.ui.views.controlflow.ControlFlowView;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineAnalysis;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview.FusedVMViewEntry.Type;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractStateSystemTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider2;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphSelectionListener;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphTimeListener;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.Machine;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphSelectionEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphViewer;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.Resolution;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * @author Cedric Biancheri
 *
 */
public class FusedVirtualMachineView extends AbstractStateSystemTimeGraphView {

    /** View ID. */
    public static final String ID = "org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview"; //$NON-NLS-1$

    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            Messages.FusedVMView_stateTypeName
    };

    // Timeout between updates in the build thread in ms
    private static final long BUILD_UPDATE_TIMEOUT = 500;

    /** Button to select a machine to highlight */
    private final Action fHighlightMachine = new Action(Messages.FusedVMView_ButtonMachineSelected, IAction.AS_CHECK_BOX) {
        @Override
        public void run() {
            FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
            Map<String, Machine> highlightedMachines = presentationProvider.getHighlightedMachines();
            Machine machine = highlightedMachines.get(presentationProvider.getSelectedMachine());
            if (machine == null) {
                setChecked(!isChecked());
                return;
            }
            machine.setHighlightedWithAllCpu(isChecked());
            fHighlightCPU.setChecked(isChecked());
            refresh();
        }
    };

    /** Button to select a CPU to highlight */
    private final Action fHighlightCPU = new Action(Messages.FusedVMView_ButtonCPUSelected, IAction.AS_CHECK_BOX) {
        @Override
        public void run() {
            FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
            Map<String, Machine> highlightedMachines = presentationProvider.getHighlightedMachines();
            Machine machine = highlightedMachines.get(presentationProvider.getSelectedMachine());
            if (machine == null) {
                setChecked(!isChecked());
                return;
            }
            machine.setHighlightedCpu(presentationProvider.getSelectedCpu(), isChecked());
            fHighlightMachine.setChecked(machine.isOneCpuHighlighted());
            refresh();
        }
    };

    /** Button to select a process to highlight */
    private final Action fHighlightProcess = new Action(Messages.FusedVMView_ButtonProcessSelected, IAction.AS_CHECK_BOX) {
        @Override
        public void run() {
            FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
            if (isChecked()) {
                presentationProvider.addHighlightedThread();
            } else {
                presentationProvider.removeHighlightedThread();
            }
            refresh();
        }
    };

    /** The beginning of the selected time */
    private long beginSelectedTime;

    /** The end of the selected time */
    private long endSelectedTime;

    /**
     * Listener that handles a change in the selected time in the FusedVM View
     */
    private final ITimeGraphTimeListener fTimeListenerFusedVMView = new ITimeGraphTimeListener() {

        @Override
        public void timeSelected(TimeGraphTimeEvent event) {
            setBeginSelectedTime(event.getBeginTime());
            setEndSelectedTime(event.getEndTime());
            long begin = getBeginSelectedTime();
            long end = getEndSelectedTime();
            if (begin == end) {
                FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
                Object o = presentationProvider.getSelectedFusedVMViewEntry();
                if (o == null) {
                    return;
                }
                if (!(o instanceof FusedVMViewEntry)) {
                    return;
                }
                FusedVMViewEntry entry = (FusedVMViewEntry) o;
                int cpuQuark = entry.getQuark();
                ITmfTrace trace = getTrace();
                if (trace == null) {
                    return;
                }
                final ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(trace, FusedVirtualMachineAnalysis.ID);
                if (ssq == null) {
                    return;
                }
                String machineName = null;
                try {
                    ITmfStateInterval interval;
                    int machineNameQuark = ssq.getQuarkRelative(cpuQuark, Attributes.MACHINE_NAME);
                    interval = ssq.querySingleState(begin, machineNameQuark);
                    ITmfStateValue value = interval.getStateValue();
                    machineName = value.unboxStr();
                    presentationProvider.setSelectedMachine(machineName);

                    int threadQuark = ssq.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                    interval = ssq.querySingleState(begin, threadQuark);
                    value = interval.getStateValue();
                    int threadID = value.unboxInt();

                    int execNameQuark = ssq.getQuarkAbsolute(Attributes.THREADS, machineName, Integer.toString(threadID), Attributes.EXEC_NAME);
                    interval = ssq.querySingleState(begin, execNameQuark);
                    value = interval.getStateValue();
                    String threadName = value.unboxStr();

                    presentationProvider.setSelectedThread(machineName, threadID, threadName);

                    int conditionQuark = ssq.getQuarkRelative(cpuQuark, Attributes.CONDITION);
                    interval = ssq.querySingleState(begin, conditionQuark);
                    value = interval.getStateValue();
                    int condition = value.unboxInt();
                    if (condition == StateValues.CONDITION_IN_VM) {
                        int machineVCpuQuark = ssq.getQuarkRelative(cpuQuark, Attributes.VIRTUAL_CPU);
                        interval = ssq.querySingleState(begin, machineVCpuQuark);
                        value = interval.getStateValue();
                        int vcpu = value.unboxInt();
                        presentationProvider.setSelectedCpu(vcpu);
                    } else {
                        presentationProvider.setSelectedCpu(Integer.parseInt(ssq.getAttributeName(cpuQuark)));
                    }

                } catch (AttributeNotFoundException e) {
                    // Activator.getDefault().logError("Error in
                    // FusedVMViewPresentationProvider", e); //$NON-NLS-1$
                } catch (StateSystemDisposedException e) {
                    /* Ignored */
                }

                updateButtonsSelection();
                updateToolTipTexts();

            } else {
                printInformations();
            }
        }
    };

    /** Listener that handles a click on an entry in the FusedVM View */
    private final ITimeGraphSelectionListener fSelListenerFusedVMView = new ITimeGraphSelectionListener() {

        @Override
        public void selectionChanged(TimeGraphSelectionEvent event) {
            ITimeGraphEntry entry = event.getSelection();
            if (entry instanceof FusedVMViewEntry) {
                FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
                presentationProvider.setSelectedFusedVMViewEntry(entry);
            }

        }
    };

    /** Listener that handles a click on an entry in the Control Flow View */
    private final ISelectionListener fSelListenerControlFlowView = new ISelectionListener() {
        @Override
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
            if (selection instanceof IStructuredSelection) {
                Object element = ((IStructuredSelection) selection).getFirstElement();
                if (element instanceof ControlFlowEntry) {
                    FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
                    ControlFlowEntry entry = (ControlFlowEntry) element;
                    String machineName = entry.getTrace().getName();
                    String threadName = entry.getName();
                    int threadID = entry.getThreadId();
                    presentationProvider.setSelectedControlFlowViewEntry(element);
                    presentationProvider.setSelectedMachine(machineName);
                    // TODO: Find a way to access to the id of the cpu running
                    // the process
                    presentationProvider.setSelectedThread(machineName, threadID, threadName);

                    updateButtonsSelection();
                    updateToolTipTexts();
                }
            }
        }
    };

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Default constructor
     */
    public FusedVirtualMachineView() {
        super(ID, new FusedVMViewPresentationProvider());
        setFilterColumns(FILTER_COLUMN_NAMES);
        setFilterLabelProvider(new FusedVMFilterLabelProvider());
        registerListener();

    }

    private static class FusedVMFilterLabelProvider extends TreeLabelProvider {
        @Override
        public String getColumnText(Object element, int columnIndex) {
            FusedVMViewEntry entry = (FusedVMViewEntry) element;
            if (columnIndex == 0) {
                return entry.getName();
            }
            return ""; //$NON-NLS-1$
        }

    }

    // ------------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------------

    @Override
    protected String getNextText() {
        return Messages.FusedVMView_nextResourceActionNameText;
    }

    @Override
    protected String getNextTooltip() {
        return Messages.FusedVMView_nextResourceActionToolTipText;
    }

    @Override
    protected String getPrevText() {
        return Messages.FusedVMView_previousResourceActionNameText;
    }

    @Override
    protected String getPrevTooltip() {
        return Messages.FusedVMView_previousResourceActionToolTipText;
    }

    @Override
    protected void rebuild() {
        setStartTime(Long.MAX_VALUE);
        setEndTime(Long.MIN_VALUE);
        refresh();
        ITmfTrace viewTrace = getTrace();
        if (viewTrace == null) {
            return;
        }
        synchronized (fBuildThreadMap) {
            BuildThread buildThread = new BuildThread(viewTrace, viewTrace, getName());
            fBuildThreadMap.put(viewTrace, buildThread);
            buildThread.start();
        }
    }

    @Override
    protected void buildEventList(ITmfTrace trace, ITmfTrace parentTrace, final IProgressMonitor monitor) {
        if (monitor.isCanceled()) {
            return;
        }
        if (!(parentTrace instanceof VirtualMachineExperiment)) {
            return;
        }

        final ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(parentTrace, FusedVirtualMachineAnalysis.ID);
        if (ssq == null) {
            return;
        }
        Comparator<ITimeGraphEntry> comparator = new Comparator<ITimeGraphEntry>() {
            @Override
            public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {
                return ((FusedVMViewEntry) o1).compareTo(o2);
            }
        };

        /* All traces are highlighted by default. */
        FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
        /* Remove highlighted machines from other analysis. */
        presentationProvider.destroyHightlightedMachines();
        for (ITmfTrace t : ((VirtualMachineExperiment) parentTrace).getTraces()) {
            Machine m = new Machine(t.getName());
            presentationProvider.getHighlightedMachines().put(t.getName(), m);
        }

        /* Highlight all vcpus of all guests by default */
        List<Integer> machinesQuarks = ssq.getQuarks(Attributes.MACHINES, "*"); //$NON-NLS-1$
        for (Integer machineQuark : machinesQuarks) {
            String machineName = ssq.getAttributeName(machineQuark);
            List<Integer> vCpuquarks = ssq.getQuarks(Attributes.MACHINES, machineName, "*"); //$NON-NLS-1$
            Machine machine = presentationProvider.getHighlightedMachines().get(machineName);
            if (machine != null) {
                for (Integer vcpu : vCpuquarks) {
                    machine.addCpu(ssq.getAttributeName(vcpu));
                }
            }

        }

        Map<Integer, FusedVMViewEntry> entryMap = new HashMap<>();
        TimeGraphEntry traceEntry = null;

        long startTime = ssq.getStartTime();
        long start = startTime;
        setStartTime(Math.min(getStartTime(), startTime));
        boolean complete = false;
        while (!complete) {
            if (monitor.isCanceled()) {
                return;
            }
            complete = ssq.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
            if (ssq.isCancelled()) {
                return;
            }
            long end = ssq.getCurrentEndTime();
            if (start == end && !complete) { // when complete execute one last
                                             // time regardless of end time
                continue;
            }
            long endTime = end + 1;
            setEndTime(Math.max(getEndTime(), endTime));

            if (traceEntry == null) {
                traceEntry = new FusedVMViewEntry(trace, trace.getName(), startTime, endTime, 0);
                traceEntry.sortChildren(comparator);
                List<TimeGraphEntry> entryList = Collections.singletonList(traceEntry);
                addToEntryList(parentTrace, ssq, entryList);
            } else {
                traceEntry.updateEndTime(endTime);
            }

            List<Integer> cpuQuarks = ssq.getQuarks(Attributes.CPUS, "*"); //$NON-NLS-1$
            for (Integer cpuQuark : cpuQuarks) {
                int cpu = Integer.parseInt(ssq.getAttributeName(cpuQuark));
                FusedVMViewEntry entry = entryMap.get(cpuQuark);
                if (entry == null) {
                    entry = new FusedVMViewEntry(cpuQuark, trace, startTime, endTime, Type.CPU, cpu);
                    entryMap.put(cpuQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> irqQuarks = ssq.getQuarks(Attributes.RESOURCES, Attributes.IRQS, "*"); //$NON-NLS-1$
            for (Integer irqQuark : irqQuarks) {
                int irq = Integer.parseInt(ssq.getAttributeName(irqQuark));
                FusedVMViewEntry entry = entryMap.get(irqQuark);
                if (entry == null) {
                    entry = new FusedVMViewEntry(irqQuark, trace, startTime, endTime, Type.IRQ, irq);
                    entryMap.put(irqQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> softIrqQuarks = ssq.getQuarks(Attributes.RESOURCES, Attributes.SOFT_IRQS, "*"); //$NON-NLS-1$
            for (Integer softIrqQuark : softIrqQuarks) {
                int softIrq = Integer.parseInt(ssq.getAttributeName(softIrqQuark));
                FusedVMViewEntry entry = entryMap.get(softIrqQuark);
                if (entry == null) {
                    entry = new FusedVMViewEntry(softIrqQuark, trace, startTime, endTime, Type.SOFT_IRQ, softIrq);
                    entryMap.put(softIrqQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }

            if (parentTrace.equals(getTrace())) {
                refresh();
            }
            final List<? extends ITimeGraphEntry> traceEntryChildren = traceEntry.getChildren();
            final long resolution = Math.max(1, (endTime - ssq.getStartTime()) / getDisplayWidth());
            final long qStart = start;
            final long qEnd = end;
            queryFullStates(ssq, qStart, qEnd, resolution, monitor, new IQueryHandler() {
                @Override
                public void handle(List<List<ITmfStateInterval>> fullStates, List<ITmfStateInterval> prevFullState) {
                    for (ITimeGraphEntry child : traceEntryChildren) {
                        if (monitor.isCanceled()) {
                            return;
                        }
                        if (child instanceof TimeGraphEntry) {
                            TimeGraphEntry entry = (TimeGraphEntry) child;
                            List<ITimeEvent> eventList = getEventList(entry, ssq, fullStates, prevFullState, monitor);
                            if (eventList != null) {
                                for (ITimeEvent event : eventList) {
                                    entry.addEvent(event);
                                }
                            }
                        }
                    }
                }
            });

            start = end;
        }
    }

    @Override
    protected @Nullable List<ITimeEvent> getEventList(@NonNull TimeGraphEntry entry, ITmfStateSystem ssq,
            @NonNull List<List<ITmfStateInterval>> fullStates, @Nullable List<ITmfStateInterval> prevFullState, @NonNull IProgressMonitor monitor) {
        FusedVMViewEntry fusedVMViewEntry = (FusedVMViewEntry) entry;
        List<ITimeEvent> eventList = null;
        int quark = fusedVMViewEntry.getQuark();

        if (fusedVMViewEntry.getType().equals(Type.CPU)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark, Attributes.STATUS);
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen if
                 * the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;
                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        } else if (fusedVMViewEntry.getType().equals(Type.IRQ) || fusedVMViewEntry.getType().equals(Type.SOFT_IRQ)) {
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null ? null : prevFullState.get(quark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            boolean lastIsNull = lastInterval == null ? false : lastInterval.getStateValue().isNull();
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                ITmfStateInterval irqInterval = fullState.get(quark);
                long time = irqInterval.getStartTime();
                long duration = irqInterval.getEndTime() - time + 1;
                if (time == lastStartTime) {
                    continue;
                }
                if (!irqInterval.getStateValue().isNull()) {
                    int cpu = irqInterval.getStateValue().unboxInt();
                    eventList.add(new TimeEvent(entry, time, duration, cpu));
                    lastIsNull = false;
                } else {
                    if (lastEndTime != time && lastIsNull) {
                        /*
                         * This is a special case where we want to show
                         * IRQ_ACTIVE state but we don't know the CPU (it is
                         * between two null samples)
                         */
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime, -1));
                    }
                    eventList.add(new NullTimeEvent(entry, time, duration));
                    lastIsNull = true;
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }

        return eventList;
    }

    @Override
    protected void fillLocalToolBar(IToolBarManager manager) {
        super.fillLocalToolBar(manager);
        TimeGraphViewer timeGraphViewer = getTimeGraphViewer();
        IAction selectMachineAction = timeGraphViewer.getSelectMachineAction();
        selectMachineAction.setText(Messages.FusedVMView_selectMachineText);
        selectMachineAction.setToolTipText(Messages.FusedVMView_selectMachineText);
        manager.add(selectMachineAction);
        manager.add(new Separator());

        manager.add(fHighlightMachine);
        manager.add(fHighlightCPU);
        manager.add(fHighlightProcess);

    }

    @Override
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        getTimeGraphViewer().addTimeListener(fTimeListenerFusedVMView);
        getTimeGraphViewer().addSelectionListener(fSelListenerFusedVMView);
    }

    /**
     * Gets the beginning of the selected time
     *
     * @return the beginning of the selected time
     */
    public long getBeginSelectedTime() {
        return beginSelectedTime;
    }

    /**
     * Sets the beginning of the selected time
     *
     * @param begin
     *            the beginning of the selected time
     */
    public void setBeginSelectedTime(long begin) {
        beginSelectedTime = begin;
    }

    /**
     * Gets the end of the selected time
     *
     * @return the end of the selected time
     */
    public long getEndSelectedTime() {
        return endSelectedTime;
    }

    /**
     * Sets the end of the selected time
     *
     * @param end
     *            the end of the selected time
     */
    public void setEndSelectedTime(long end) {
        endSelectedTime = end;
    }

    /**
     * Getter to the presentation provider
     *
     * @return the FusedVMViewProvider
     */
    public FusedVMViewPresentationProvider getFusedVMViewPresentationProvider() {
        ITimeGraphPresentationProvider2 pp = getPresentationProvider();
        if (!(pp instanceof FusedVMViewPresentationProvider)) {
            return null;
        }
        return (FusedVMViewPresentationProvider) pp;
    }

    private void printInformations() {
        long begin = getBeginSelectedTime();
        long end = getEndSelectedTime();

        System.out.println("Begin time: " + Utils.formatTime(begin, TimeFormat.CALENDAR, Resolution.NANOSEC)); //$NON-NLS-1$
        System.out.println("End time: " + Utils.formatTime(end, TimeFormat.CALENDAR, Resolution.NANOSEC)); //$NON-NLS-1$
        System.out.println();

    }

    /**
     * Registers the listener that handles the click on a Control Flow View
     * entry
     */
    private void registerListener() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        IWorkbench wb = PlatformUI.getWorkbench();
        if (wb == null) {
            return;
        }
        IWorkbenchWindow wbw = wb.getActiveWorkbenchWindow();
        if (wbw == null) {
            return;
        }
        final IWorkbenchPage activePage = wbw.getActivePage();
        if (activePage == null) {
            return;
        }

        /* Add the listener to the control flow view */
        IViewPart view = activePage.findView(ControlFlowView.ID);
        if (view != null) {
            view.getSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(fSelListenerControlFlowView);
        }
    }

    /**
     * Updates the tooltip text of the buttons so it corresponds to the machine,
     * cpu and process selected
     */
    private void updateToolTipTexts() {
        FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
        fHighlightMachine.setToolTipText(presentationProvider.getSelectedMachine());
        fHighlightCPU.setToolTipText(Integer.toString((presentationProvider.getSelectedCpu())));
        fHighlightProcess.setToolTipText(Messages.FusedVMView_ButtonProcessSelected + ": " + //$NON-NLS-1$
                presentationProvider.getSelectedThreadName() + "\n" + //$NON-NLS-1$
                Messages.FusedVMView_ButtonHoverProcessSelectedTID + ": " + //$NON-NLS-1$
                Integer.toString(presentationProvider.getSelectedThreadID()));
    }

    /**
     * Sets the checked state of the buttons
     */
    private void updateButtonsSelection() {
        FusedVMViewPresentationProvider presentationProvider = getFusedVMViewPresentationProvider();
        Map<String, Machine> highlightedMachines = presentationProvider.getHighlightedMachines();
        Machine machine = highlightedMachines.get(presentationProvider.getSelectedMachine());
        if (machine == null) {
            return;
        }

        fHighlightMachine.setChecked(machine.isHighlighted());
        fHighlightCPU.setChecked(machine.isCpuHighlighted(presentationProvider.getSelectedCpu()));
        fHighlightProcess.setChecked(presentationProvider.isThreadSelected(machine.getMachineName(), presentationProvider.getSelectedThreadID()));
    }
}
