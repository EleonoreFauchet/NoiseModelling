/**
 * NoiseModelling is a free and open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 * as part of:
 * the Eval-PDU project (ANR-08-VILL-0005) 2008-2011, funded by the Agence Nationale de la Recherche (French)
 * the CENSE project (ANR-16-CE22-0012) 2017-2021, funded by the Agence Nationale de la Recherche (French)
 * the Nature4cities (N4C) project, funded by European Union’s Horizon 2020 research and innovation programme under grant agreement No 730468
 *
 * Noisemap is distributed under GPL 3 license.
 *
 * Contact: contact@noise-planet.org
 *
 * Copyright (C) 2011-2012 IRSTV (FR CNRS 2488) and Ifsttar
 * Copyright (C) 2013-2019 Ifsttar and CNRS
 * Copyright (C) 2020 Université Gustave Eiffel and CNRS
 *
 * @Author Pierre Aumond, Université Gustave Eiffel
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */


package org.noise_planet.noisemodelling.wps.Others_Tools

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.time.TimeCategory
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.SFSUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.noise_planet.noisemodelling.emission.jdbc.BezierContouring

import java.sql.Connection
import java.sql.Statement

title = 'Create Isosurface polygons from NoiseModelling result and Triangles'

description = 'Create vectorial noise maps using the isocontouring method. Output table is <b>CONTOURING_NOISE_MAP</b>'

inputs = [resultTable  : [name: 'Noise levels table', title: 'Name of the noise table', description: 'Name of the noise table, ex LDEN_GEOM or LDAY_GEOM or LEVENING_GEOM or LNIGHT_GEOM generated from Noise_level_from_source. Receivers table must be created using Delaunay_Grid function',
                      type: String.class],
          outputTable: [name: 'Contouring noise map table', title: 'Name of the output table', description: 'Name of the created table, will be deleted if it exists. Default is <b>CONTOURING_NOISE_MAP</b>',
                        min: 0, max: 1, type: String.class],
          triangleTable: [name: 'Triangle vertices table', title: 'Triangle table', description: 'This table is generated by Delaunay_Grid function. Default TRIANGLES',
                        min: 0, max: 1, type: String.class],
          isoClass: [name: 'Iso-levels', title: 'Iso levels in dB', description: 'Comma separated noise level (dB) separation of surfaces. Default to 35.0,40.0,45.0,50.0,55.0,60.0,65.0,70.0,75.0,80.0,200.0',
                          min: 0, max: 1, type: String.class],
          smoothCoefficient: [name: 'Polygon smoothing coefficient', title: 'Bezier curve coefficient', description: 'This coefficient will smooth generated isosurfaces. At 0 it disables the smoothing step. Default value 1.0',
                     min: 0, max: 1, type: Double.class]
]

outputs = [result: [name: 'Result output string', title: 'Result output string', description: 'This type of result does not allow the blocks to be linked together.', type: String.class]]


static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def exec(Connection connection, input) {

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // output string, the information given back to the user
    String resultString = null

    long start = System.currentTimeMillis();

    List<Double> isoLevels = BezierContouring.NF31_133_ISO;

    if(input.containsKey("isoClass")) {
        isoLevels = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(input['isoClass'] as String, ",")
        while(st.hasMoreTokens()) {
            isoLevels.add(Double.parseDouble(st.nextToken()))
        }
    }

    String levelTable = input['resultTable'] as String

    int srid = SFSUtilities.getSRID(connection, TableLocation.parse(levelTable))

    BezierContouring bezierContouring = new BezierContouring(isoLevels, srid)

    bezierContouring.setPointTable(levelTable)

    if(input.containsKey("outputTable")) {
        bezierContouring.setOutputTable(input['outputTable'] as String)
    }

    if(input.containsKey("triangleTable")) {
        bezierContouring.setTriangleTable(input['triangleTable'] as String)
    }

    if(input.containsKey("smoothCoefficient")) {
        double coefficient = input['smoothCoefficient'] as Double
        if(coefficient < 0.01) {
            bezierContouring.setSmooth(false)
        } else {
            bezierContouring.setSmooth(true)
            bezierContouring.setSmoothCoefficient(coefficient)
        }
    }

    System.out.println("Compute Isosurfaces")

    bezierContouring.createTable(connection)

    System.out.println(String.format(Locale.ROOT, 'Duration : %d ms', System.currentTimeMillis() - start));

    resultString = "Table " + bezierContouring.getOutputTable() + " created"

    // print to WPS Builder
    return resultString
}


def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}