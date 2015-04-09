package gis.rytis.actions;

import gis.rytis.tools.SelectTool;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;

import java.awt.event.ActionEvent;

public class SelectAction extends MapAction {

    public static final String TOOL_NAME = "Select";
    public static final String TOOL_TIP = "Select one or few features";
    public static final String ICON_IMAGE = "/org/geotools/swing/icons/pointer.png";

    public SelectAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        getMapPane().setCursorTool(new SelectTool());
    }
}
