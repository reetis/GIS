package gis.rytis;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.swing.MapPane;
import org.opengis.feature.simple.SimpleFeature;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

public class Utilities {
    public enum LayerType{
        POLYGON, LINE, DOT, UNKNOWN
    }

    public enum AreaType{
        HYDRO, FOREST, BUILDING, GARDEN, UNKNOWN
    }

    public static final Color[] colors = {  Color.BLUE.darker(),
            Color.BLACK,
            Color.RED.darker(),
            Color.GREEN.darker(),
            Color.GRAY};
    public static int colorIter = 0;

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

    public static List<SelectableLayer> createLayers(File file) {
        List<SelectableLayer> layers = new ArrayList<>();

        try {
            Map<String,Object> params = new HashMap<>();
            params.put( "url", file.toURI().toURL());
            params.put( "create spatial index", true);
            params.put( "memory mapped buffer", true);
            params.put( "charset", Charset.forName("Windows-1257") );

            DataStore store = DataStoreFinder.getDataStore(params);

            for (String typeName : store.getTypeNames()) {
                SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
                SimpleFeatureCollection features = featureSource.getFeatures();
                String layerTitle = file.getName() + "#" + typeName;

                SelectableLayer layer = createLayer(featureSource, layerTitle);
                layers.add(layer);
            }


        } catch (IOException exc) {
            System.out.println("Could not read file: " + exc);
        }

        return layers;
    }

    public static SelectableLayer createLayer(SimpleFeatureCollection features, String layerTitle) {
        SimpleFeatureSource indexedSource = DataUtilities.source(features);

        return createLayer(indexedSource, layerTitle);
    }

    public static SelectableLayer createLayer(SimpleFeatureSource source, String layerTitle) {

        SelectableLayer layer = new SelectableLayer(layerTitle, source, colors[colorIter%colors.length], colors[colorIter%colors.length].brighter());
        colorIter++;
        layer.setVisible(false);
        return layer;
    }

    public static Polygon createPolygon(ReferencedEnvelope bboxEnv) {
        final GeometryFactory gfac = new GeometryFactory();
        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = new Coordinate(bboxEnv.getMinX(), bboxEnv.getMinY());
        coordinates[1] = new Coordinate(bboxEnv.getMaxX(), bboxEnv.getMinY());
        coordinates[2] = new Coordinate(bboxEnv.getMaxX(), bboxEnv.getMaxY());
        coordinates[3] = new Coordinate(bboxEnv.getMinX(), bboxEnv.getMaxY());
        coordinates[4] = new Coordinate(bboxEnv.getMinX(), bboxEnv.getMinY());
        return gfac.createPolygon(coordinates);
    }
}
