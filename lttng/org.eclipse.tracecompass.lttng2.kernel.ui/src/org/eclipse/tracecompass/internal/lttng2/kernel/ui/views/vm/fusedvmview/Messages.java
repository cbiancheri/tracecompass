/*******************************************************************************
 * Copyright (c) 2013, 2015 Ericsson
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Patrick Tassé - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;

import org.eclipse.osgi.util.NLS;

@SuppressWarnings("javadoc")
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview.messages"; //$NON-NLS-1$

    public static String FusedVMView_stateTypeName;
    public static String FusedVMView_multipleStates;
    public static String FusedVMView_nextResourceActionNameText;
    public static String FusedVMView_nextResourceActionToolTipText;
    public static String FusedVMView_previousResourceActionNameText;
    public static String FusedVMView_previousResourceActionToolTipText;
    public static String FusedVMView_attributeCpuName;
    public static String FusedVMView_attributeIrqName;
    public static String FusedVMView_attributeSoftIrqName;
    public static String FusedVMView_attributeHoverTime;
    public static String FusedVMView_attributeTidName;
    public static String FusedVMView_attributeProcessName;
    public static String FusedVMView_attributeSyscallName;
    public static String FusedVMView_selectMachineText;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
