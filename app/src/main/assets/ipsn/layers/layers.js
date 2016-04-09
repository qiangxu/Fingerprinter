var format_entitiesPolygon = new ol.format.GeoJSON();
var features_entitiesPolygon = format_entitiesPolygon.readFeatures(geojson_entitiesPolygon, 
            {dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3786'});
var jsonSource_entitiesPolygon = new ol.source.Vector();
jsonSource_entitiesPolygon.addFeatures(features_entitiesPolygon);var lyr_entitiesPolygon = new ol.layer.Vector({
                source:jsonSource_entitiesPolygon, 
                style: style_entitiesPolygon,
                title: "entities Polygon"
            });var format_entitiesLineString = new ol.format.GeoJSON();
var features_entitiesLineString = format_entitiesLineString.readFeatures(geojson_entitiesLineString, 
            {dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3786'});
var jsonSource_entitiesLineString = new ol.source.Vector();
jsonSource_entitiesLineString.addFeatures(features_entitiesLineString);var lyr_entitiesLineString = new ol.layer.Vector({
                source:jsonSource_entitiesLineString, 
                style: style_entitiesLineString,
                title: "entities LineString"
            });var format_entitiesPoint = new ol.format.GeoJSON();
var features_entitiesPoint = format_entitiesPoint.readFeatures(geojson_entitiesPoint, 
            {dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3786'});
var jsonSource_entitiesPoint = new ol.source.Vector();
jsonSource_entitiesPoint.addFeatures(features_entitiesPoint);var lyr_entitiesPoint = new ol.layer.Vector({
                source:jsonSource_entitiesPoint, 
                style: style_entitiesPoint,
                title: "entities Point"
            });

lyr_entitiesPolygon.setVisible(true);lyr_entitiesLineString.setVisible(true);lyr_entitiesPoint.setVisible(true);
var layersList = [lyr_entitiesPolygon,lyr_entitiesLineString,lyr_entitiesPoint];
