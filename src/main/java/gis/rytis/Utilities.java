package gis.rytis;

import com.vividsolutions.jts.geom.*;
import org.geotools.map.FeatureLayer;
import org.geotools.swing.MapPane;

import java.util.Optional;

public class Utilities {
    public enum LayerType{
        POLYGON, LINE, DOT, UNKNOWN
    }

    public static Optional<SelectableLayer> findLayerByName(MapPane mapPane, String layerName) {
        return mapPane.getMapContent().layers().stream()
                .filter(x -> x instanceof SelectableLayer)
                .map(x -> (SelectableLayer) x)
                .filter(x -> x.getTitle().toLowerCase().contains(layerName.toLowerCase()))
                .findFirst();
    }

    public static LayerType getLayerType(FeatureLayer layer) {
        Class geomType = layer.getFeatureSource().getSchema().getGeometryDescriptor().getType().getBinding();
        if (Polygon.class.isAssignableFrom(geomType)
                || MultiPolygon.class.isAssignableFrom(geomType)) {
            return LayerType.POLYGON;

        } else if (LineString.class.isAssignableFrom(geomType)
                || MultiLineString.class.isAssignableFrom(geomType)) {
            return LayerType.LINE;

        } else if (Point.class.isAssignableFrom(geomType)
                || MultiPoint.class.isAssignableFrom(geomType)){
            return LayerType.DOT;
        } else {
            return LayerType.UNKNOWN;
        }
    }
}
