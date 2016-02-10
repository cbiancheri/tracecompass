package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.lxc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.IVirtualMachineModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualCPU;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.model.VirtualMachine;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class LxcModel implements IVirtualMachineModel {
    /* Maps a namespace ID to a container */
    private final Map<Long, VirtualMachine> fKnownContainers = new HashMap<>();

    @Override
    public @Nullable VirtualMachine getCurrentMachine(@NonNull ITmfEvent event) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @NonNull Set<@NonNull String> getRequiredEvents() {
        // TODO Replace by required events
        Set<String> temporarySet = new HashSet();
        return temporarySet;
    }

    @Override
    public @Nullable VirtualCPU getVCpuEnteringHypervisorMode(@NonNull ITmfEvent event, @NonNull HostThread ht) {
        // Not used
        return null;
    }

    @Override
    public @Nullable VirtualCPU getVCpuExitingHypervisorMode(@NonNull ITmfEvent event, @NonNull HostThread ht) {
        // Not used
        return null;
    }

    @Override
    public @Nullable VirtualCPU getVirtualCpu(@NonNull HostThread ht) {
        // Not used
        return null;
    }

    @Override
    public void handleEvent(@NonNull ITmfEvent event) {
        // TODO Auto-generated method stub

    }

}
