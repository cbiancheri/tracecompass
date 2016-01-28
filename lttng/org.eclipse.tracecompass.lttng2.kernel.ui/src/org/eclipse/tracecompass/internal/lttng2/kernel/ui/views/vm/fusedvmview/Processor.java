package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.fusedvmview;


public class Processor {
    private String n;
    private Boolean highlighted;
    private Machine machine;

    public Processor(String p, Machine m){
        n = p;
        highlighted = true;
        machine = m;
    }

    @Override
    public String toString(){
        return n;
    }

    public Boolean isHighlighted(){
        return highlighted;
    }

    public void setHighlighted(Boolean b) {
        highlighted = b;
    }

    public Machine getMachine() {
        return machine;
    }

    public String getNumber() {
        return n;
    }
}
