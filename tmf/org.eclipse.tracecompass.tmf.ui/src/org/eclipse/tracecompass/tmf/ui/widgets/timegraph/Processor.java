package org.eclipse.tracecompass.tmf.ui.widgets.timegraph;

/**
 * @author Cedric Biancheri
 * @since 2.0
 *
 */
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
}
