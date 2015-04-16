package gis.rytis.actions;

import gis.rytis.SelectableLayer;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.Layer;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;

import java.awt.event.ActionEvent;

public class ZoomToSelectionAction extends MapAction {

    public static final String TOOL_NAME = "Zoom To Selection";
    public static final String TOOL_TIP = "Zoom To Selection";
    public static final String ICON_IMAGE = null;

    public ZoomToSelectionAction(MapPane mapPane) {
        super.init(mapPane, TOOL_NAME, TOOL_TIP, ICON_IMAGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ReferencedEnvelope zoomArea = new ReferencedEnvelope();

        getMapPane().getMapContent().layers().stream()
                .filter(Layer::isSelected)
                .filter(Layer::isVisible)
                .filter(x -> x instanceof SelectableLayer)
                .map(x -> ((SelectableLayer) x).getSelectionBounds())
                .filter(x -> x != null)
                .forEach(x -> zoomArea.expandToInclude(x));

        getMapPane().setDisplayArea(zoomArea);
    }
}
