/*
 * Copyright 2019-2024 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.adapter.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.pf4j.Extension;
import org.polypheny.db.adapter.ConnectionMethod;
import org.polypheny.db.adapter.DataSource;
import org.polypheny.db.adapter.DeployMode;
import org.polypheny.db.adapter.RelationalScanDelegate;
import org.polypheny.db.adapter.annotations.AdapterProperties;
import org.polypheny.db.adapter.annotations.AdapterSettingDirectory;
import org.polypheny.db.adapter.annotations.AdapterSettingInteger;
import org.polypheny.db.adapter.annotations.AdapterSettingList;
import org.polypheny.db.adapter.annotations.AdapterSettingString;
import org.polypheny.db.adapter.csv.CsvTable.Flavor;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.catalogs.RelAdapterCatalog;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.allocation.AllocationTableWrapper;
import org.polypheny.db.catalog.entity.logical.LogicalTableWrapper;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalTable;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.information.InformationGroup;
import org.polypheny.db.information.InformationTable;
import org.polypheny.db.prepare.Context;
import org.polypheny.db.schemaDiscovery.AbstractNode;
import org.polypheny.db.schemaDiscovery.AttributeNode;
import org.polypheny.db.schemaDiscovery.MetadataProvider;
import org.polypheny.db.schemaDiscovery.Node;
import org.polypheny.db.transaction.PolyXid;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Source;
import org.polypheny.db.util.Sources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
@AdapterProperties(
        name = "CSV",
        description = "An adapter for querying CSV files. The location of the directory containing the CSV files can be specified. Currently, this adapter only supports read operations.",
        usedModes = DeployMode.EMBEDDED,
        defaultMode = DeployMode.EMBEDDED)
@AdapterSettingList(name = "method", options = { "upload", "link" }, defaultValue = "upload", description = "If the supplied file(s) should be uploaded or a link to the local filesystem is used (sufficient permissions are required).", position = 1)
@AdapterSettingDirectory(subOf = "method_upload", name = "directory", defaultValue = "classpath://hr", description = "You can upload one or multiple .csv or .csv.gz files.", position = 2)
@AdapterSettingString(subOf = "method_link", defaultValue = "classpath://hr", name = "directoryName", description = "You can select a path to a folder or specific .csv or .csv.gz files.", position = 2)
@AdapterSettingInteger(name = "maxStringLength", defaultValue = 255, position = 3,
        description = "Which length (number of characters including whitespace) should be used for the varchar columns. Make sure this is equal or larger than the longest string in any of the columns.")
public class CsvSource extends DataSource<RelAdapterCatalog> implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger( CsvSource.class );
    @Delegate(excludes = Excludes.class)
    private final RelationalScanDelegate delegate;
    private final ConnectionMethod connectionMethod;

    private URL csvDir;
    @Getter
    private CsvSchema currentNamespace;
    private final int maxStringLength;
    private Map<String, List<ExportedColumn>> exportedColumnCache;

    private AbstractNode metadataRoot;
    private Map<String, List<Map<String, Object>>> previewByTable = new LinkedHashMap<>();


    public CsvSource( final long storeId, final String uniqueName, final Map<String, String> settings, final DeployMode mode ) {
        super( storeId, uniqueName, settings, mode, true, new RelAdapterCatalog( storeId ) );

        this.connectionMethod = settings.containsKey( "method" ) ? ConnectionMethod.from( settings.get( "method" ).toUpperCase() ) : ConnectionMethod.UPLOAD;

        setCsvDir( settings );

        // Validate maxStringLength setting
        {
            maxStringLength = Integer.parseInt( settings.get( "maxStringLength" ) );
        }
        if ( maxStringLength < 1 ) {
            throw new GenericRuntimeException( "Invalid value for maxStringLength: " + maxStringLength );
        }

        addInformationExportedColumns();
        enableInformationPage();

        this.delegate = new RelationalScanDelegate( this, adapterCatalog );
    }


    @Override
    public void updateNamespace( String name, long id ) {
        currentNamespace = new CsvSchema( id, adapterId, csvDir, Flavor.FILTERABLE );
    }


    @Override
    public List<PhysicalEntity> createTable( Context context, LogicalTableWrapper logical, AllocationTableWrapper allocation ) {
        PhysicalTable table = adapterCatalog.createTable(
                logical.table.getNamespaceName(),
                logical.table.name,
                logical.columns.stream().collect( Collectors.toMap( c -> c.id, c -> c.name ) ),
                logical.table,
                logical.columns.stream().collect( Collectors.toMap( t -> t.id, t -> t ) ),
                logical.pkIds, allocation );

        CsvTable physical = currentNamespace.createCsvTable( table.id, table, this );

        adapterCatalog.replacePhysical( physical );

        return List.of( physical );
    }


    @Override
    public void restoreTable( AllocationTable alloc, List<PhysicalEntity> entities, Context context ) {
        PhysicalEntity table = entities.get( 0 );
        updateNamespace( table.namespaceName, table.namespaceId );
        adapterCatalog.addPhysical( alloc, currentNamespace.createCsvTable( table.id, table.unwrapOrThrow( PhysicalTable.class ), this ) );
    }


    private void setCsvDir( Map<String, String> settings ) {
        String dir = settings.get( "directory" );
        if ( connectionMethod == ConnectionMethod.LINK ) {
            dir = settings.get( "directoryName" );
        }

        if ( dir.startsWith( "classpath://" ) ) {
            csvDir = this.getClass().getClassLoader().getResource( dir.replace( "classpath://", "" ) + "/" );
        } else {
            try {
                csvDir = new File( dir ).toURI().toURL();
            } catch ( MalformedURLException e ) {
                throw new GenericRuntimeException( e );
            }
        }
    }


    @Override
    public void truncate( Context context, long allocId ) {
        throw new GenericRuntimeException( "CSV adapter does not support truncate" );
    }


    @Override
    public Map<String, List<ExportedColumn>> getExportedColumns() {
        if ( connectionMethod == ConnectionMethod.UPLOAD && exportedColumnCache != null ) {
            // if we upload, file will not be changed, and we can cache the columns information, if "link" is used this is not advised
            return exportedColumnCache;
        }
        Map<String, List<ExportedColumn>> exportedColumnCache = new HashMap<>();
        Set<String> fileNames;
        if ( csvDir.getProtocol().equals( "jar" ) ) {

            List<AllocationEntity> placements = Catalog.snapshot().alloc().getEntitiesOnAdapter( getAdapterId() ).orElse( List.of() );
            fileNames = new HashSet<>();
            for ( AllocationEntity ccp : placements ) {
                fileNames.add( ccp.getNamespaceName() );
            }
        } else if ( Sources.of( csvDir ).file().isFile() ) {
            // single files
            fileNames = Set.of( csvDir.getPath() );
        } else {
            // multiple files
            File[] files = Sources.of( csvDir )
                    .file()
                    .listFiles( ( d, name ) -> name.endsWith( ".csv" ) || name.endsWith( ".csv.gz" ) );
            if ( files == null ) {
                throw new GenericRuntimeException( "No .csv files where found." );
            }
            fileNames = Arrays.stream( files )
                    .sequential()
                    .map( File::getName )
                    .collect( Collectors.toSet() );
        }
        for ( String fileName : fileNames ) {
            String physicalTableName = computePhysicalEntityName( fileName );

            List<ExportedColumn> list = new ArrayList<>();
            int position = 1;
            try {
                Source source = Sources.of( new URL( csvDir, fileName ) );
                BufferedReader reader = new BufferedReader( source.reader() );
                String firstLine = reader.readLine();
                for ( String col : firstLine.split( "," ) ) {
                    String[] colSplit = col.split( ":" );
                    String name = colSplit[0]
                            .toLowerCase()
                            .trim()
                            .replaceAll( "[^a-z0-9_]+", "" );
                    String typeStr = "string";
                    if ( colSplit.length > 1 ) {
                        typeStr = colSplit[1].toLowerCase().trim();
                    }

                    PolyType type;
                    Integer length = null;
                    switch ( typeStr.toLowerCase() ) {
                        case "int":
                            type = PolyType.INTEGER;
                            break;
                        case "string":
                            type = PolyType.VARCHAR;
                            length = maxStringLength;
                            break;
                        case "boolean":
                            type = PolyType.BOOLEAN;
                            break;
                        case "long":
                            type = PolyType.BIGINT;
                            break;
                        case "float":
                            type = PolyType.REAL;
                            break;
                        case "double":
                            type = PolyType.DOUBLE;
                            break;
                        case "date":
                            type = PolyType.DATE;
                            break;
                        case "time":
                            type = PolyType.TIME;
                            length = 0;
                            break;
                        case "timestamp":
                            type = PolyType.TIMESTAMP;
                            length = 0;
                            break;
                        default:
                            throw new GenericRuntimeException( "Unknown type: " + typeStr.toLowerCase() );
                    }
                    list.add( new ExportedColumn(
                            name,
                            type,
                            null,
                            length,
                            null,
                            null,
                            null,
                            false,
                            fileName,
                            physicalTableName,
                            name,
                            position,
                            position == 1 ) );
                    position++;
                }
            } catch ( IOException e ) {
                throw new GenericRuntimeException( e );
            }

            exportedColumnCache.put( physicalTableName, list );
        }
        this.exportedColumnCache = exportedColumnCache;
        return exportedColumnCache;
    }


    private static String computePhysicalEntityName( String fileName ) {
        // Compute physical table name
        String physicalTableName = fileName.toLowerCase();
        // remove gz
        if ( physicalTableName.endsWith( ".gz" ) ) {
            physicalTableName = physicalTableName.substring( 0, physicalTableName.length() - ".gz".length() );
        }
        // use only filename
        if ( physicalTableName.contains( "/" ) ) {
            String[] splits = physicalTableName.split( "/" );
            physicalTableName = splits[splits.length - 2];
        }

        if ( physicalTableName.contains( "\\" ) ) {
            String[] splits = physicalTableName.split( "\\\\" );
            physicalTableName = splits[splits.length - 2];
        }

        return physicalTableName
                .substring( 0, physicalTableName.length() - ".csv".length() )
                .trim()
                .replaceAll( "[^a-z0-9_]+", "" );
    }


    @Override
    public boolean prepare( PolyXid xid ) {
        log.debug( "CSV Store does not support prepare()." );
        return true;
    }


    @Override
    public void commit( PolyXid xid ) {
        log.debug( "CSV Store does not support commit()." );
    }


    @Override
    public void rollback( PolyXid xid ) {
        log.debug( "CSV Store does not support rollback()." );
    }


    @Override
    public void shutdown() {
        removeInformationPage();
    }


    @Override
    protected void reloadSettings( List<String> updatedSettings ) {
        if ( updatedSettings.contains( "directory" ) ) {
            setCsvDir( settings );
        }
    }


    private void addInformationExportedColumns() {
        for ( Map.Entry<String, List<ExportedColumn>> entry : getExportedColumns().entrySet() ) {
            InformationGroup group = new InformationGroup( informationPage, entry.getValue().get( 0 ).physicalSchemaName );
            informationGroups.add( group );

            InformationTable table = new InformationTable(
                    group,
                    Arrays.asList( "Position", "Column Name", "Type", "Nullable", "Filename", "Primary" ) );
            for ( ExportedColumn exportedColumn : entry.getValue() ) {
                table.addRow(
                        exportedColumn.physicalPosition,
                        exportedColumn.name,
                        exportedColumn.getDisplayType(),
                        exportedColumn.nullable ? "✔" : "",
                        exportedColumn.physicalSchemaName,
                        exportedColumn.primary ? "✔" : ""
                );
            }
            informationElements.add( table );
        }
    }


    protected void updateNativePhysical( long allocId ) {
        PhysicalTable table = adapterCatalog.fromAllocation( allocId );
        adapterCatalog.replacePhysical( this.currentNamespace.createCsvTable( table.id, table, this ) );
    }


    @Override
    public void renameLogicalColumn( long id, String newColumnName ) {
        adapterCatalog.renameLogicalColumn( id, newColumnName );
        adapterCatalog.fields.values().stream().filter( c -> c.id == id ).forEach( c -> updateNativePhysical( c.allocId ) );
    }


    @Override
    public AbstractNode fetchMetadataTree() {
        File csvFile = new File( "C:/Users/roman/Desktop/Dateieins.csv" );
        String tableName = csvFile.getName();
        AbstractNode rootNode = new Node( "csv", tableName );

        try ( BufferedReader reader = new BufferedReader( new FileReader( csvFile ) ) ) {
            String headerLine = reader.readLine();
            if ( headerLine == null ) {
                throw new RuntimeException( "No header line found" );
            }

            String[] rawColumns = headerLine.split( "," );
            for ( String colRaw : rawColumns ) {
                String[] split = colRaw.split( ":" );
                String name = split[0].trim().replaceAll( "[^a-zA-Z0-9_]", "" );
                String type = split.length > 1 ? split[1].trim() : "string";

                AbstractNode columnNode = new AttributeNode( "column", name );
                columnNode.addProperty( "type", mapCsvType( type ) );
                columnNode.addProperty( "nullable", true );

                rootNode.addChild( columnNode );
            }
            String fqName = csvFile.getName();
            List<Map<String, Object>> preview = fetchPreview( null, fqName, 10 );
            this.previewByTable.put( fqName, preview );
        } catch ( IOException e ) {
            throw new RuntimeException( "Failed to parse metadata of CSV source: " + e );
        }
        this.metadataRoot = rootNode;
        return this.metadataRoot;

    }


    private String mapCsvType( String rawType ) {
        switch ( rawType ) {
            case "int":
            case "integer":
                return "INTEGER";
            case "bool":
            case "boolean":
                return "BOOLEAN";
            case "long":
                return "BIGINT";
            case "float":
                return "REAL";
            case "double":
                return "DOUBLE";
            case "date":
                return "DATE";
            case "time":
                return "TIME";
            case "timestamp":
                return "TIMESTAMP";
            case "string":
            default:
                return "VARCHAR";
        }
    }


    @Override
    public List<Map<String, Object>> fetchPreview( Connection conn, String fqName, int limit ) {
        File csvFile = new File( "C:/Users/roman/Desktop/Dateieins.csv" );
        List<Map<String, Object>> rows = new ArrayList<>();

        try ( BufferedReader reader = new BufferedReader( new FileReader( csvFile ) ) ) {
            String headerLine = reader.readLine();
            if ( headerLine == null ) {
                return List.of();
            }

            String[] headerParts = headerLine.split( "," );
            List<String> colNames = new ArrayList<>();

            for ( String raw : headerParts ) {
                String[] split = raw.split( ":" );
                String colName = split[0].trim();
                colNames.add( colName );
            }

            String line;
            int count = 0;
            while ( (line = reader.readLine()) != null && count < limit ) {
                String[] values = line.split( ",", -1 );
                Map<String, Object> row = new LinkedHashMap<>();

                for ( int i = 0; i < colNames.size(); i++ ) {
                    String value = i < values.length ? values[i].trim() : null;
                    row.put( colNames.get( i ), value );
                }

                rows.add( row );
                count++;
            }

        } catch ( IOException e ) {
            throw new RuntimeException( "Failed to read CSV preview: " + fqName, e );
        }

        return rows;
    }


    @Override
    public void markSelectedAttributes(List<String> selectedPaths) {
        if (this.metadataRoot == null) {
            log.warn("⚠️ Kein Metadatenbaum vorhanden – kann Attribute nicht markieren.");
            return;
        }

        for (String path : selectedPaths) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot == -1 || lastDot == path.length() - 1) {
                log.warn("⚠️ Kein gültiger Attribut-Pfad: " + path);
                continue;
            }

            String columnName = path.substring(lastDot + 1);
            String normalizedColumnName = columnName.replaceAll("[^a-zA-Z0-9_]", "");

            Optional<AbstractNode> attrOpt = metadataRoot.getChildren().stream()
                    .filter(child -> child instanceof AttributeNode
                            && child.getName().equals(normalizedColumnName))
                    .findFirst();

            if (attrOpt.isPresent()) {
                ((AttributeNode) attrOpt.get()).setSelected(true);
                log.info("✅ Attribut gesetzt: " + path);
            } else {
                log.warn("❌ Attribut nicht gefunden: " + normalizedColumnName + " im Pfad: " + path);
            }
        }
    }



    @Override
    public void printTree( AbstractNode node, int depth ) {
        if ( node == null ) {
            node = this.metadataRoot;
        }
        System.out.println( "  ".repeat( depth ) + node.getType() + ": " + node.getName() );
        for ( Map.Entry<String, Object> entry : node.getProperties().entrySet() ) {
            System.out.println( "  ".repeat( depth + 1 ) + "- " + entry.getKey() + ": " + entry.getValue() );
        }
        for ( AbstractNode child : node.getChildren() ) {
            printTree( child, depth + 1 );
        }

    }


    @Override
    public void setRoot( AbstractNode root ) {
        this.metadataRoot = root;
    }


    @Override
    public Object getPreview() {
        return this.previewByTable;
    }


    @SuppressWarnings("unused")
    private interface Excludes {

        void renameLogicalColumn( long id, String newColumnName );

        void refreshTable( long allocId );

        void createTable( Context context, LogicalTableWrapper logical, AllocationTableWrapper allocation );

        void restoreTable( AllocationTable alloc, List<PhysicalEntity> entities );

    }

}
