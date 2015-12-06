package org.eclipse.tracecompass.tmf.ui.widgets.timegraph;

/**
 * @author Cedric Biancheri
 * @since 2.0
 *
 */
public class Processor {

    private String n;
    private Boolean highlighted;

    public Processor(String p){
        n = p;
        highlighted = true;
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
}
