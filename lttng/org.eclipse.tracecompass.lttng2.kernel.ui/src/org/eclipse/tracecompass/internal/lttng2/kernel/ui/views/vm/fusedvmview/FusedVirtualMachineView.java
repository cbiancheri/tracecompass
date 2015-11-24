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
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.FusedVirtualMachineAnalysis;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview.FusedVMViewEntry.Type;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractStateSystemTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;

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

}
