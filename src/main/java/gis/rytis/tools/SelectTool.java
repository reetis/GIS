package gis.rytis.tools;

import gis.rytis.SelectableLayer;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.Layer;
import org.geotools.styling.Style;
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.CursorTool;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class SelectTool extends CursorTool {
    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private SimpleFeatureSource featureSource;

    private String geometryAttributeName;

    private boolean isCtrlPressed = false;
    private boolean isDragged = false;

    private Point startPosition = new Point();

    @Override
    public void onMousePressed(MapMouseEvent ev) {
        startPosition.setLocation(ev.getPoint());
        isCtrlPressed = ev.isControlDown();
    }

    @Override
    public void onMouseDragged(MapMouseEvent ev) {
        isDragged = true;
    }

    @Override
    public void onMouseReleased(MapMouseEvent ev) {
        System.out.println("Mouse released at: " + ev.getMapPosition());
        Point endPosition = ev.getPoint();
        Rectangle screenRect = new Rectangle();;
        if (!isDragged || startPosition.equals(endPosition)) {
            screenRect.setFrame(endPosition.x-2, endPosition.y-2, 5, 5);
        } else {
            screenRect.setFrameFromDiagonal(startPosition, endPosition);
        }
        isDragged = false;

        /*
         * Transform the screen rectangle into bounding box in the coordinate
         * reference system of our map context. Note: we are using a naive method
         * here but GeoTools also offers other, more accurate methods.
         */
        Rectangle2D rectangle = getMapPane().getScreenToWorldTransform().createTransformedShape(screenRect).getBounds2D();

        getSelectableLayer()
                .forEach(x -> {
                    if (isCtrlPressed) x.addSelection(x.findFeaturesCrossingBox(rectangle));
                    else x.newSelection(x.findFeaturesCrossingBox(rectangle));
                });

        isCtrlPressed = false;
    }

    private Stream<SelectableLayer> getSelectableLayer() {
        return getMapPane().getMapContent().layers().parallelStream()
                .filter(x -> x instanceof SelectableLayer)
                .filter(Layer::isSelected)
                .filter(Layer::isVisible)
                .map(x -> (SelectableLayer) x);
    }

    @Override
    public boolean drawDragBox() {
        return true;
    }
}
