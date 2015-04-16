package gis.rytis;

import org.geotools.data.DataAccess;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.event.MapAdapter;
import org.geotools.map.event.MapLayerListEvent;
import org.geotools.swing.MapPane;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.table.FeatureCollectionTableModel;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AttributesWindow extends JFrame {
    private JComboBox<String> featureTypeCBox;
    private JComboBox<FeatureLayer> layerCBox;
    private JTable table;
    private JTextField text;
    private JPanel statusBar;
    private JLabel statusText;
    private MapPane mapPane;

    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    public AttributesWindow(MapPane mapPane) {
        this.mapPane = mapPane;

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        text = new JTextField(80);
        text.setText("include"); // include selects everything!
        getContentPane().add(text, BorderLayout.NORTH);
        text.addActionListener(e -> {
            try {
                showFeatures();
            } catch (Exception e1) {
                System.out.println("Klaida: " + e1);
            }
        });

        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(new DefaultTableModel());
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);

        layerCBox = new JComboBox<>();
        layerCBox.setRenderer(new layerCBoxRenderer());
        menubar.add(layerCBox);

        layerCBox.addItemListener(e -> onLayerChanged());

        featureTypeCBox = new JComboBox<>();
        menubar.add(featureTypeCBox);

        JMenu dataMenu = new JMenu("Selection");
        menubar.add(dataMenu);

        dataMenu.add(new SafeAction("Get selected") {
            public void action(ActionEvent e) throws Throwable {
                showSelectedFeatures();
            }
        });
        dataMenu.add(new SafeAction("Select on map") {
            public void action(ActionEvent e) throws Throwable {
                showSelectedItemsOnMap();
            }
        });

        statusBar = new JPanel();
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        getContentPane().add(statusBar, BorderLayout.SOUTH);
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusText = new JLabel("smth");
        statusText.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.add(statusText);

        pack();

        mapPane.getMapContent().addMapLayerListListener(
                new MapAdapter() {
                    @Override
                    public void layerAdded(MapLayerListEvent event) {
                        updateLayerCbox();
                    }

                    @Override
                    public void layerRemoved(MapLayerListEvent event) {
                        updateLayerCbox();
                    }

                    @Override
                    public void layerMoved(MapLayerListEvent event) {
                        updateLayerCbox();
                    }
                });

    }

    private void updateLayerCbox() {
        final Layer selectedLayer = getSelectedLayer();

        final java.util.List<FeatureLayer> layers = mapPane.getMapContent().layers().stream()
                .filter(it -> it instanceof FeatureLayer)
                .map(it -> (FeatureLayer)it)
                .collect(Collectors.toList());
        final FeatureLayer[] array = layers.toArray(new FeatureLayer[layers.size()]);

        layerCBox.setModel(new DefaultComboBoxModel<>(array));
        if (selectedLayer != null) {
            layerCBox.setSelectedItem(selectedLayer);
        }
        layerCBox.repaint();

        onLayerChanged();
    }

    private FeatureLayer getSelectedLayer() {
        return (FeatureLayer)layerCBox.getSelectedItem();
    }

    public void showFeatures() {
        String typeName = (String) featureTypeCBox.getSelectedItem();

        Optional<DataStore> dataStore = getSelectedLayerDataStore();
        if (!dataStore.isPresent()) {
            System.out.println("Klaida: Nėra pasirinkta sluoksnio ( showFeatures() )");
            table.setModel(new DefaultTableModel());
            statusText.setText("Count: 0");
            return;
        }
        try {
            SimpleFeatureSource source = dataStore.get().getFeatureSource(typeName);
            Filter filter = ECQL.toFilter(text.getText());
            SimpleFeatureCollection features = source.getFeatures(filter);
            FeatureCollectionTableModel model = new FeatureCollectionTableModel(features);
            table.setModel(model);
            statusText.setText("Count: " + features.size());
        } catch (IOException|CQLException e) {
            System.out.println("Klaida: " + e);
        }
    }

    private Optional<DataStore> getSelectedLayerDataStore() {
        final FeatureLayer selectedLayer = getSelectedLayer();
        if (selectedLayer == null) {
            return Optional.empty();
        }
        final DataAccess<SimpleFeatureType, SimpleFeature> dataAccess = selectedLayer.getSimpleFeatureSource().getDataStore();
        if (!(dataAccess instanceof DataStore)) {
            return Optional.empty();
        }

        return Optional.of((DataStore) dataAccess);
    }

    private void onLayerChanged() {
        Optional<DataStore> dataStore = getSelectedLayerDataStore();
        if (!dataStore.isPresent()) {
            System.out.println("Klaida: Nėra pasirinkta sluoksnio ( onLayerChanged() )");
            featureTypeCBox.setModel(new DefaultComboBoxModel<>());
            table.setModel(new DefaultTableModel());
            statusText.setText("Count: 0");
            return;
        }
        try {
            featureTypeCBox.setModel(new DefaultComboBoxModel<>(dataStore.get().getTypeNames()));
        } catch (IOException e) {
            System.out.println("Klaida " + e);
        }

        showFeatures();
    }

    private void showSelectedFeatures() {
        Optional<SelectableLayer> selectableLayer = getSelectableLayer();
        if (!selectableLayer.isPresent()) {
            return;
        }

        table.clearSelection();
        SimpleFeatureCollection selectedFeatures = selectableLayer.get().getSelectedFeatures();
        table.setModel(new FeatureCollectionTableModel(selectedFeatures));
        statusText.setText("Count: " + selectedFeatures.size());
    }

    private void showSelectedItemsOnMap() {
        Optional<SimpleFeatureSource> featureSource = getSelectedFeatureSource();
        if (!featureSource.isPresent()) {
            return;
        }

        Set<FeatureIdImpl> featureIds = IntStream.of(table.getSelectedRows()).boxed()
                .map(rowNumber -> (String) table.getValueAt(rowNumber, 0))
                .map(FeatureIdImpl::new)
                .collect(Collectors.toSet());

        if (featureIds.isEmpty()) {
            return;
        }

        final Filter filter = ff.id(featureIds);
        try {
            selectOnMap(featureSource.get().getFeatures(filter));
        } catch (IOException e) {
            System.out.println("Failed to select features by filter" + e);
        }
    }

    private Optional<SelectableLayer> getSelectableLayer() {
        final FeatureLayer selectedLayer = getSelectedLayer();
        if (selectedLayer == null) {
            return Optional.empty();
        }
        if (! (selectedLayer instanceof SelectableLayer)) {
            return Optional.empty();
        }

        return Optional.of((SelectableLayer) selectedLayer);
    }

    private static class layerCBoxRenderer extends JLabel implements ListCellRenderer<Layer> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Layer> list, Layer value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value == null) {
                setText("");
            } else {
                setText(value.getTitle());
            }
            return this;
        }
    }

    private Optional<SimpleFeatureSource> getSelectedFeatureSource() {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        Optional<DataStore> dataStore = getSelectedLayerDataStore();

        if (!dataStore.isPresent()) {
            return Optional.empty();
        }

        try {
            return Optional.of(dataStore.get().getFeatureSource(typeName));
        } catch (IOException e) {
            System.out.println("Failed to find feature source: " + e);
            return Optional.empty();
        }
    }

    private void selectOnMap(SimpleFeatureCollection features) throws IOException {
        Optional<SelectableLayer> selectableLayer = getSelectableLayer();
        if (selectableLayer.isPresent()) {
            selectableLayer.get().newSelection(features);
        }
    }
}
