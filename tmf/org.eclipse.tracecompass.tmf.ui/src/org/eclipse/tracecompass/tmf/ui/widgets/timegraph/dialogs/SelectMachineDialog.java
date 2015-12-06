package org.eclipse.tracecompass.tmf.ui.widgets.timegraph.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.tracecompass.internal.tmf.ui.Messages;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfNavigatorContentProvider;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfNavigatorLabelProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.Machine;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.Processor;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

/**
 * @author Cedric Biancheri
 * @since 2.0
 *
 */
public class SelectMachineDialog extends TitleAreaDialog {
    private final ITimeGraphPresentationProvider provider;
    private CheckboxTreeViewer fCheckboxTreeViewer;
    private TmfNavigatorContentProvider fContentProvider;
    private TmfNavigatorLabelProvider fLabelProvider;
    // private final LocalResourceManager fResourceManager = new
    // LocalResourceManager(JFaceResources.getResources());

    /**
     * Open the select machines window
     *
     * @param parent
     *            The parent shell
     * @param provider
     *            The presentation provider
     */
    public static void open(Shell parent, ITimeGraphPresentationProvider provider) {
        (new SelectMachineDialog(parent, provider)).open();
    }

    /**
     * Standard constructor
     *
     * @param parent
     *            The parent shell
     * @param provider
     *            The presentation provider
     */
    public SelectMachineDialog(Shell parent, ITimeGraphPresentationProvider provider) {
        super(parent);
        this.provider = provider;
        this.setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        if (!(provider instanceof TimeGraphPresentationProvider)) {
            return null;
        }
        createMachinesGroup(parent);

        setTitle(Messages.TmfSelectMachine_SELECT_MACHINE);
        setDialogHelpAvailable(false);
        setHelpAvailable(false);

        return parent;
    }

    private void createMachinesGroup(Composite composite) {

        TimeGraphPresentationProvider timeGraphPresentationProvider = (TimeGraphPresentationProvider) provider;
        Map<String, Machine> machines = timeGraphPresentationProvider.getHighlightedMachines();

        new FilteredTree(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER, new PatternFilter(), true) {
            @Override
            protected TreeViewer doCreateTreeViewer(Composite aparent, int style) {
                return SelectMachineDialog.this.doCreateTreeViewer(aparent, machines);
            }
        };
    }

    private TreeViewer doCreateTreeViewer(Composite parent, Map<String, Machine> machines) {
        fCheckboxTreeViewer = new CheckboxTreeViewer(parent, SWT.BORDER);

        fContentProvider = new TmfNavigatorContentProvider() {

            @Override
            public Object getParent(Object element) {
                if (element instanceof Machine) {
                    return null;
                } else if (element instanceof Processor) {
                    return ((Processor) element).getMachine();
                }
                return null;
            }

            @Override
            public Object[] getElements(Object inputElement) {
                return getChildren(inputElement);
            }

            @Override
            public synchronized Object[] getChildren(Object parentElement) {
                if (parentElement instanceof List) {
                    return ((List<?>) parentElement).toArray();
                } else if (parentElement instanceof Machine) {
                    Machine m = (Machine) parentElement;
                    return m.getCpus().toArray();
                }
                return null;
            }

            @Override
            public boolean hasChildren(Object element) {
                Object[] children = getChildren(element);
                return children != null && children.length > 0;
            }

        };
        fCheckboxTreeViewer.setContentProvider(fContentProvider);
        fLabelProvider = new TmfNavigatorLabelProvider() {
            @Override
            public String getText(Object arg0) {
                return arg0.toString();
            }
        };
        fCheckboxTreeViewer.setLabelProvider(fLabelProvider);
        fCheckboxTreeViewer.setSorter(new ViewerSorter());

        final Tree tree = fCheckboxTreeViewer.getTree();
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        tree.setLayoutData(gd);
        tree.setHeaderVisible(true);

        final TreeViewerColumn column = new TreeViewerColumn(fCheckboxTreeViewer, SWT.NONE);
        column.getColumn().setText(Messages.TmfTimeGraphViewer_SelectMachineActionNameText);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof String) {
                    return (String) element;
                }
                return fLabelProvider.getText(element);
            }

            @Override
            public Image getImage(Object element) {
                return fLabelProvider.getImage(element);
            }
        });

        // Populate the list with the machines' names
        List<Machine> listMachines = new ArrayList<>(machines.values());
        fCheckboxTreeViewer.setInput(listMachines);
        column.getColumn().pack();

        fCheckboxTreeViewer.addCheckStateListener(new ICheckStateListener() {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                Object element = event.getElement();
                if (element instanceof Machine) {
                    Machine m = (Machine) element;
                    m.setHighlighted(event.getChecked());
                    if (!event.getChecked()) {
                        fCheckboxTreeViewer.setGrayed(element, false);
                    }
                    Boolean isExpanded = fCheckboxTreeViewer.getExpandedState(element);
                    fCheckboxTreeViewer.expandToLevel(element, AbstractTreeViewer.ALL_LEVELS);
                    fCheckboxTreeViewer.setSubtreeChecked(element, event.getChecked());
                    if (!isExpanded) {
                        fCheckboxTreeViewer.collapseToLevel(element, AbstractTreeViewer.ALL_LEVELS);
                    }
                    for (Processor cpu : m.getCpus()) {
                        cpu.setHighlighted(event.getChecked());
                    }
                } else if (element instanceof Processor) {
                    Boolean checked = event.getChecked();
                    ((Processor) element).setHighlighted(checked);
                    Object ancestor = fContentProvider.getParent(element);
                    if (ancestor instanceof Machine) {
                        Machine m = (Machine) ancestor;
                        fCheckboxTreeViewer.setGrayed(m, m.isGrayed());
                        fCheckboxTreeViewer.setChecked(m, m.isOneCpuHighlighted());
                        m.setHighlighted(m.isOneCpuHighlighted());
                    }
                }
            }
        });

        fCheckboxTreeViewer.expandAll();
        for (Machine m : listMachines) {
            fCheckboxTreeViewer.setChecked(m, m.isHighlighted());
            Set<Processor> cpus = m.getCpus();
            for (Processor cpu : cpus) {
                fCheckboxTreeViewer.setChecked(cpu, cpu.isHighlighted());
            }
            fCheckboxTreeViewer.setGrayed(m, m.isGrayed());
        }
        fCheckboxTreeViewer.collapseAll();

        return fCheckboxTreeViewer;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
    }
}
