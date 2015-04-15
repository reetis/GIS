package gis.rytis.actions;

import gis.rytis.AttributesWindow;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class AttributesWindowAction extends MapAction {

    public static final String TOOL_NAME = "Attributes";
    public static final String TOOL_TIP = "Show features attributes";
    public static final String ICON_IMAGE = null;
    private final AttributesWindow frame;

    public AttributesWindowAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);

        frame = new AttributesWindow(mapPane);
        frame.setLocationRelativeTo(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame.setVisible(!frame.isVisible());
        frame.showFeatures();
    }
}
