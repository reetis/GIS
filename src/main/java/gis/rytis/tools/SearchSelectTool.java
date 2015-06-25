package gis.rytis.tools;

import gis.rytis.actions.SearchAction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.CursorTool;
import org.opengis.filter.FilterFactory2;

import java.awt.*;
import java.awt.geom.Rectangle2D;

public class SearchSelectTool extends CursorTool {
    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private SimpleFeatureSource featureSource;

    private String geometryAttributeName;
    private SearchAction parentAction;

    private Point startPosition = new Point();

    public SearchSelectTool(SearchAction parentAction) {
        this.parentAction  = parentAction;
    }

    @Override
    public void onMousePressed(MapMouseEvent ev) {
        startPosition.setLocation(ev.getPoint());
    }

    @Override
    public void onMouseReleased(MapMouseEvent ev) {
        Point endPosition = ev.getPoint();
        Rectangle screenRect = new Rectangle();;
        screenRect.setFrameFromDiagonal(startPosition, endPosition);

        /*
         * Transform the screen rectangle into bounding box in the coordinate
         * reference system of our map context. Note: we are using a naive method
         * here but GeoTools also offers other, more accurate methods.
         */
        Rectangle2D rectangle = getMapPane().getScreenToWorldTransform().createTransformedShape(screenRect).getBounds2D();
//        GeometryDescriptor geometryDescriptor = featureSource.getSchema().getGeometryDescriptor();
        ReferencedEnvelope bbox = new ReferencedEnvelope(rectangle, getMapPane().getMapContent().getCoordinateReferenceSystem());

        parentAction.setBBox(bbox);
        System.out.println(bbox);
    }

    @Override
    public boolean drawDragBox() {
        return true;
    }
}
