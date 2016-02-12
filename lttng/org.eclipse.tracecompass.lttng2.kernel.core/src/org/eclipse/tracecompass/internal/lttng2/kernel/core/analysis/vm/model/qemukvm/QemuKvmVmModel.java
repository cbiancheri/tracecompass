/*******************************************************************************
 * Copyright (c) 2014, 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.qemukvm;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelThreadInformationProvider;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.IVirtualMachineModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperimentUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;

/**
 * The virtual machine model corresponding to the Qemu/KVM hypervisor. It uses
 * the kvm_exit/kvm_entry events to identify entry to and exit from the
 * hypervisor. It also requires vmsync_* events from both guests and hosts to
 * identify which thread from a host belongs to which machine.
 *
 * @author Mohamad Gebai
 */
public class QemuKvmVmModel implements IVirtualMachineModel {

    private static final String KVM = "kvm_"; //$NON-NLS-1$

    /* Associate a host's thread to a virtual CPU */
    private final Map<HostThread, VirtualCPU> fTidToVcpu = new HashMap<>();
    /* Associate a host's thread to a virtual machine */
    private final Map<HostThread, VirtualMachine> fTidToVm = new HashMap<>();
    /* Maps a virtual machine name to a virtual machine */
    private final Map<String, VirtualMachine> fKnownMachines = new HashMap<>();
    /* Associate a VM and a VCPU to a PCPU */
    private final Table<VirtualMachine, VirtualCPU, Long> fVirtualToPhysicalCpu = NonNullUtils.checkNotNull(HashBasedTable.<VirtualMachine, VirtualCPU, Long> create());

    private final TmfExperiment fExperiment;

    static final ImmutableSet<String> REQUIRED_EVENTS = ImmutableSet.of(
            QemuKvmStrings.KVM_ENTRY,
            QemuKvmStrings.KVM_EXIT,
            QemuKvmStrings.VMSYNC_GH_GUEST,
            QemuKvmStrings.VMSYNC_GH_HOST,
            QemuKvmStrings.VMSYNC_HG_GUEST,
            QemuKvmStrings.VMSYNC_HG_HOST
            );

    /**
     * Constructor
     *
     * @param exp
     *            The experiment this model applies to
     */
    public QemuKvmVmModel(TmfExperiment exp) {
        fExperiment = exp;
    }

    @Override
    public @Nullable VirtualMachine getCurrentMachine(ITmfEvent event) {
        final String hostId = event.getTrace().getHostId();
        VirtualMachine machine = fKnownMachines.get(hostId);
        if (machine != null) {
            return machine;
        }

        /*
         * TODO: This model wouldn't support a machine (same hostId) being both
         * a guest and a host
         */

        /*
         * This code path would be used only at the beginning of the analysis.
         * Once all the hostIds have been associated with a virtual machine, the
         * machines are all cached and the method returns earlier
         */
        /* Try to get the virtual machine from the event */
        String eventName = event.getName();
        String traceName = event.getTrace().getName();
        if (traceName == null) {
            traceName = "Unknown trace"; //$NON-NLS-1$
        }
        if (eventName.startsWith(KVM)) {
            /* Only the host machine has kvm_* events, so this is a host */
            machine = VirtualMachine.newHostMachine(hostId, traceName);
        } else if (eventName.equals(QemuKvmStrings.VMSYNC_GH_GUEST) || eventName.equals(QemuKvmStrings.VMSYNC_HG_GUEST)) {
            /* Those events are only present in the guests */
            TmfEventField field = (TmfEventField) event.getContent();
            ITmfEventField data = field.getField(QemuKvmStrings.VM_UID_PAYLOAD);
            if (data != null) {
                machine = VirtualMachine.newGuestMachine((Long) data.getValue(), hostId, traceName);
            }
        }
        if (machine != null) {
            /*
             * Associate the machine to the hostID here, for cached access later
             */
            fKnownMachines.put(hostId, machine);
        }
        return machine;
    }

    @Override
    public Set<String> getRequiredEvents() {
        return REQUIRED_EVENTS;
    }

    private @Nullable VirtualMachine findVmFromParent(ITmfEvent event, HostThread ht) {
        /*
         * Maybe the parent of the current thread has a VM associated, see if we
         * can infer the VM for this thread
         */
        KernelAnalysisModule module = getLttngKernelModuleFor(ht.getHost());
        if (module == null) {
            return null;
        }

        Integer ppid = KernelThreadInformationProvider.getParentPid(module, ht.getTid(), event.getTimestamp().getValue());
        if (ppid == null) {
            return null;
        }

        HostThread parentHt = new HostThread(ht.getHost(), ppid);
        VirtualMachine vm = fTidToVm.get(parentHt);
        if (vm == null) {
            return null;
        }
        fTidToVm.put(ht, vm);

        return vm;
    }

    @Override
    public @Nullable VirtualCPU getVCpuExitingHypervisorMode(ITmfEvent event, HostThread ht) {
        final String eventName = event.getName();

        /* TODO: Use event layouts for this part also */
        /*
         * The KVM_ENTRY event means we are entering a virtual CPU, so exiting
         * hypervisor mode
         */
        if (!eventName.equals(QemuKvmStrings.KVM_ENTRY)) {
            return null;
        }

        /*
         * Are we entering the hypervisor and if so, which virtual CPU is
         * concerned?
         */
        VirtualMachine vm = fTidToVm.get(ht);
        if (vm == null) {
            vm = findVmFromParent(event, ht);
            if (vm == null) {
                return null;
            }
        }
        /* Associate this thread with the virtual CPU that is going to be run */
        final ITmfEventField content = event.getContent();
        long vcpu_id = (Long) content.getField(QemuKvmStrings.VCPU_ID).getValue();

        VirtualCPU virtualCPU = VirtualCPU.getVirtualCPU(vm, vcpu_id);
        fTidToVcpu.put(ht, virtualCPU);

        return virtualCPU;
    }

    @Override
    public @Nullable VirtualCPU getVCpuEnteringHypervisorMode(ITmfEvent event, HostThread ht) {
        final String eventName = event.getName();
        /*
         * The KVM_EXIT event means we are exiting a virtual CPU, so entering
         * hypervisor mode
         */
        if (!eventName.equals(QemuKvmStrings.KVM_EXIT)) {
            return null;
        }

        return getVirtualCpu(ht);
    }

    @Override
    public @Nullable VirtualCPU getVirtualCpu(HostThread ht) {
        return fTidToVcpu.get(ht);
    }

    @Override
    public void handleEvent(ITmfEvent event) {
        /* Is the event handled by this model */
        final String eventName = event.getName();
        switch (eventName) {
        case QemuKvmStrings.VMSYNC_GH_HOST:
            if (!eventName.equals(QemuKvmStrings.VMSYNC_GH_HOST)) {
                return;
            }

            final ITmfEventField content = event.getContent();
            long ts = event.getTimestamp().getValue();
            String hostId = event.getTrace().getHostId();

            Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
            if (cpu == null) {
                /* We couldn't find any CPU information, ignore this event */
                return;
            }

            /* Find a virtual machine with the vm uid payload value */
            ITmfEventField data = content.getField(QemuKvmStrings.VM_UID_PAYLOAD);
            if (data == null) {
                return;
            }
            long vmUid = (Long) data.getValue();
            for (Entry<String, VirtualMachine> entry : fKnownMachines.entrySet()) {
                if (entry.getValue().getVmUid() == vmUid) {
                    /*
                     * We found the VM being run, let's associate it with the
                     * thread ID
                     */
                    KernelAnalysisModule module = getLttngKernelModuleFor(hostId);
                    if (module == null) {
                        break;
                    }
                    Integer tid = KernelThreadInformationProvider.getThreadOnCpu(module, cpu, ts);
                    if (tid == null) {
                        /*
                         * We do not know which process is running at this
                         * point. It may happen at the beginning of the trace.
                         */
                        break;
                    }
                    HostThread ht = new HostThread(hostId, tid);
                    fTidToVm.put(ht, entry.getValue());

                    /*
                     * To make sure siblings are also associated with this VM,
                     * also add an entry for the parent TID
                     */
                    Integer ppid = KernelThreadInformationProvider.getParentPid(module, tid, ts);
                    if (ppid != null) {
                        HostThread parentHt = new HostThread(hostId, ppid);
                        fTidToVm.put(parentHt, entry.getValue());
                    }
                }
            }
            break;
        case QemuKvmStrings.KVM_ENTRY:
            hostId = event.getTrace().getHostId();
            ts = event.getTimestamp().getValue();
            cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
            if (cpu == null) {
                /* We couldn't find any CPU information, ignore this event */
                return;
            }
            KernelAnalysisModule module = getLttngKernelModuleFor(hostId);
            if (module == null) {
                break;
            }
            Integer tid = KernelThreadInformationProvider.getThreadOnCpu(module, cpu, ts);
            if (tid == null) {
                /*
                 * We do not know which process is running at this point. It may
                 * happen at the beginning of the trace.
                 */
                break;
            }
            HostThread ht = new HostThread(hostId, tid);
            VirtualCPU vcpu = getVCpuExitingHypervisorMode(event, ht);
            VirtualMachine virtualMachine = fTidToVm.get(ht);
            if (virtualMachine == null) {
                return;
            }
            fVirtualToPhysicalCpu.put(virtualMachine, vcpu, cpu.longValue());
            break;
        default:
        }
        return;

    }

    private @Nullable KernelAnalysisModule getLttngKernelModuleFor(String hostId) {
        return TmfExperimentUtils.getAnalysisModuleOfClassForHost(fExperiment, hostId, KernelAnalysisModule.class);
    }

    /**
     * Return one of the host threads running a virtual machine.
     *
     * @param virtualMachine
     *            The virtual machine.
     * @return One of the host threads.
     */
    public @Nullable HostThread getHostThreadFromVm(VirtualMachine virtualMachine) {
        for (Entry<HostThread, VirtualMachine> entry : fTidToVm.entrySet()) {
            if (virtualMachine.getVmUid() == entry.getValue().getVmUid()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Return the physical cpu where a vcpu is currently running.
     *
     * @param virtualMachine
     *            The virtual machine that possesses the vcpu.
     * @param vcpu
     *            The vcpu.
     * @return The physical cpu.
     */
    public @Nullable Long getPhysicalCpuFromVcpu(VirtualMachine virtualMachine, VirtualCPU vcpu) {
        return fVirtualToPhysicalCpu.get(virtualMachine, vcpu);
    }

    /**
     * Get the vm that a host thread is running.
     *
     * @param ht
     *            The host thread.
     * @return The virtual machine.
     */
    public @Nullable VirtualMachine getVmFromHostThread(HostThread ht) {
        return fTidToVm.get(ht);
    }

    /**
     * @param v
     */
    public void AddKnownMachine(VirtualMachine v) {
        fKnownMachines.put(v.getHostId(), v);
    }

    /**
     * Return the number of known machines
     *
     * @return The number of known machines
     */
    public int numberOfKnownMachines() {
        return fKnownMachines.size();
    }

}
