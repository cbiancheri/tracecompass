/*******************************************************************************
 * Copyright (c) 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.StateValues;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * This class represents a virtual CPU, which is a CPU running on a guest. It
 * associates the guest CPU ID to a virtual machine of the model.
 *
 * @author Geneviève Bastien
 */
public class VirtualCPU {

    private static final Table<VirtualMachine, Long, @Nullable VirtualCPU> VIRTUAL_CPU_TABLE = HashBasedTable.create();

    private final VirtualMachine fVm;
    private final Long fCpuId;
    /* Current state of the cpu. */
    private ITmfStateValue currentState;
    /* Current thread of the cpu. */
    private ITmfStateValue currentThread;

    /**
     * Return the virtual CPU for to the virtual machine and requested CPU ID
     *
     * @param vm
     *            The virtual machine
     * @param cpu
     *            the CPU number
     * @return the virtual CPU
     */
    public static synchronized VirtualCPU getVirtualCPU(VirtualMachine vm, Long cpu) {
        VirtualCPU ht = VIRTUAL_CPU_TABLE.get(vm, cpu);
        if (ht == null) {
            ht = new VirtualCPU(vm, cpu);
            VIRTUAL_CPU_TABLE.put(vm, cpu, ht);
        }
        return ht;
    }

    public static synchronized void addVirtualCPU(VirtualMachine vm, Long cpu) {
        getVirtualCPU(vm, cpu);
    }

    public static synchronized @Nullable Map<Long, @Nullable VirtualCPU> getVirtualCPUs(VirtualMachine machine) {
        return VIRTUAL_CPU_TABLE.row(machine);
    }

    private VirtualCPU(VirtualMachine vm, Long cpu) {
        fVm = vm;
        fCpuId = cpu;
        currentState = StateValues.CPU_STATUS_IDLE_VALUE;
        currentThread = TmfStateValue.newValueInt(-1);
    }

    /**
     * Get the CPU ID of this virtual CPU
     *
     * @return The zero-based CPU ID
     */
    public Long getCpuId() {
        return fCpuId;
    }

    /**
     * Get the virtual machine object this virtual CPU belongs to
     *
     * @return The guest Virtual Machine
     */
    public VirtualMachine getVm() {
        return fVm;
    }

    @Override
    public String toString() {
        return "VirtualCPU: [" + fVm + ',' + fCpuId + ']'; //$NON-NLS-1$
    }

    /**
     * Get the current state.
     * @return the currentState
     */
    public ITmfStateValue getCurrentState() {
        return currentState;
    }

    /**
     * Set the current state.
     * @param currentState
     *            the currentState to set
     */
    public void setCurrentState(ITmfStateValue currentState) {
        this.currentState = currentState;
    }

    /**
     * Get the current thread.
     * @return the currentThread
     */
    public ITmfStateValue getCurrentThread() {
        return currentThread;
    }

    /**
     * Set the current state.
     * @param currentThread
     *            the currentThread to set
     */
    public void setCurrentThread(ITmfStateValue currentThread) {
        this.currentThread = currentThread;
    }

}