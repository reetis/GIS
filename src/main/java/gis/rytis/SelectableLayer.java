package gis.rytis;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.store.EmptyFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.event.MapLayerEvent;
import org.geotools.styling.*;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SelectableLayer extends FeatureLayer {
    Rule selectedRule, defaultRule;

    static StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Set<FeatureId> selectedFeatures = new HashSet<>();

    public SelectableLayer(String layerTitle, FeatureSource featureSource, Color primaryColor, Color secondaryColor) {
        super(featureSource, null);
        Color selectedPrimaryColor = Color.YELLOW.darker();
        Color selectedSecondaryColor = selectedPrimaryColor.brighter();
        setTitle(layerTitle);

        SimpleFeatureType schema = (SimpleFeatureType)featureSource.getSchema();
        Class geomType = schema.getGeometryDescriptor().getType().getBinding();


        if (Polygon.class.isAssignableFrom(geomType)
                || MultiPolygon.class.isAssignableFrom(geomType)) {
            selectedRule = createPolygonRule(selectedPrimaryColor, selectedSecondaryColor);
            defaultRule = createPolygonRule(primaryColor, secondaryColor);
        } else if (LineString.class.isAssignableFrom(geomType)
                || MultiLineString.class.isAssignableFrom(geomType)) {
            selectedRule = createLineRule(selectedPrimaryColor);
            defaultRule = createLineRule(primaryColor);
        } else {
            selectedRule = createPointRule(selectedPrimaryColor, selectedSecondaryColor);
            defaultRule = createPointRule(primaryColor, secondaryColor);
        }

        updateStyle();
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
        updateStyle();
        System.out.println("Added features: " + selectionIDs.size());
        System.out.println("Current selected features in this layer: " + selectedFeatures.size());
    }

    public void deselectFeatures() {
        selectedFeatures.clear();
        updateStyle();
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
            System.out.println("Error: " + e);
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

    private void updateStyle() {
        setStyle(createStyle());
    }

    private Style createStyle() {
        selectedRule.setFilter(ff.id(selectedFeatures));
        defaultRule.setElseFilter(true);

        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.rules().add(selectedRule);
        fts.rules().add(defaultRule);

        Style style = styleFactory.createStyle();
        style.featureTypeStyles().add(fts);

        return style;
    }

    private Rule createPolygonRule(Color primaryColor, Color secondaryColor) {

        // create a partially opaque outline stroke
        org.geotools.styling.Stroke stroke = styleFactory.createStroke(
                ff.literal(primaryColor),
                ff.literal(1),
                ff.literal(0.5));

        // create a partial opaque fill
        Fill fill = styleFactory.createFill(
                ff.literal(secondaryColor),
                ff.literal(0.5));

        /*
         * Setting the geometryPropertyName arg to null signals that we want to
         * draw the default geomettry of features
         */
        PolygonSymbolizer sym = styleFactory.createPolygonSymbolizer(stroke, fill, null);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(sym);

        return rule;
    }

    private Rule createLineRule(Color primaryColor) {
        org.geotools.styling.Stroke stroke = styleFactory.createStroke(
                ff.literal(primaryColor),
                ff.literal(1));

        /*
         * Setting the geometryPropertyName arg to null signals that we want to
         * draw the default geomettry of features
         */
        LineSymbolizer sym = styleFactory.createLineSymbolizer(stroke, null);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(sym);

        return rule;
    }

    private Rule createPointRule(Color primaryColor, Color secondaryColor) {
        Graphic gr = styleFactory.createDefaultGraphic();

        Mark mark = styleFactory.getCircleMark();

        mark.setStroke(styleFactory.createStroke(
                ff.literal(primaryColor), ff.literal(1)));

        mark.setFill(styleFactory.createFill(ff.literal(secondaryColor)));

        gr.graphicalSymbols().clear();
        gr.graphicalSymbols().add(mark);
        gr.setSize(ff.literal(5));

        /*
         * Setting the geometryPropertyName arg to null signals that we want to
         * draw the default geomettry of features
         */
        PointSymbolizer sym = styleFactory.createPointSymbolizer(gr, null);

        Rule rule = styleFactory.createRule();
        rule.symbolizers().add(sym);

        return rule;
    }

    @Override
    public void setStyle(Style style) {
        if (style == null) {
            throw new NullPointerException("Style is required");
        }
        if (style.featureTypeStyles().size() == 1) {
            if (style.featureTypeStyles().get(0).rules().size() == 1) {
                defaultRule = style.featureTypeStyles().get(0).rules().get(0);
                updateStyle();
                return;
            }
        }

        this.style = style;
        fireMapLayerListenerLayerChanged(MapLayerEvent.STYLE_CHANGED);
    }
}
