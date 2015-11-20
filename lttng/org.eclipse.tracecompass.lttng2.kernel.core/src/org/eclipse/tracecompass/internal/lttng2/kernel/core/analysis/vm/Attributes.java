package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm;

/**
 * This file defines all the attribute names used in the handler. Both the
 * construction and query steps should use them.
 *
 * These should not be externalized! The values here are used as-is in the
 * history file on disk, so they should be kept the same to keep the file format
 * compatible. If a view shows attribute names directly, the localization should
 * be done on the viewer side.
 *
 * @author alexmont
 *
 */
@SuppressWarnings({"nls", "javadoc"})
public interface Attributes {

    /* First-level attributes */
    String CPUS = "CPUs";
    String THREADS = "Threads";
    String RESOURCES = "Resources";

    /* Sub-attributes of the CPU nodes */
    String CURRENT_THREAD = "Current_thread";
    String STATUS = "Status";
    String CONDITION = "Condition";
    String MACHINE_NAME = "Machine_name";

    /* Sub-attributes of the Thread nodes */
    String PPID = "PPID";
    //static final String STATUS = "Status"
    String EXEC_NAME = "Exec_name";

    /** @since 1.0 */
    String PRIO = "Prio";
    String SYSTEM_CALL = "System_call";

    /* Attributes under "Resources" */
    String IRQS = "IRQs";
    String SOFT_IRQS = "Soft_IRQs";

    /* Misc stuff */
    String UNKNOWN = "Unknown";
}