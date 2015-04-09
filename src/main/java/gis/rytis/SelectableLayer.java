package gis.rytis;

import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.EmptyFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.styling.Style;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SelectableLayer extends FeatureLayer {

    private Style normalStyle;
    private Style selectedStyle;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Set<FeatureId> selectedFeatures = new HashSet<>();

    public SelectableLayer(Style normalStyle, Style selectedStyle, FeatureSource featureSource) {
        super(featureSource, normalStyle);
        this.normalStyle = normalStyle;
        this.selectedStyle = selectedStyle;
    }

    public void newSelection(SimpleFeatureCollection selection) {
        selectedFeatures.clear();
        addSelection(selection);
    }

    public void addSelection(SimpleFeatureCollection selection) {
        if (selection == null) {
            System.out.println("No features to select");
            return;
        }
        Set<FeatureId> selectionIDs = new HashSet<>();
        try {
            selection.accepts(x -> selectionIDs.add(x.getIdentifier()), null);
        } catch (IOException e) {
            System.out.println("Error adding features: " + e);
            return;
        }
        selectedFeatures.addAll(selectionIDs);
        System.out.println("Added features: " + selectionIDs.size());
        System.out.println("Current selected features in this layer: " + selectedFeatures.size());
    }

    public SimpleFeatureCollection findFeaturesCrossingBox(Rectangle2D box) {
        GeometryDescriptor geometryDescriptor = featureSource.getSchema().getGeometryDescriptor();
        ReferencedEnvelope bbox = new ReferencedEnvelope(box, geometryDescriptor.getCoordinateReferenceSystem());
        Filter filter = ff.intersects(ff.property(geometryDescriptor.getLocalName()), ff.literal(bbox));
        try {
            return getSimpleFeatureSource().getFeatures(filter);
        } catch (IOException e) {
            System.out.println("Error finding features: " + e);
            return null;
        }
    }

    public SimpleFeatureCollection getSelectedFeatures() {
        if (selectedFeatures.isEmpty()) {
            return new EmptyFeatureCollection(getSimpleFeatureSource().getSchema());
        }
        try {
            return getSimpleFeatureSource().getFeatures(ff.id(selectedFeatures));
        } catch (IOException e) {
            System.out.println("Klaida: " + e);
            return new EmptyFeatureCollection(getSimpleFeatureSource().getSchema());
        }
    }

    public ReferencedEnvelope getSelectionBounds() {
        SimpleFeatureCollection featureCollection = getSelectedFeatures();
        if (!featureCollection.isEmpty()) {
            return featureCollection.getBounds();
        } else {
            return null;
        }
    }
}
