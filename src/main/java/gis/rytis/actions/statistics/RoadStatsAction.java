package gis.rytis.actions.statistics;

import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;

import java.awt.event.ActionEvent;

public class RoadStatsAction extends MapAction {

    public static final String TOOL_NAME = "Roads";
    public static final String TOOL_TIP = "Roads statistics";
    public static final String ICON_IMAGE = null;

    public RoadStatsAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }
}
