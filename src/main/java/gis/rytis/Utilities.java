package gis.rytis;

import com.vividsolutions.jts.geom.*;
import org.geotools.map.FeatureLayer;
import org.geotools.swing.MapPane;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Optional;

public class Utilities {
    public enum LayerType{
        POLYGON, LINE, DOT, UNKNOWN
    }

    public enum AreaType{
        HYDRO, FOREST, BUILDING, GARDEN, UNKNOWN
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

    public static AreaType getAreaType(SimpleFeature area) {
        String gkodas = (String) area.getAttribute("GKODAS");
        switch (gkodas) {
            case "hd1":
            case "hd2":
            case "hd9":
            case "hd3":
            case "hd4":
            case "hd5":
                return AreaType.HYDRO;
            case "ms0":
                return AreaType.FOREST;
            case "ms4":
                return AreaType.GARDEN;
            case "pu0":
                return AreaType.BUILDING;
            default:
                return AreaType.UNKNOWN;
        }
    }

}
