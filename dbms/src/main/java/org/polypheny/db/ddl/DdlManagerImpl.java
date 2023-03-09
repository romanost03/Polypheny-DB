/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.ddl;


import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.polypheny.db.StatisticsManager;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.adapter.DataSource;
import org.polypheny.db.adapter.DataSource.ExportedColumn;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.DataStore.AvailableIndexMethod;
import org.polypheny.db.adapter.index.IndexManager;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.BiAlg;
import org.polypheny.db.algebra.SingleAlg;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.logical.relational.LogicalRelScan;
import org.polypheny.db.algebra.logical.relational.LogicalRelViewScan;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.CatalogAdapter.AdapterType;
import org.polypheny.db.catalog.entity.CatalogCollectionMapping;
import org.polypheny.db.catalog.entity.CatalogCollectionPlacement;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogConstraint;
import org.polypheny.db.catalog.entity.CatalogDataPlacement;
import org.polypheny.db.catalog.entity.CatalogForeignKey;
import org.polypheny.db.catalog.entity.CatalogGraphPlacement;
import org.polypheny.db.catalog.entity.CatalogIndex;
import org.polypheny.db.catalog.entity.CatalogKey;
import org.polypheny.db.catalog.entity.CatalogMaterializedView;
import org.polypheny.db.catalog.entity.CatalogPartitionGroup;
import org.polypheny.db.catalog.entity.CatalogPrimaryKey;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.entity.CatalogView;
import org.polypheny.db.catalog.entity.LogicalNamespace;
import org.polypheny.db.catalog.entity.MaterializedCriteria;
import org.polypheny.db.catalog.entity.MaterializedCriteria.CriteriaType;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.logical.LogicalCollection;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalGraph;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.exceptions.ColumnAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.EntityAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.NamespaceAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.UnknownAdapterException;
import org.polypheny.db.catalog.exceptions.UnknownCollationException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.catalog.exceptions.UnknownConstraintException;
import org.polypheny.db.catalog.exceptions.UnknownForeignKeyException;
import org.polypheny.db.catalog.exceptions.UnknownGraphException;
import org.polypheny.db.catalog.exceptions.UnknownIndexException;
import org.polypheny.db.catalog.exceptions.UnknownKeyException;
import org.polypheny.db.catalog.exceptions.UnknownPartitionTypeException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownTableException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.catalog.logistic.Collation;
import org.polypheny.db.catalog.logistic.ConstraintType;
import org.polypheny.db.catalog.logistic.DataPlacementRole;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.catalog.logistic.ForeignKeyOption;
import org.polypheny.db.catalog.logistic.IndexType;
import org.polypheny.db.catalog.logistic.NameGenerator;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.catalog.logistic.PartitionType;
import org.polypheny.db.catalog.logistic.PlacementType;
import org.polypheny.db.catalog.snapshot.LogicalRelSnapshot;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.ddl.exception.AlterSourceException;
import org.polypheny.db.ddl.exception.ColumnNotExistsException;
import org.polypheny.db.ddl.exception.DdlOnSourceException;
import org.polypheny.db.ddl.exception.IndexExistsException;
import org.polypheny.db.ddl.exception.IndexPreventsRemovalException;
import org.polypheny.db.ddl.exception.LastPlacementException;
import org.polypheny.db.ddl.exception.MissingColumnPlacementException;
import org.polypheny.db.ddl.exception.NotMaterializedViewException;
import org.polypheny.db.ddl.exception.NotNullAndDefaultValueException;
import org.polypheny.db.ddl.exception.NotViewException;
import org.polypheny.db.ddl.exception.PartitionGroupNamesNotUniqueException;
import org.polypheny.db.ddl.exception.PlacementAlreadyExistsException;
import org.polypheny.db.ddl.exception.PlacementIsPrimaryException;
import org.polypheny.db.ddl.exception.PlacementNotExistsException;
import org.polypheny.db.ddl.exception.SchemaNotExistException;
import org.polypheny.db.ddl.exception.UnknownIndexMethodException;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.monitoring.events.DdlEvent;
import org.polypheny.db.monitoring.events.StatementEvent;
import org.polypheny.db.partition.PartitionManager;
import org.polypheny.db.partition.PartitionManagerFactory;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.partition.properties.TemperaturePartitionProperty;
import org.polypheny.db.partition.properties.TemperaturePartitionProperty.PartitionCostIndication;
import org.polypheny.db.partition.raw.RawTemperaturePartitionInformation;
import org.polypheny.db.processing.DataMigrator;
import org.polypheny.db.routing.RoutingManager;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.runtime.PolyphenyDbException;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.type.ArrayType;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.view.MaterializedViewManager;


@Slf4j
public class DdlManagerImpl extends DdlManager {

    private final Catalog catalog;


    public DdlManagerImpl( Catalog catalog ) {
        this.catalog = catalog;
    }


    private void checkIfDdlPossible( EntityType entityType ) throws DdlOnSourceException {
        if ( entityType == EntityType.SOURCE ) {
            throw new DdlOnSourceException();
        }
    }


    private void checkViewDependencies( LogicalTable catalogTable ) {
        if ( catalogTable.connectedViews.size() > 0 ) {
            List<String> views = new ArrayList<>();
            for ( Long id : catalogTable.connectedViews ) {
                views.add( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getTable( id ).name );
            }
            throw new PolyphenyDbException( "Cannot alter table because of underlying View " + views.stream().map( String::valueOf ).collect( Collectors.joining( (", ") ) ) );
        }
    }


    private void addDefaultValue( long namespaceId, String defaultValue, long addedColumnId ) {
        if ( defaultValue != null ) {
            // TODO: String is only a temporal solution for default values
            String v = defaultValue;
            if ( v.startsWith( "'" ) ) {
                v = v.substring( 1, v.length() - 1 );
            }
            catalog.getLogicalRel( namespaceId ).setDefaultValue( addedColumnId, PolyType.VARCHAR, v );
        }
    }


    protected DataStore getDataStoreInstance( long storeId ) throws DdlOnSourceException {
        Adapter adapterInstance = AdapterManager.getInstance().getAdapter( storeId );
        if ( adapterInstance == null ) {
            throw new RuntimeException( "Unknown store id: " + storeId );
        }
        // Make sure it is a data store instance
        if ( adapterInstance instanceof DataStore ) {
            return (DataStore) adapterInstance;
        } else if ( adapterInstance instanceof DataSource ) {
            throw new DdlOnSourceException();
        } else {
            throw new RuntimeException( "Unknown kind of adapter: " + adapterInstance.getClass().getName() );
        }
    }


    private LogicalColumn getCatalogColumn( long namespaceId, long tableId, String columnName ) throws ColumnNotExistsException {
        try {
            return catalog.getSnapshot().getRelSnapshot( namespaceId ).getColumn( tableId, columnName );
        } catch ( UnknownColumnException e ) {
            throw new ColumnNotExistsException( tableId, columnName );
        }
    }


    @Override
    public long createNamespace( String name, NamespaceType type, boolean ifNotExists, boolean replace ) throws NamespaceAlreadyExistsException {
        name = name.toLowerCase();
        // Check if there is already a schema with this name
        if ( catalog.getSnapshot().checkIfExistsNamespace( name ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a schema with this name because "IF NOT EXISTS" was specified
                return catalog.getSnapshot().getNamespace( name ).id;
            } else if ( replace ) {
                throw new RuntimeException( "Replacing namespace is not yet supported." );
            } else {
                throw new NamespaceAlreadyExistsException();
            }
        } else {
            return catalog.addNamespace( name, type, false );
        }
    }


    @Override
    public void addAdapter( String uniqueName, String adapterName, AdapterType adapterType, Map<String, String> config ) {
        uniqueName = uniqueName.toLowerCase();
        Adapter adapter = AdapterManager.getInstance().addAdapter( adapterName, uniqueName, adapterType, config );
        if ( adapter instanceof DataSource ) {
            handleSource( (DataSource) adapter );
        }
    }


    private void handleSource( DataSource adapter ) {
        long defaultNamespaceId = 1;
        Map<String, List<ExportedColumn>> exportedColumns;
        try {
            exportedColumns = adapter.getExportedColumns();
        } catch ( Exception e ) {
            AdapterManager.getInstance().removeAdapter( adapter.getAdapterId() );
            throw new RuntimeException( "Could not deploy adapter", e );
        }
        // Create table, columns etc.
        for ( Map.Entry<String, List<ExportedColumn>> entry : exportedColumns.entrySet() ) {
            // Make sure the table name is unique
            String tableName = entry.getKey();
            if ( catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).checkIfExistsEntity( tableName ) ) { // apparently we put them all into 1?
                int i = 0;
                while ( catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).checkIfExistsEntity( tableName + i ) ) {
                    i++;
                }
                tableName += i;
            }

            long tableId = catalog.getLogicalRel( defaultNamespaceId ).addTable( tableName, EntityType.SOURCE, !(adapter).isDataReadOnly() );
            List<Long> primaryKeyColIds = new ArrayList<>();
            int colPos = 1;
            String physicalSchemaName = null;
            String physicalTableName = null;
            for ( ExportedColumn exportedColumn : entry.getValue() ) {
                long columnId = catalog.getLogicalRel( defaultNamespaceId ).addColumn(
                        exportedColumn.name,
                        tableId,
                        colPos++,
                        exportedColumn.type,
                        exportedColumn.collectionsType,
                        exportedColumn.length,
                        exportedColumn.scale,
                        exportedColumn.dimension,
                        exportedColumn.cardinality,
                        exportedColumn.nullable,
                        Collation.getDefaultCollation() );
                catalog.getAllocRel( defaultNamespaceId ).addColumnPlacement( catalog.getLogicalEntity( tableId ).unwrap( LogicalTable.class ),
                        adapter.getAdapterId(),
                        columnId,
                        PlacementType.STATIC,
                        exportedColumn.physicalSchemaName,
                        exportedColumn.physicalTableName, exportedColumn.physicalColumnName, exportedColumn.physicalPosition ); // Not a valid partitionGroupID --> placeholder
                catalog.getAllocRel( defaultNamespaceId ).updateColumnPlacementPhysicalPosition( adapter.getAdapterId(), columnId, exportedColumn.physicalPosition );
                if ( exportedColumn.primary ) {
                    primaryKeyColIds.add( columnId );
                }
                if ( physicalSchemaName == null ) {
                    physicalSchemaName = exportedColumn.physicalSchemaName;
                }
                if ( physicalTableName == null ) {
                    physicalTableName = exportedColumn.physicalTableName;
                }
            }
            try {
                catalog.getLogicalRel( defaultNamespaceId ).addPrimaryKey( tableId, primaryKeyColIds );
                LogicalTable catalogTable = catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).getTable( tableId );
                catalog.getAllocRel( defaultNamespaceId )
                        .addPartitionPlacement(
                                catalogTable.namespaceId,
                                adapter.getAdapterId(),
                                catalogTable.id,
                                catalogTable.partitionProperty.partitionIds.get( 0 ),
                                PlacementType.AUTOMATIC,
                                DataPlacementRole.UPTODATE );
            } catch ( GenericCatalogException e ) {
                throw new RuntimeException( "Exception while adding primary key" );
            }
        }

    }


    @Override
    public void dropAdapter( String name, Statement statement ) throws UnknownAdapterException {
        long defaultNamespaceId = 1;
        if ( name.startsWith( "'" ) ) {
            name = name.substring( 1 );
        }
        if ( name.endsWith( "'" ) ) {
            name = StringUtils.chop( name );
        }

        CatalogAdapter catalogAdapter = catalog.getSnapshot().getAdapter( name );
        if ( catalogAdapter.type == AdapterType.SOURCE ) {
            // Remove collection
            Set<Long> collectionsToDrop = new HashSet<>();
            for ( CatalogCollectionPlacement collectionPlacement : catalog.getAllocDoc( defaultNamespaceId ).getCollectionPlacementsByAdapter( catalogAdapter.id ) ) {
                collectionsToDrop.add( collectionPlacement.collectionId );
            }

            for ( long id : collectionsToDrop ) {
                LogicalCollection collection = catalog.getSnapshot().getDocSnapshot( 1 ).getCollection( id );

                // Make sure that there is only one adapter
                if ( collection.placements.size() != 1 ) {
                    throw new RuntimeException( "The data source contains collections with more than one placement. This should not happen!" );
                }

                dropCollection( collection, statement );

            }

            // Remove table
            Set<Long> tablesToDrop = new HashSet<>();
            for ( CatalogColumnPlacement ccp : catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapter( catalogAdapter.id ) ) {
                tablesToDrop.add( ccp.tableId );
            }

            for ( Long id : tablesToDrop ) {
                if ( catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).getTable( id ).entityType != EntityType.MATERIALIZED_VIEW ) {
                    tablesToDrop.add( id );
                }
            }

            // Remove foreign keys
            for ( Long tableId : tablesToDrop ) {
                for ( CatalogForeignKey fk : catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).getForeignKeys( tableId ) ) {
                    try {
                        catalog.getLogicalRel( defaultNamespaceId ).deleteForeignKey( fk.id );
                    } catch ( GenericCatalogException e ) {
                        throw new PolyphenyDbContextException( "Exception while dropping foreign key", e );
                    }
                }
            }
            // Drop tables
            for ( Long tableId : tablesToDrop ) {
                LogicalTable table = catalog.getSnapshot().getRelSnapshot( defaultNamespaceId ).getTable( tableId );

                // Make sure that there is only one adapter
                if ( table.dataPlacements.size() != 1 ) {
                    throw new RuntimeException( "The data source contains tables with more than one placement. This should not happen!" );
                }

                // Make sure table is of type source
                if ( table.entityType != EntityType.SOURCE ) {
                    throw new RuntimeException( "Trying to drop a table located on a data source which is not of table type SOURCE. This should not happen!" );
                }

                // Delete column placement in catalog
                for ( LogicalColumn column : table.columns ) {
                    if ( catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( catalogAdapter.id, column.id ) ) {
                        catalog.getAllocRel( defaultNamespaceId ).deleteColumnPlacement( catalogAdapter.id, column.id, false );
                    }
                }

                // Remove primary keys
                try {
                    catalog.getLogicalRel( defaultNamespaceId ).deletePrimaryKey( table.id );
                } catch ( GenericCatalogException e ) {
                    throw new PolyphenyDbContextException( "Exception while dropping primary key", e );
                }

                // Delete columns
                for ( LogicalColumn column : table.columns ) {
                    catalog.getLogicalRel( defaultNamespaceId ).deleteColumn( column.id );
                }

                // Delete the table
                catalog.getLogicalRel( defaultNamespaceId ).deleteTable( table.id );
            }

            // Reset plan cache implementation cache & routing cache
            statement.getQueryProcessor().resetCaches();
        }
        AdapterManager.getInstance().removeAdapter( catalogAdapter.id );
    }


    @Override
    public void renameSchema( String newName, String oldName ) throws NamespaceAlreadyExistsException, UnknownSchemaException {
        newName = newName.toLowerCase();
        if ( catalog.getSnapshot().checkIfExistsNamespace( newName ) ) {
            throw new NamespaceAlreadyExistsException();
        }
        LogicalNamespace logicalNamespace = catalog.getSnapshot().getNamespace( oldName );
        catalog.renameNamespace( logicalNamespace.id, newName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateSchemaName( logicalNamespace, newName );
    }


    @Override
    public void addColumnToSourceTable( LogicalTable catalogTable, String columnPhysicalName, String columnLogicalName, String beforeColumnName, String afterColumnName, String defaultValue, Statement statement ) throws ColumnAlreadyExistsException, DdlOnSourceException, ColumnNotExistsException {

        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsColumn( catalogTable.id, columnLogicalName ) ) {
            throw new ColumnAlreadyExistsException( columnLogicalName, catalogTable.name );
        }

        LogicalColumn beforeColumn = beforeColumnName == null ? null : getCatalogColumn( catalogTable.namespaceId, catalogTable.id, beforeColumnName );
        LogicalColumn afterColumn = afterColumnName == null ? null : getCatalogColumn( catalogTable.namespaceId, catalogTable.id, afterColumnName );

        // Make sure that the table is of table type SOURCE
        if ( catalogTable.entityType != EntityType.SOURCE ) {
            throw new RuntimeException( "Illegal operation on table of type " + catalogTable.entityType );
        }

        // Make sure there is only one adapter
        if ( catalog.getSnapshot().getAllocSnapshot().getColumnPlacements( catalogTable.columns.get( 0 ).id ).size() != 1 ) {
            throw new RuntimeException( "The table has an unexpected number of placements!" );
        }

        long adapterId = catalog.getSnapshot().getAllocSnapshot().getAllocationsFromLogical( catalogTable.id ).get( 0 ).adapterId;
        DataSource dataSource = (DataSource) AdapterManager.getInstance().getAdapter( adapterId );

        String physicalTableName = catalog.getSnapshot().getAllocSnapshot().getPartitionPlacement( adapterId, catalogTable.partitionProperty.partitionIds.get( 0 ) ).physicalTableName;
        List<ExportedColumn> exportedColumns = dataSource.getExportedColumns().get( physicalTableName );

        // Check if physicalColumnName is valid
        ExportedColumn exportedColumn = null;
        for ( ExportedColumn ec : exportedColumns ) {
            if ( ec.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                exportedColumn = ec;
            }
        }
        if ( exportedColumn == null ) {
            throw new RuntimeException( "Invalid physical column name '" + columnPhysicalName + "'!" );
        }

        // Make sure this physical column has not already been added to this table
        for ( CatalogColumnPlacement ccp : catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapterPerTable( adapterId, catalogTable.id ) ) {
            if ( ccp.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                throw new RuntimeException( "The physical column '" + columnPhysicalName + "' has already been added to this table!" );
            }
        }

        int position = updateAdjacentPositions( catalogTable, beforeColumn, afterColumn );

        long columnId = catalog.getLogicalRel( catalogTable.namespaceId ).addColumn(
                columnLogicalName,
                catalogTable.id,
                position,
                exportedColumn.type,
                exportedColumn.collectionsType,
                exportedColumn.length,
                exportedColumn.scale,
                exportedColumn.dimension,
                exportedColumn.cardinality,
                exportedColumn.nullable,
                Collation.getDefaultCollation()
        );

        // Add default value
        addDefaultValue( catalogTable.namespaceId, defaultValue, columnId );
        LogicalColumn addedColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( columnId );

        // Add column placement
        catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                adapterId,
                addedColumn.id,
                PlacementType.STATIC,
                exportedColumn.physicalSchemaName,
                exportedColumn.physicalTableName, exportedColumn.physicalColumnName, position );//Not a valid partitionID --> placeholder

        // Set column position
        catalog.getAllocRel( catalogTable.namespaceId ).updateColumnPlacementPhysicalPosition( adapterId, columnId, exportedColumn.physicalPosition );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private int updateAdjacentPositions( LogicalTable catalogTable, LogicalColumn beforeColumn, LogicalColumn afterColumn ) {
        List<LogicalColumn> columns = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumns( catalogTable.id );
        int position = columns.size() + 1;
        if ( beforeColumn != null || afterColumn != null ) {
            if ( beforeColumn != null ) {
                position = beforeColumn.position;
            } else {
                position = afterColumn.position + 1;
            }
            // Update position of the other columns
            for ( int i = columns.size(); i >= position; i-- ) {
                catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( columns.get( i - 1 ).id, i + 1 );
            }
        }
        return position;
    }


    @Override
    public void addColumn( String columnName, LogicalTable catalogTable, String beforeColumnName, String afterColumnName, ColumnTypeInformation type, boolean nullable, String defaultValue, Statement statement ) throws NotNullAndDefaultValueException, ColumnAlreadyExistsException, ColumnNotExistsException {
        columnName = adjustNameIfNeeded( columnName, catalogTable.namespaceId );
        // Check if the column either allows null values or has a default value defined.
        if ( defaultValue == null && !nullable ) {
            throw new NotNullAndDefaultValueException();
        }

        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsColumn( catalogTable.id, columnName ) ) {
            throw new ColumnAlreadyExistsException( columnName, catalogTable.name );
        }
        //
        LogicalColumn beforeColumn = beforeColumnName == null ? null : getCatalogColumn( catalogTable.namespaceId, catalogTable.id, beforeColumnName );
        LogicalColumn afterColumn = afterColumnName == null ? null : getCatalogColumn( catalogTable.namespaceId, catalogTable.id, afterColumnName );

        int position = updateAdjacentPositions( catalogTable, beforeColumn, afterColumn );

        long columnId = catalog.getLogicalRel( catalogTable.namespaceId ).addColumn(
                columnName,
                catalogTable.id,
                position,
                type.type,
                type.collectionType,
                type.precision,
                type.scale,
                type.dimension,
                type.cardinality,
                nullable,
                Collation.getDefaultCollation()
        );

        // Add default value
        addDefaultValue( catalogTable.namespaceId, defaultValue, columnId );
        LogicalColumn addedColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( columnId );

        // Ask router on which stores this column shall be placed
        List<DataStore> stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewColumn( addedColumn );

        // Add column on underlying data stores and insert default value
        for ( DataStore store : stores ) {
            catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                    store.getAdapterId(),
                    addedColumn.id,   // Will be set later
                    PlacementType.AUTOMATIC,   // Will be set later
                    null,   // Will be set later
                    null, null, position );//Not a valid partitionID --> placeholder
            AdapterManager.getInstance().getStore( store.getAdapterId() ).addColumn( statement.getPrepareContext(), catalogTable, addedColumn );
        }

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addForeignKey( LogicalTable catalogTable, LogicalTable refTable, List<String> columnNames, List<String> refColumnNames, String constraintName, ForeignKeyOption onUpdate, ForeignKeyOption onDelete ) throws UnknownColumnException, GenericCatalogException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( catalogTable.id, columnName );
            columnIds.add( logicalColumn.id );
        }
        List<Long> referencesIds = new LinkedList<>();
        for ( String columnName : refColumnNames ) {
            LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( refTable.id, columnName );
            referencesIds.add( logicalColumn.id );
        }
        catalog.getLogicalRel( catalogTable.namespaceId ).addForeignKey( catalogTable.id, columnIds, refTable.id, referencesIds, constraintName, onUpdate, onDelete );
    }


    @Override
    public void addIndex( LogicalTable catalogTable, String indexMethodName, List<String> columnNames, String indexName, boolean isUnique, DataStore location, Statement statement ) throws UnknownColumnException, UnknownIndexMethodException, GenericCatalogException, UnknownTableException, UnknownUserException, UnknownSchemaException, UnknownKeyException, TransactionException, AlterSourceException, IndexExistsException, MissingColumnPlacementException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( catalogTable.id, columnName );
            columnIds.add( logicalColumn.id );
        }

        IndexType type = IndexType.MANUAL;

        // Make sure that this is a table of type TABLE (and not SOURCE)
        if ( catalogTable.entityType != EntityType.ENTITY && catalogTable.entityType != EntityType.MATERIALIZED_VIEW ) {
            throw new RuntimeException( "It is only possible to add an index to a " + catalogTable.entityType.name() );
        }

        // Check if there is already an index with this name for this table
        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsIndex( catalogTable.id, indexName ) ) {
            throw new IndexExistsException();
        }

        if ( location == null ) {
            if ( RuntimeConfig.DEFAULT_INDEX_PLACEMENT_STRATEGY.getEnum() == DefaultIndexPlacementStrategy.POLYPHENY ) { // Polystore Index
                addPolyphenyIndex( catalogTable, indexMethodName, columnNames, indexName, isUnique, statement );
            } else if ( RuntimeConfig.DEFAULT_INDEX_PLACEMENT_STRATEGY.getEnum() == DefaultIndexPlacementStrategy.ONE_DATA_STORE ) {
                if ( indexMethodName != null ) {
                    throw new RuntimeException( "It is not possible to specify a index method if no location has been specified." );
                }
                // Find a store that has all required columns
                for ( CatalogDataPlacement dataPlacement : catalog.getSnapshot().getAllocSnapshot().getDataPlacements( catalogTable.id ) ) {
                    boolean hasAllColumns = true;
                    if ( ((DataStore) AdapterManager.getInstance().getAdapter( dataPlacement.adapterId )).getAvailableIndexMethods().size() > 0 ) {
                        for ( long columnId : columnIds ) {
                            if ( !catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( dataPlacement.adapterId, columnId ) ) {
                                hasAllColumns = false;
                            }
                        }
                        if ( hasAllColumns ) {
                            location = (DataStore) AdapterManager.getInstance().getAdapter( dataPlacement.adapterId );
                            break;
                        }
                    }
                }
                if ( location == null ) {
                    throw new RuntimeException( "Unable to create an index on one of the underlying data stores since there is no data store that supports indexes and has all required columns!" );
                }
                addDataStoreIndex( catalogTable, indexMethodName, indexName, isUnique, location, statement, columnIds, type );
            } else if ( RuntimeConfig.DEFAULT_INDEX_PLACEMENT_STRATEGY.getEnum() == DefaultIndexPlacementStrategy.ALL_DATA_STORES ) {
                if ( indexMethodName != null ) {
                    throw new RuntimeException( "It is not possible to specify a index method if no location has been specified." );
                }
                boolean createdAtLeastOne = false;
                for ( CatalogDataPlacement dataPlacement : catalog.getSnapshot().getAllocSnapshot().getDataPlacements( catalogTable.id ) ) {
                    boolean hasAllColumns = true;
                    if ( ((DataStore) AdapterManager.getInstance().getAdapter( dataPlacement.adapterId )).getAvailableIndexMethods().size() > 0 ) {
                        for ( long columnId : columnIds ) {
                            if ( !catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( dataPlacement.adapterId, columnId ) ) {
                                hasAllColumns = false;
                            }
                        }
                        if ( hasAllColumns ) {
                            DataStore loc = (DataStore) AdapterManager.getInstance().getAdapter( dataPlacement.adapterId );
                            String name = indexName + "_" + loc.getUniqueName();
                            String nameSuffix = "";
                            int counter = 0;
                            while ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsIndex( catalogTable.id, name + nameSuffix ) ) {
                                nameSuffix = counter++ + "";
                            }
                            addDataStoreIndex( catalogTable, indexMethodName, name + nameSuffix, isUnique, loc, statement, columnIds, type );
                            createdAtLeastOne = true;
                        }
                    }
                }
                if ( !createdAtLeastOne ) {
                    throw new RuntimeException( "Unable to create an index on one of the underlying data stores since there is no data store that supports indexes and has all required columns!" );
                }
            }
        } else { // Store Index
            addDataStoreIndex( catalogTable, indexMethodName, indexName, isUnique, location, statement, columnIds, type );
        }
    }


    private void addDataStoreIndex( LogicalTable catalogTable, String indexMethodName, String indexName, boolean isUnique, DataStore location, Statement statement, List<Long> columnIds, IndexType type ) throws MissingColumnPlacementException, UnknownIndexMethodException, GenericCatalogException {
        // Check if all required columns are present on this store
        for ( long columnId : columnIds ) {
            if ( !catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( location.getAdapterId(), columnId ) ) {
                throw new MissingColumnPlacementException( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( columnId ).name );
            }
        }

        String method;
        String methodDisplayName;
        if ( indexMethodName != null ) {
            AvailableIndexMethod aim = null;
            for ( AvailableIndexMethod availableIndexMethod : location.getAvailableIndexMethods() ) {
                if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                    aim = availableIndexMethod;
                }
            }
            if ( aim == null ) {
                throw new UnknownIndexMethodException();
            }
            method = aim.name;
            methodDisplayName = aim.displayName;
        } else {
            method = location.getDefaultIndexMethod().name;
            methodDisplayName = location.getDefaultIndexMethod().displayName;
        }

        long indexId = catalog.getLogicalRel( catalogTable.namespaceId ).addIndex(
                catalogTable.id,
                columnIds,
                isUnique,
                method,
                methodDisplayName,
                location.getAdapterId(),
                type,
                indexName );

        location.addIndex(
                statement.getPrepareContext(),
                catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndex( indexId ),
                catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( location.getAdapterId(), catalogTable.id ) );
    }


    public void addPolyphenyIndex( LogicalTable catalogTable, String indexMethodName, List<String> columnNames, String indexName, boolean isUnique, Statement statement ) throws UnknownColumnException, UnknownIndexMethodException, GenericCatalogException, UnknownTableException, UnknownUserException, UnknownSchemaException, UnknownKeyException, TransactionException, AlterSourceException, IndexExistsException, MissingColumnPlacementException {
        indexName = indexName.toLowerCase();
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( catalogTable.id, columnName );
            columnIds.add( logicalColumn.id );
        }

        IndexType type = IndexType.MANUAL;

        // Make sure that this is a table of type TABLE (and not SOURCE)
        if ( catalogTable.entityType != EntityType.ENTITY && catalogTable.entityType != EntityType.MATERIALIZED_VIEW ) {
            throw new RuntimeException( "It is only possible to add an index to a " + catalogTable.entityType.name() );
        }

        // Check if there is already an index with this name for this table
        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsIndex( catalogTable.id, indexName ) ) {
            throw new IndexExistsException();
        }

        String method;
        String methodDisplayName;
        if ( indexMethodName != null ) {
            AvailableIndexMethod aim = null;
            for ( AvailableIndexMethod availableIndexMethod : IndexManager.getAvailableIndexMethods() ) {
                if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                    aim = availableIndexMethod;
                }
            }
            if ( aim == null ) {
                throw new UnknownIndexMethodException();
            }
            method = aim.name;
            methodDisplayName = aim.displayName;
        } else {
            method = IndexManager.getDefaultIndexMethod().name;
            methodDisplayName = IndexManager.getDefaultIndexMethod().displayName;
        }

        long indexId = catalog.getLogicalRel( catalogTable.namespaceId ).addIndex(
                catalogTable.id,
                columnIds,
                isUnique,
                method,
                methodDisplayName,
                0,
                type,
                indexName );

        IndexManager.getInstance().addIndex( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndex( indexId ), statement );
    }


    @Override
    public void addDataPlacement( LogicalTable catalogTable, List<Long> columnIds, List<Integer> partitionGroupIds, List<String> partitionGroupNames, DataStore dataStore, Statement statement ) throws PlacementAlreadyExistsException {
        List<LogicalColumn> addedColumns = new LinkedList<>();

        List<Long> tempPartitionGroupList = new ArrayList<>();

        if ( catalogTable.dataPlacements.contains( dataStore.getAdapterId() ) ) {
            throw new PlacementAlreadyExistsException();
        } else {
            catalog.getAllocRel( catalogTable.namespaceId ).addDataPlacement( dataStore.getAdapterId(), catalogTable.id );
        }

        // Check whether the list is empty (this is a shorthand for a full placement)
        if ( columnIds.size() == 0 ) {
            columnIds = ImmutableList.copyOf( catalogTable.getColumnIds() );
        }

        // Select partitions to create on this placement
        boolean isDataPlacementPartitioned = false;
        long tableId = catalogTable.id;
        // Needed to ensure that column placements on the same store contain all the same partitions
        // Check if this column placement is the first on the data placement
        // If this returns null this means that this is the first placement and partition list can therefore be specified
        List<Long> currentPartList = catalog.getSnapshot().getAllocSnapshot().getPartitionGroupsOnDataPlacement( dataStore.getAdapterId(), catalogTable.id );

        isDataPlacementPartitioned = !currentPartList.isEmpty();

        if ( !partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {

            // Abort if a manual partitionList has been specified even though the data placement has already been partitioned
            if ( isDataPlacementPartitioned ) {
                throw new RuntimeException( "WARNING: The Data Placement for table: '" + catalogTable.name + "' on store: '"
                        + dataStore.getUniqueName() + "' already contains manually specified partitions: " + currentPartList + ". Use 'ALTER TABLE ... MODIFY PARTITIONS...' instead" );
            }

            log.debug( "Table is partitioned and concrete partitionList has been specified " );
            // First convert specified index to correct partitionGroupId
            for ( int partitionGroupId : partitionGroupIds ) {
                // Check if specified partition index is even part of table and if so get corresponding uniquePartId
                try {
                    tempPartitionGroupList.add( catalogTable.partitionProperty.partitionGroupIds.get( partitionGroupId ) );
                } catch ( IndexOutOfBoundsException e ) {
                    throw new RuntimeException( "Specified Partition-Index: '" + partitionGroupId + "' is not part of table '"
                            + catalogTable.name + "', has only " + catalogTable.partitionProperty.numPartitionGroups + " partitions" );
                }
            }
        } else if ( !partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {

            if ( isDataPlacementPartitioned ) {
                throw new RuntimeException( "WARNING: The Data Placement for table: '" + catalogTable.name + "' on store: '"
                        + dataStore.getUniqueName() + "' already contains manually specified partitions: " + currentPartList + ". Use 'ALTER TABLE ... MODIFY PARTITIONS...' instead" );
            }

            List<CatalogPartitionGroup> catalogPartitionGroups = catalog.getSnapshot().getAllocSnapshot().getPartitionGroups( tableId );
            for ( String partitionName : partitionGroupNames ) {
                boolean isPartOfTable = false;
                for ( CatalogPartitionGroup catalogPartitionGroup : catalogPartitionGroups ) {
                    if ( partitionName.equals( catalogPartitionGroup.partitionGroupName.toLowerCase() ) ) {
                        tempPartitionGroupList.add( catalogPartitionGroup.id );
                        isPartOfTable = true;
                        break;
                    }
                }
                if ( !isPartOfTable ) {
                    throw new RuntimeException( "Specified Partition-Name: '" + partitionName + "' is not part of table '"
                            + catalogTable.name + "'. Available partitions: " + String.join( ",", catalog.getSnapshot().getAllocSnapshot().getPartitionGroupNames( tableId ) ) );

                }
            }
        }
        // Simply Place all partitions on placement since nothing has been specified
        else if ( partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {
            log.debug( "Table is partitioned and concrete partitionList has NOT been specified " );

            if ( isDataPlacementPartitioned ) {
                // If DataPlacement already contains partitions then create new placement with same set of partitions.
                tempPartitionGroupList = currentPartList;
            } else {
                tempPartitionGroupList = catalogTable.partitionProperty.partitionGroupIds;
            }
        }
        //}

        //all internal partitions placed on this store
        List<Long> partitionIds = new ArrayList<>();

        // Gather all partitions relevant to add depending on the specified partitionGroup
        tempPartitionGroupList.forEach( pg -> catalog.getSnapshot().getAllocSnapshot().getPartitions( pg ).forEach( p -> partitionIds.add( p.id ) ) );

        // Create column placements
        for ( long cid : columnIds ) {
            catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                    dataStore.getAdapterId(),
                    cid,
                    PlacementType.MANUAL,
                    null,
                    null, null, 0 );
            addedColumns.add( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( cid ) );
        }
        // Check if placement includes primary key columns
        CatalogPrimaryKey primaryKey = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getPrimaryKey( catalogTable.primaryKey );
        for ( long cid : primaryKey.columnIds ) {
            if ( !columnIds.contains( cid ) ) {
                catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                        dataStore.getAdapterId(),
                        cid,
                        PlacementType.AUTOMATIC,
                        null,
                        null, null, 0 );
                addedColumns.add( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( cid ) );
            }
        }

        // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
        for ( long partitionId : partitionIds ) {
            catalog.getAllocRel( catalogTable.namespaceId ).addPartitionPlacement(
                    catalogTable.namespaceId, dataStore.getAdapterId(),
                    catalogTable.id,
                    partitionId,
                    PlacementType.AUTOMATIC,
                    DataPlacementRole.UPTODATE );
        }

        // Make sure that the stores have created the schema
        Catalog.getInstance().getSnapshot();

        // Create table on store
        dataStore.createPhysicalTable( statement.getPrepareContext(), catalogTable, null );
        // Copy data to the newly added placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        dataMigrator.copyData( statement.getTransaction(), catalog.getSnapshot().getAdapter( dataStore.getAdapterId() ), addedColumns, partitionIds );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addPrimaryKey( LogicalTable catalogTable, List<String> columnNames, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        checkModelLogic( catalogTable );

        try {
            CatalogPrimaryKey oldPk = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getPrimaryKey( catalogTable.primaryKey );

            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( catalogTable.id, columnName );
                columnIds.add( logicalColumn.id );
            }
            catalog.getLogicalRel( catalogTable.namespaceId ).addPrimaryKey( catalogTable.id, columnIds );

            // Add new column placements
            long pkColumnId = oldPk.columnIds.get( 0 ); // It is sufficient to check for one because all get replicated on all stores
            List<CatalogColumnPlacement> oldPkPlacements = catalog.getSnapshot().getAllocSnapshot().getColumnPlacements( pkColumnId );
            for ( CatalogColumnPlacement ccp : oldPkPlacements ) {
                for ( long columnId : columnIds ) {
                    if ( !catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( ccp.adapterId, columnId ) ) {
                        catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                                ccp.adapterId,
                                columnId,   // Will be set later
                                PlacementType.AUTOMATIC,   // Will be set later
                                null,   // Will be set later
                                null, null, 0 );
                        AdapterManager.getInstance().getStore( ccp.adapterId ).addColumn(
                                statement.getPrepareContext(),
                                catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getTable( ccp.tableId ),
                                catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( columnId ) );
                    }
                }
            }
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void addUniqueConstraint( LogicalTable catalogTable, List<String> columnNames, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        checkModelLogic( catalogTable, null );

        try {
            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( catalogTable.id, columnName );
                columnIds.add( logicalColumn.id );
            }
            catalog.getLogicalRel( catalogTable.namespaceId ).addUniqueConstraint( catalogTable.id, constraintName, columnIds );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropColumn( LogicalTable catalogTable, String columnName, Statement statement ) throws ColumnNotExistsException {
        if ( catalogTable.columns.size() < 2 ) {
            throw new RuntimeException( "Cannot drop sole column of table " + catalogTable.name );
        }

        // check if model permits operation
        checkModelLogic( catalogTable, columnName );

        //check if views are dependent from this view
        checkViewDependencies( catalogTable );

        LogicalColumn column = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId );

        // Check if column is part of a key
        for ( CatalogKey key : snapshot.getTableKeys( catalogTable.id ) ) {
            if ( key.columnIds.contains( column.id ) ) {
                if ( snapshot.isPrimaryKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the primary key." );
                } else if ( snapshot.isIndex( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the index with the name: '" + snapshot.getIndexes( key ).get( 0 ).name + "'." );
                } else if ( snapshot.isForeignKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the foreign key with the name: '" + snapshot.getForeignKeys( key ).get( 0 ).name + "'." );
                } else if ( snapshot.isConstraint( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + column.name + "' because it is part of the constraint with the name: '" + snapshot.getConstraints( key ).get( 0 ).name + "'." );
                }
                throw new PolyphenyDbException( "Ok, strange... Something is going wrong here!" );
            }
        }

        // Delete column from underlying data stores
        /*for ( CatalogColumnPlacement dp : catalog.getAllocRel( catalogTable.namespaceId ).getColumnPlacementsByColumn( column.id ) ) {
            if ( catalogTable.entityType == EntityType.ENTITY ) {
                AdapterManager.getInstance().getStore( dp.adapterId ).dropColumn( statement.getPrepareContext(), dp );
            }
            catalog.getAllocRel( catalogTable.namespaceId ).deleteColumnPlacement( dp.adapterId, dp.columnId, true );
        }*/
        for ( AllocationTable table : catalog.getSnapshot().getAllocSnapshot().getAllocationsFromLogical( catalogTable.id ) ) {
            for ( CatalogColumnPlacement placement : table.placements ) {
                if ( catalogTable.entityType == EntityType.ENTITY ) {
                    AdapterManager.getInstance().getStore( table.adapterId ).dropColumn( statement.getPrepareContext(), placement );
                }
                catalog.getAllocRel( catalogTable.namespaceId ).deleteColumnPlacement( placement.adapterId, placement.columnId, true );
            }
        }

        // Delete from catalog
        List<LogicalColumn> columns = snapshot.getColumns( catalogTable.id );
        catalog.getLogicalRel( catalogTable.namespaceId ).deleteColumn( column.id );
        if ( column.position != columns.size() ) {
            // Update position of the other columns
            for ( int i = column.position; i < columns.size(); i++ ) {
                catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( columns.get( i ).id, i );
            }
        }

        // Monitor dropColumn for statistics
        prepareMonitoring( statement, Kind.DROP_COLUMN, catalogTable, column );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private void checkModelLogic( LogicalTable catalogTable ) {
        if ( catalogTable.getNamespaceType() == NamespaceType.DOCUMENT ) {
            throw new RuntimeException( "Modification operation is not allowed by schema type DOCUMENT" );
        }
    }


    private void checkModelLogic( LogicalTable catalogTable, String columnName ) {
        if ( catalogTable.getNamespaceType() == NamespaceType.DOCUMENT
                && (columnName.equals( "_data" ) || columnName.equals( "_id" )) ) {
            throw new RuntimeException( "Modification operation is not allowed by schema type DOCUMENT" );
        }
    }


    @Override
    public void dropConstraint( LogicalTable catalogTable, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        try {
            CatalogConstraint constraint = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getConstraint( catalogTable.id, constraintName );
            catalog.getLogicalRel( catalogTable.namespaceId ).deleteConstraint( constraint.id );
        } catch ( GenericCatalogException | UnknownConstraintException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropForeignKey( LogicalTable catalogTable, String foreignKeyName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        try {
            CatalogForeignKey foreignKey = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getForeignKey( catalogTable.id, foreignKeyName );
            catalog.getLogicalRel( catalogTable.namespaceId ).deleteForeignKey( foreignKey.id );
        } catch ( GenericCatalogException | UnknownForeignKeyException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropIndex( LogicalTable catalogTable, String indexName, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        try {
            CatalogIndex index = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndex( catalogTable.id, indexName );

            if ( index.location == 0 ) {
                IndexManager.getInstance().deleteIndex( index );
            } else {
                DataStore storeInstance = AdapterManager.getInstance().getStore( index.location );
                storeInstance.dropIndex( statement.getPrepareContext(), index, catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( index.location, catalogTable.id ) );
            }

            catalog.getLogicalRel( catalogTable.namespaceId ).deleteIndex( index.id );
        } catch ( UnknownIndexException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropDataPlacement( LogicalTable catalogTable, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, LastPlacementException {
        // Check whether this placement exists
        if ( !catalogTable.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        CatalogDataPlacement dataPlacement = catalog.getSnapshot().getAllocSnapshot().getDataPlacement( storeInstance.getAdapterId(), catalogTable.id );
        if ( !catalog.getAllocRel( catalogTable.namespaceId ).validateDataPlacementsConstraints( catalogTable.id, storeInstance.getAdapterId(),
                dataPlacement.columnPlacementsOnAdapter, dataPlacement.getAllPartitionIds() ) ) {

            throw new LastPlacementException();
        }

        // Drop all indexes on this store
        for ( CatalogIndex index : catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndexes( catalogTable.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() ) {
                if ( index.location == 0 ) {
                    // Delete polystore index
                    IndexManager.getInstance().deleteIndex( index );
                } else {
                    // Delete index on store
                    AdapterManager.getInstance().getStore( index.location ).dropIndex(
                            statement.getPrepareContext(),
                            index,
                            catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( index.location, catalogTable.id ) );
                }
                // Delete index in catalog
                catalog.getLogicalRel( catalogTable.namespaceId ).deleteIndex( index.id );
            }
        }
        // Physically delete the data from the store
        storeInstance.dropTable( statement.getPrepareContext(), catalogTable, catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( storeInstance.getAdapterId(), catalogTable.id ) );

        // Remove physical stores afterwards
        catalog.getAllocRel( catalogTable.namespaceId ).removeDataPlacement( storeInstance.getAdapterId(), catalogTable.id );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropPrimaryKey( LogicalTable catalogTable ) throws DdlOnSourceException {
        try {
            // Make sure that this is a table of type TABLE (and not SOURCE)
            checkIfDdlPossible( catalogTable.entityType );
            catalog.getLogicalRel( catalogTable.namespaceId ).deletePrimaryKey( catalogTable.id );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void setColumnType( LogicalTable catalogTable, String columnName, ColumnTypeInformation type, Statement statement ) throws DdlOnSourceException, ColumnNotExistsException, GenericCatalogException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        // check if model permits operation
        checkModelLogic( catalogTable, columnName );

        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        catalog.getLogicalRel( catalogTable.namespaceId ).setColumnType(
                logicalColumn.id,
                type.type,
                type.collectionType,
                type.precision,
                type.scale,
                type.dimension,
                type.cardinality );
        for ( CatalogColumnPlacement placement : catalog.getSnapshot().getAllocSnapshot().getColumnPlacements( logicalColumn.id ) ) {
            AdapterManager.getInstance().getStore( placement.adapterId ).updateColumnType(
                    statement.getPrepareContext(),
                    placement,
                    catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( logicalColumn.id ),
                    logicalColumn.type );
        }

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnNullable( LogicalTable catalogTable, String columnName, boolean nullable, Statement statement ) throws ColumnNotExistsException, DdlOnSourceException, GenericCatalogException {
        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        // Check if model permits operation
        checkModelLogic( catalogTable, columnName );

        catalog.getLogicalRel( catalogTable.namespaceId ).setNullable( logicalColumn.id, nullable );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnPosition( LogicalTable catalogTable, String columnName, String beforeColumnName, String afterColumnName, Statement statement ) throws ColumnNotExistsException {
        // Check if model permits operation
        checkModelLogic( catalogTable, columnName );

        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        int targetPosition;
        LogicalColumn refColumn;
        if ( beforeColumnName != null ) {
            refColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, beforeColumnName );
            targetPosition = refColumn.position;
        } else {
            refColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, afterColumnName );
            targetPosition = refColumn.position + 1;
        }
        if ( logicalColumn.id == refColumn.id ) {
            throw new RuntimeException( "Same column!" );
        }
        List<LogicalColumn> columns = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumns( catalogTable.id );
        if ( targetPosition < logicalColumn.position ) {  // Walk from last column to first column
            for ( int i = columns.size(); i >= 1; i-- ) {
                if ( i < logicalColumn.position && i >= targetPosition ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( columns.get( i - 1 ).id, i + 1 );
                } else if ( i == logicalColumn.position ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( logicalColumn.id, columns.size() + 1 );
                }
                if ( i == targetPosition ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( logicalColumn.id, targetPosition );
                }
            }
        } else if ( targetPosition > logicalColumn.position ) { // Walk from first column to last column
            targetPosition--;
            for ( int i = 1; i <= columns.size(); i++ ) {
                if ( i > logicalColumn.position && i <= targetPosition ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( columns.get( i - 1 ).id, i - 1 );
                } else if ( i == logicalColumn.position ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( logicalColumn.id, columns.size() + 1 );
                }
                if ( i == targetPosition ) {
                    catalog.getLogicalRel( catalogTable.namespaceId ).setColumnPosition( logicalColumn.id, targetPosition );
                }
            }
        }
        // Do nothing

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setColumnCollation( LogicalTable catalogTable, String columnName, Collation collation, Statement statement ) throws ColumnNotExistsException, DdlOnSourceException {
        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // Check if model permits operation
        checkModelLogic( catalogTable, columnName );

        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfDdlPossible( catalogTable.entityType );

        catalog.getLogicalRel( catalogTable.namespaceId ).setCollation( logicalColumn.id, collation );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void setDefaultValue( LogicalTable catalogTable, String columnName, String defaultValue, Statement statement ) throws ColumnNotExistsException {
        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // Check if model permits operation
        checkModelLogic( catalogTable, columnName );

        addDefaultValue( catalogTable.namespaceId, defaultValue, logicalColumn.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropDefaultValue( LogicalTable catalogTable, String columnName, Statement statement ) throws ColumnNotExistsException {
        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // check if model permits operation
        checkModelLogic( catalogTable, columnName );

        catalog.getLogicalRel( catalogTable.namespaceId ).deleteDefaultValue( logicalColumn.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void modifyDataPlacement( LogicalTable catalogTable, List<Long> columnIds, List<Integer> partitionGroupIds, List<String> partitionGroupNames, DataStore storeInstance, Statement statement )
            throws PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException {

        // Check whether this placement already exists
        if ( !catalogTable.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        // Check if views are dependent from this view
        checkViewDependencies( catalogTable );

        List<Long> columnsToRemove = new ArrayList<>();

        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId );

        // Checks before physically removing of placement that the partition distribution is still valid and sufficient
        // Identifies which columns need to be removed
        for ( CatalogColumnPlacement placement : catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapterPerTable( storeInstance.getAdapterId(), catalogTable.id ) ) {
            if ( !columnIds.contains( placement.columnId ) ) {
                // Check whether there are any indexes located on the store requiring this column
                for ( CatalogIndex index : snapshot.getIndexes( catalogTable.id, false ) ) {
                    if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( placement.columnId ) ) {
                        throw new IndexPreventsRemovalException( index.name, snapshot.getColumn( placement.columnId ).name );
                    }
                }
                // Check whether the column is a primary key column
                CatalogPrimaryKey primaryKey = snapshot.getPrimaryKey( catalogTable.primaryKey );
                if ( primaryKey.columnIds.contains( placement.columnId ) ) {
                    // Check if the placement type is manual. If so, change to automatic
                    if ( placement.placementType == PlacementType.MANUAL ) {
                        // Make placement manual
                        catalog.getAllocRel( catalogTable.namespaceId ).updateColumnPlacementType(
                                storeInstance.getAdapterId(),
                                placement.columnId,
                                PlacementType.AUTOMATIC );
                    }
                } else {
                    // It is not a primary key. Remove the column
                    columnsToRemove.add( placement.columnId );
                }
            }
        }

        if ( !catalog.getAllocRel( catalogTable.namespaceId ).validateDataPlacementsConstraints( catalogTable.id, storeInstance.getAdapterId(), columnsToRemove, new ArrayList<>() ) ) {
            throw new LastPlacementException();
        }

        boolean adjustPartitions = true;
        // Remove columns physically
        for ( long columnId : columnsToRemove ) {
            // Drop Column on store
            storeInstance.dropColumn( statement.getPrepareContext(), catalog.getSnapshot().getAllocSnapshot().getColumnPlacement( storeInstance.getAdapterId(), columnId ) );
            // Drop column placement
            catalog.getAllocRel( catalogTable.namespaceId ).deleteColumnPlacement( storeInstance.getAdapterId(), columnId, true );
        }

        List<Long> tempPartitionGroupList = new ArrayList<>();

        // Select partitions to create on this placement
        if ( catalogTable.partitionProperty.isPartitioned ) {
            long tableId = catalogTable.id;
            // If index partitions are specified
            if ( !partitionGroupIds.isEmpty() && partitionGroupNames.isEmpty() ) {
                // First convert specified index to correct partitionGroupId
                for ( long partitionGroupId : partitionGroupIds ) {
                    // Check if specified partition index is even part of table and if so get corresponding uniquePartId
                    try {
                        int index = catalogTable.partitionProperty.partitionGroupIds.indexOf( partitionGroupId );
                        tempPartitionGroupList.add( catalogTable.partitionProperty.partitionGroupIds.get( index ) );
                    } catch ( IndexOutOfBoundsException e ) {
                        throw new RuntimeException( "Specified Partition-Index: '" + partitionGroupId + "' is not part of table '"
                                + catalogTable.name + "', has only " + catalogTable.partitionProperty.partitionGroupIds.size() + " partitions" );
                    }
                }
            }
            // If name partitions are specified
            else if ( !partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {
                List<CatalogPartitionGroup> catalogPartitionGroups = catalog.getSnapshot().getAllocSnapshot().getPartitionGroups( tableId );
                for ( String partitionName : partitionGroupNames ) {
                    boolean isPartOfTable = false;
                    for ( CatalogPartitionGroup catalogPartitionGroup : catalogPartitionGroups ) {
                        if ( partitionName.equals( catalogPartitionGroup.partitionGroupName.toLowerCase() ) ) {
                            tempPartitionGroupList.add( catalogPartitionGroup.id );
                            isPartOfTable = true;
                            break;
                        }
                    }
                    if ( !isPartOfTable ) {
                        throw new RuntimeException( "Specified partition name: '" + partitionName + "' is not part of table '"
                                + catalogTable.name + "'. Available partitions: " + String.join( ",", catalog.getSnapshot().getAllocSnapshot().getPartitionGroupNames( tableId ) ) );
                    }
                }
            } else if ( partitionGroupNames.isEmpty() && partitionGroupIds.isEmpty() ) {
                // If nothing has been explicitly specified keep current placement of partitions.
                // Since it's impossible to have a placement without any partitions anyway
                log.debug( "Table is partitioned and concrete partitionList has NOT been specified " );
                tempPartitionGroupList = catalogTable.partitionProperty.partitionGroupIds;
            }
        } else {
            tempPartitionGroupList.add( catalogTable.partitionProperty.partitionGroupIds.get( 0 ) );
        }

        // All internal partitions placed on this store
        List<Long> intendedPartitionIds = new ArrayList<>();

        // Gather all partitions relevant to add depending on the specified partitionGroup
        tempPartitionGroupList.forEach( pg -> catalog.getSnapshot().getAllocSnapshot().getPartitions( pg ).forEach( p -> intendedPartitionIds.add( p.id ) ) );

        // Which columns to add
        List<LogicalColumn> addedColumns = new LinkedList<>();

        for ( long cid : columnIds ) {
            if ( catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( storeInstance.getAdapterId(), cid ) ) {
                CatalogColumnPlacement placement = catalog.getSnapshot().getAllocSnapshot().getColumnPlacement( storeInstance.getAdapterId(), cid );
                if ( placement.placementType == PlacementType.AUTOMATIC ) {
                    // Make placement manual
                    catalog.getAllocRel( catalogTable.namespaceId ).updateColumnPlacementType( storeInstance.getAdapterId(), cid, PlacementType.MANUAL );
                }
            } else {
                // Create column placement
                catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                        storeInstance.getAdapterId(),
                        cid,
                        PlacementType.MANUAL,
                        null,
                        null, null, 0 );
                // Add column on store
                storeInstance.addColumn( statement.getPrepareContext(), catalogTable, snapshot.getColumn( cid ) );
                // Add to list of columns for which we need to copy data
                addedColumns.add( snapshot.getColumn( cid ) );
            }
        }

        CatalogDataPlacement dataPlacement = catalog.getSnapshot().getAllocSnapshot().getDataPlacement( storeInstance.getAdapterId(), catalogTable.id );
        List<Long> removedPartitionIdsFromDataPlacement = new ArrayList<>();
        // Removed Partition Ids
        for ( long partitionId : dataPlacement.getAllPartitionIds() ) {
            if ( !intendedPartitionIds.contains( partitionId ) ) {
                removedPartitionIdsFromDataPlacement.add( partitionId );
            }
        }

        List<Long> newPartitionIdsOnDataPlacement = new ArrayList<>();
        // Added Partition Ids
        for ( long partitionId : intendedPartitionIds ) {
            if ( !dataPlacement.getAllPartitionIds().contains( partitionId ) ) {
                newPartitionIdsOnDataPlacement.add( partitionId );
            }
        }

        if ( removedPartitionIdsFromDataPlacement.size() > 0 ) {
            storeInstance.dropTable( statement.getPrepareContext(), catalogTable, removedPartitionIdsFromDataPlacement );
        }

        if ( newPartitionIdsOnDataPlacement.size() > 0 ) {
            newPartitionIdsOnDataPlacement.forEach( partitionId -> catalog.getAllocRel( catalogTable.namespaceId ).addPartitionPlacement(
                    catalogTable.namespaceId, storeInstance.getAdapterId(),
                    catalogTable.id,
                    partitionId,
                    PlacementType.MANUAL,
                    DataPlacementRole.UPTODATE )
            );
            storeInstance.createPhysicalTable( statement.getPrepareContext(), catalogTable, null );
        }

        // Copy the data to the newly added column placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        if ( addedColumns.size() > 0 ) {
            dataMigrator.copyData( statement.getTransaction(), catalog.getSnapshot().getAdapter( storeInstance.getAdapterId() ), addedColumns, intendedPartitionIds );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void modifyPartitionPlacement( LogicalTable catalogTable, List<Long> partitionGroupIds, DataStore storeInstance, Statement statement ) throws LastPlacementException {
        long storeId = storeInstance.getAdapterId();
        List<Long> newPartitions = new ArrayList<>();
        List<Long> removedPartitions = new ArrayList<>();

        List<Long> currentPartitionGroupsOnStore = catalog.getSnapshot().getAllocSnapshot().getPartitionGroupsOnDataPlacement( storeId, catalogTable.id );

        // Get PartitionGroups that have been removed
        for ( long partitionGroupId : currentPartitionGroupsOnStore ) {
            if ( !partitionGroupIds.contains( partitionGroupId ) ) {
                catalog.getSnapshot().getAllocSnapshot().getPartitions( partitionGroupId ).forEach( p -> removedPartitions.add( p.id ) );
            }
        }

        if ( !catalog.getAllocRel( catalogTable.namespaceId ).validateDataPlacementsConstraints( catalogTable.id, storeInstance.getAdapterId(), new ArrayList<>(), removedPartitions ) ) {
            throw new LastPlacementException();
        }

        // Get PartitionGroups that have been newly added
        for ( Long partitionGroupId : partitionGroupIds ) {
            if ( !currentPartitionGroupsOnStore.contains( partitionGroupId ) ) {
                catalog.getSnapshot().getAllocSnapshot().getPartitions( partitionGroupId ).forEach( p -> newPartitions.add( p.id ) );
            }
        }

        // Copy the data to the newly added column placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        if ( newPartitions.size() > 0 ) {
            // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
            for ( long partitionId : newPartitions ) {
                catalog.getAllocRel( catalogTable.namespaceId ).addPartitionPlacement(
                        catalogTable.namespaceId, storeInstance.getAdapterId(),
                        catalogTable.id,
                        partitionId,
                        PlacementType.AUTOMATIC,
                        DataPlacementRole.UPTODATE );
            }

            storeInstance.createPhysicalTable( statement.getPrepareContext(), catalogTable, null );

            // Get only columns that are actually on that store
            List<LogicalColumn> necessaryColumns = new LinkedList<>();
            catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapterPerTable( storeInstance.getAdapterId(), catalogTable.id ).forEach( cp -> necessaryColumns.add( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getColumn( cp.columnId ) ) );
            dataMigrator.copyData( statement.getTransaction(), catalog.getSnapshot().getAdapter( storeId ), necessaryColumns, newPartitions );

            // Add indexes on this new Partition Placement if there is already an index
            for ( CatalogIndex currentIndex : catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndexes( catalogTable.id, false ) ) {
                if ( currentIndex.location == storeId ) {
                    storeInstance.addIndex( statement.getPrepareContext(), currentIndex, newPartitions );
                }
            }
        }

        if ( removedPartitions.size() > 0 ) {
            //  Remove indexes
            for ( CatalogIndex currentIndex : catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndexes( catalogTable.id, false ) ) {
                if ( currentIndex.location == storeId ) {
                    storeInstance.dropIndex( statement.getPrepareContext(), currentIndex, removedPartitions );
                }
            }
            storeInstance.dropTable( statement.getPrepareContext(), catalogTable, removedPartitions );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void addColumnPlacement( LogicalTable catalogTable, String columnName, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, PlacementAlreadyExistsException, ColumnNotExistsException {
        columnName = adjustNameIfNeeded( columnName, catalogTable.namespaceId );

        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );
        }
        // Check whether this placement already exists
        if ( !catalogTable.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // Make sure that this store does not contain a placement of this column
        if ( catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( storeInstance.getAdapterId(), logicalColumn.id ) ) {
            CatalogColumnPlacement placement = catalog.getSnapshot().getAllocSnapshot().getColumnPlacement( storeInstance.getAdapterId(), logicalColumn.id );
            if ( placement.placementType == PlacementType.AUTOMATIC ) {
                // Make placement manual
                catalog.getAllocRel( catalogTable.namespaceId ).updateColumnPlacementType(
                        storeInstance.getAdapterId(),
                        logicalColumn.id,
                        PlacementType.MANUAL );
            } else {
                throw new PlacementAlreadyExistsException();
            }
        } else {
            // Create column placement
            catalog.getAllocRel( catalogTable.namespaceId ).addColumnPlacement( catalogTable,
                    storeInstance.getAdapterId(),
                    logicalColumn.id,
                    PlacementType.MANUAL,
                    null,
                    null, null, 0 );
            // Add column on store
            storeInstance.addColumn( statement.getPrepareContext(), catalogTable, logicalColumn );
            // Copy the data to the newly added column placements
            DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
            dataMigrator.copyData( statement.getTransaction(), catalog.getSnapshot().getAdapter( storeInstance.getAdapterId() ),
                    ImmutableList.of( logicalColumn ), catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( storeInstance.getAdapterId(), catalogTable.id ) );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropColumnPlacement( LogicalTable catalogTable, String columnName, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException, PlacementIsPrimaryException, ColumnNotExistsException {
        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );
        }
        // Check whether this placement already exists
        if ( !catalogTable.dataPlacements.contains( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        // Check whether this store actually contains a placement of this column
        if ( !catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( storeInstance.getAdapterId(), logicalColumn.id ) ) {
            throw new PlacementNotExistsException();
        }
        // Check whether there are any indexes located on the store requiring this column
        for ( CatalogIndex index : catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getIndexes( catalogTable.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( logicalColumn.id ) ) {
                throw new IndexPreventsRemovalException( index.name, columnName );
            }
        }

        if ( !catalog.getAllocRel( catalogTable.namespaceId ).validateDataPlacementsConstraints( logicalColumn.tableId, storeInstance.getAdapterId(), Arrays.asList( logicalColumn.id ), new ArrayList<>() ) ) {
            throw new LastPlacementException();
        }

        // Check whether the column to drop is a primary key
        CatalogPrimaryKey primaryKey = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).getPrimaryKey( catalogTable.primaryKey );
        if ( primaryKey.columnIds.contains( logicalColumn.id ) ) {
            throw new PlacementIsPrimaryException();
        }
        // Drop Column on store
        storeInstance.dropColumn( statement.getPrepareContext(), catalog.getSnapshot().getAllocSnapshot().getColumnPlacement( storeInstance.getAdapterId(), logicalColumn.id ) );
        // Drop column placement
        catalog.getAllocRel( catalogTable.namespaceId ).deleteColumnPlacement( storeInstance.getAdapterId(), logicalColumn.id, false );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void alterTableOwner( LogicalTable catalogTable, String newOwnerName ) throws UnknownUserException {
        CatalogUser catalogUser = catalog.getSnapshot().getUser( newOwnerName );
        catalog.getLogicalRel( catalogTable.namespaceId ).setTableOwner( catalogTable.id, catalogUser.id );
    }


    @Override
    public void renameTable( LogicalTable catalogTable, String newTableName, Statement statement ) throws EntityAlreadyExistsException {
        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsEntity( newTableName ) ) {
            throw new EntityAlreadyExistsException();
        }
        // Check if views are dependent from this view
        checkViewDependencies( catalogTable );

        if ( catalog.getSnapshot().getNamespace( catalogTable.namespaceId ).caseSensitive ) {
            newTableName = newTableName.toLowerCase();
        }

        catalog.getLogicalRel( catalogTable.namespaceId ).renameTable( catalogTable.id, newTableName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateTableName( catalogTable, newTableName );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void renameColumn( LogicalTable catalogTable, String columnName, String newColumnName, Statement statement ) throws ColumnAlreadyExistsException, ColumnNotExistsException {
        LogicalColumn logicalColumn = getCatalogColumn( catalogTable.namespaceId, catalogTable.id, columnName );

        if ( catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId ).checkIfExistsColumn( logicalColumn.tableId, newColumnName ) ) {
            throw new ColumnAlreadyExistsException( newColumnName, logicalColumn.getTableName() );
        }
        // Check if views are dependent from this view
        checkViewDependencies( catalogTable );

        catalog.getLogicalRel( catalogTable.namespaceId ).renameColumn( logicalColumn.id, newColumnName );

        // Update Name in statistics
        StatisticsManager.getInstance().updateColumnName( logicalColumn, newColumnName );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void createView( String viewName, long namespaceId, AlgNode algNode, AlgCollation algCollation, boolean replace, Statement statement, PlacementType placementType, List<String> projectedColumns, String query, QueryLanguage language ) throws EntityAlreadyExistsException {
        viewName = adjustNameIfNeeded( viewName, namespaceId );

        if ( catalog.getSnapshot().getRelSnapshot( namespaceId ).checkIfExistsEntity( viewName ) ) {
            if ( replace ) {
                try {
                    dropView( catalog.getSnapshot().getRelSnapshot( namespaceId ).getTable( viewName ), statement );
                } catch ( UnknownTableException | DdlOnSourceException e ) {
                    throw new RuntimeException( "Unable tp drop the existing View with this name." );
                }
            } else {
                throw new EntityAlreadyExistsException();
            }
        }

        AlgDataType fieldList = algNode.getRowType();

        List<FieldInformation> columns = getColumnInformation( projectedColumns, fieldList );

        Map<Long, List<Long>> underlyingTables = new HashMap<>();

        findUnderlyingTablesOfView( algNode, underlyingTables, fieldList );

        // add check if underlying table is of model document -> mql, relational -> sql
        underlyingTables.keySet().forEach( tableId -> checkModelLangCompatibility( language, namespaceId, tableId ) );

        long tableId = catalog.getLogicalRel( namespaceId ).addView(
                viewName,
                namespaceId,
                EntityType.VIEW,
                false,
                algNode,
                algCollation,
                underlyingTables,
                fieldList,
                query,
                language
        );

        for ( FieldInformation column : columns ) {
            catalog.getLogicalRel( namespaceId ).addColumn(
                    column.name,
                    tableId,
                    column.position,
                    column.typeInformation.type,
                    column.typeInformation.collectionType,
                    column.typeInformation.precision,
                    column.typeInformation.scale,
                    column.typeInformation.dimension,
                    column.typeInformation.cardinality,
                    column.typeInformation.nullable,
                    column.collation );
        }
    }


    private String adjustNameIfNeeded( String name, long namespaceId ) {
        if ( !catalog.getSnapshot().getNamespace( namespaceId ).caseSensitive ) {
            return name.toLowerCase();
        }
        return name;
    }


    @Override
    public void createMaterializedView( String viewName, long namespaceId, AlgRoot algRoot, boolean replace, Statement statement, List<DataStore> stores, PlacementType placementType, List<String> projectedColumns, MaterializedCriteria materializedCriteria, String query, QueryLanguage language, boolean ifNotExists, boolean ordered ) throws EntityAlreadyExistsException, GenericCatalogException {
        viewName = adjustNameIfNeeded( viewName, namespaceId );
        // Check if there is already a table with this name
        if ( assertEntityExists( namespaceId, viewName, ifNotExists ) ) {
            return;
        }

        if ( stores == null ) {
            // Ask router on which store(s) the table should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewEntity();
        }

        AlgDataType fieldList = algRoot.alg.getRowType();

        Map<Long, List<Long>> underlyingTables = new HashMap<>();
        Map<Long, List<Long>> underlying = findUnderlyingTablesOfView( algRoot.alg, underlyingTables, fieldList );

        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( namespaceId );

        // add check if underlying table is of model document -> mql, relational -> sql
        underlying.keySet().forEach( tableId -> checkModelLangCompatibility( language, namespaceId, tableId ) );

        if ( materializedCriteria.getCriteriaType() == CriteriaType.UPDATE ) {
            List<EntityType> entityTypes = new ArrayList<>();
            underlying.keySet().forEach( t -> entityTypes.add( snapshot.getTable( t ).entityType ) );
            if ( !(entityTypes.contains( EntityType.ENTITY )) ) {
                throw new GenericCatalogException( "Not possible to use Materialized View with Update Freshness if underlying table does not include a modifiable table." );
            }
        }

        long tableId = catalog.getLogicalRel( namespaceId ).addMaterializedView(
                viewName,
                namespaceId,
                EntityType.MATERIALIZED_VIEW,
                false,
                algRoot.alg,
                algRoot.collation,
                underlying,
                fieldList,
                materializedCriteria,
                query,
                language,
                ordered
        );

        // Creates a list with all columns, tableId is needed to create the primary key
        List<FieldInformation> columns = getColumnInformation( projectedColumns, fieldList, true, tableId );
        Map<Long, List<LogicalColumn>> addedColumns = new HashMap<>();

        List<Long> columnIds = new ArrayList<>();

        for ( FieldInformation column : columns ) {
            long columnId = catalog.getLogicalRel( namespaceId ).addColumn(
                    column.name,
                    tableId,
                    column.position,
                    column.typeInformation.type,
                    column.typeInformation.collectionType,
                    column.typeInformation.precision,
                    column.typeInformation.scale,
                    column.typeInformation.dimension,
                    column.typeInformation.cardinality,
                    column.typeInformation.nullable,
                    column.collation );

            // Created primary key is added to list
            if ( column.name.startsWith( "_matid_" ) ) {
                columnIds.add( columnId );
            }

            for ( DataStore s : stores ) {
                long adapterId = s.getAdapterId();
                catalog.getAllocRel( namespaceId ).addColumnPlacement( catalog.getLogicalEntity( tableId ).unwrap( LogicalTable.class ),
                        s.getAdapterId(),
                        columnId,
                        placementType,
                        null,
                        null, null, 0 );

                List<LogicalColumn> logicalColumns;
                if ( addedColumns.containsKey( adapterId ) ) {
                    logicalColumns = addedColumns.get( adapterId );
                } else {
                    logicalColumns = new ArrayList<>();
                }
                logicalColumns.add( snapshot.getColumn( columnId ) );
                addedColumns.put( adapterId, logicalColumns );
            }

        }
        // Sets previously created primary key
        catalog.getLogicalRel( namespaceId ).addPrimaryKey( tableId, columnIds );

        CatalogMaterializedView catalogMaterializedView = catalog.getSnapshot().getRelSnapshot( namespaceId ).getTable( tableId ).unwrap( CatalogMaterializedView.class );
        Catalog.getInstance().getSnapshot();

        for ( DataStore store : stores ) {
            catalog.getAllocRel( namespaceId ).addPartitionPlacement(
                    catalogMaterializedView.namespaceId,
                    store.getAdapterId(),
                    tableId,
                    catalogMaterializedView.partitionProperty.partitionIds.get( 0 ),
                    PlacementType.AUTOMATIC,
                    DataPlacementRole.UPTODATE );

            store.createPhysicalTable( statement.getPrepareContext(), catalogMaterializedView, null );
        }

        // Selected data from tables is added into the newly crated materialized view
        MaterializedViewManager materializedManager = MaterializedViewManager.getInstance();
        materializedManager.addData( statement.getTransaction(), stores, addedColumns, algRoot, catalogMaterializedView );
    }


    private void checkModelLangCompatibility( QueryLanguage language, long namespaceId, Long tableId ) {
        LogicalTable catalogTable = catalog.getSnapshot().getRelSnapshot( namespaceId ).getTable( tableId );
        if ( catalogTable.getNamespaceType() != language.getNamespaceType() ) {
            throw new RuntimeException(
                    String.format(
                            "The used language cannot execute schema changing queries on this entity with the data model %s.",
                            catalogTable.getNamespaceType() ) );
        }
    }


    @Override
    public void refreshView( Statement statement, Long materializedId ) {
        MaterializedViewManager materializedManager = MaterializedViewManager.getInstance();
        materializedManager.updateData( statement.getTransaction(), materializedId );
        materializedManager.updateMaterializedTime( materializedId );
    }


    @Override
    public long createGraph( String graphName, boolean modifiable, @Nullable List<DataStore> stores, boolean ifNotExists, boolean replace, boolean caseSensitive, Statement statement ) {
        assert !replace : "Graphs cannot be replaced yet.";

        graphName = caseSensitive ? graphName : graphName.toLowerCase();

        if ( stores == null ) {
            // Ask router on which store(s) the graph should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewEntity();
        }

        // add general graph
        long graphId = catalog.addNamespace( graphName, NamespaceType.GRAPH, caseSensitive );
        catalog.getLogicalGraph( graphId ).addGraph( graphName, stores, modifiable, ifNotExists, replace );

        addGraphPlacement( graphId, stores, false, statement );

        return graphId;
    }


    @Override
    public long addGraphPlacement( long graphId, List<DataStore> stores, boolean onlyPlacement, Statement statement ) {
        try {
            catalog.getLogicalGraph( graphId ).addGraphLogistics( graphId, stores, onlyPlacement );
        } catch ( GenericCatalogException | UnknownTableException | UnknownColumnException e ) {
            throw new RuntimeException();
        }

        LogicalGraph graph = catalog.getSnapshot().getGraphSnapshot( graphId ).getGraph( graphId );
        Catalog.getInstance().getSnapshot();

        List<Long> preExistingPlacements = graph.placements
                .stream()
                .filter( p -> !stores.stream().map( Adapter::getAdapterId ).collect( Collectors.toList() ).contains( p ) )
                .collect( Collectors.toList() );

        Long existingAdapterId = preExistingPlacements.isEmpty() ? null : preExistingPlacements.get( 0 );

        for ( DataStore store : stores ) {
            catalog.getAllocGraph( graphId ).addGraphPlacement( store.getAdapterId(), graphId );

            afterGraphPlacementAddLogistics( store, graphId );

            store.createGraph( statement.getPrepareContext(), graph );

            if ( existingAdapterId != null ) {
                // Copy the data to the newly added column placements
                DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
                dataMigrator.copyGraphData( graph, statement.getTransaction(), existingAdapterId, catalog.getSnapshot().getAdapter( store.getAdapterId() ) );
            }

        }

        return graphId;
    }


    @Override
    public void removeGraphDatabasePlacement( long graphId, DataStore store, Statement statement ) {
        CatalogGraphPlacement placement = catalog.getAllocGraph( graphId ).getGraphPlacement( graphId, store.getAdapterId() );

        store.dropGraph( statement.getPrepareContext(), placement );

        afterGraphDropLogistics( store, graphId );

        catalog.getAllocGraph( graphId ).deleteGraphPlacement( store.getAdapterId(), graphId );

        Catalog.getInstance().getSnapshot();

    }


    private void afterGraphDropLogistics( DataStore store, long graphId ) {
        /*CatalogGraphMapping mapping = catalog.getLogicalRel( graphId ).getGraphMapping( graphId );

        catalog.getAllocGraph( graphId ).removeDataPlacement( store.getAdapterId(), mapping.nodesId );
        catalog.getAllocGraph( graphId ).removeDataPlacement( store.getAdapterId(), mapping.nodesPropertyId );
        catalog.getAllocGraph( catalogTable.namespaceId ).removeDataPlacement( store.getAdapterId(), mapping.edgesId );
        catalog.getAllocGraph( catalogTable.namespaceId ).removeDataPlacement( store.getAdapterId(), mapping.edgesPropertyId );*/ // replace
    }


    private void afterGraphPlacementAddLogistics( DataStore store, long graphId ) {
        /*CatalogGraphMapping mapping = catalog.getLogicalRel( catalogTable.namespaceId ).getGraphMapping( graphId );
        LogicalTable nodes = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.nodesId );
        LogicalTable nodeProperty = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.nodesPropertyId );
        LogicalTable edges = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.edgesId );
        LogicalTable edgeProperty = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.edgesPropertyId );

        catalog.getLogicalRel( catalogTable.namespaceId ).addDataPlacement( store.getAdapterId(), mapping.nodesId );
        catalog.getLogicalRel( catalogTable.namespaceId ).addDataPlacement( store.getAdapterId(), mapping.nodesPropertyId );
        catalog.getLogicalRel( catalogTable.namespaceId ).addDataPlacement( store.getAdapterId(), mapping.edgesId );
        catalog.getLogicalRel( catalogTable.namespaceId ).addDataPlacement( store.getAdapterId(), mapping.edgesPropertyId );

        catalog.getLogicalRel( catalogTable.namespaceId ).addPartitionPlacement(
                nodes.namespaceId,
                store.getAdapterId(),
                nodes.id,
                nodes.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.getLogicalRel( catalogTable.namespaceId ).addPartitionPlacement(
                nodeProperty.namespaceId,
                store.getAdapterId(),
                nodeProperty.id,
                nodeProperty.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.getLogicalRel( catalogTable.namespaceId ).addPartitionPlacement(
                edges.namespaceId,
                store.getAdapterId(),
                edges.id,
                edges.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );

        catalog.getLogicalRel( catalogTable.namespaceId ).addPartitionPlacement(
                edgeProperty.namespaceId,
                store.getAdapterId(),
                edgeProperty.id,
                edgeProperty.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );
*/// todo dl replace

    }


    @Override
    public void addGraphAlias( long graphId, String alias, boolean ifNotExists ) {
        catalog.getLogicalGraph( graphId ).addGraphAlias( graphId, alias, ifNotExists );
    }


    @Override
    public void removeGraphAlias( long graphId, String alias, boolean ifNotExists ) {
        alias = alias.toLowerCase();
        catalog.getLogicalGraph( graphId ).removeGraphAlias( graphId, alias, ifNotExists );
    }


    @Override
    public void replaceGraphAlias( long graphId, String oldAlias, String alias ) {
        alias = alias.toLowerCase();
        oldAlias = oldAlias.toLowerCase();
        catalog.getLogicalGraph( graphId ).removeGraphAlias( graphId, oldAlias, true );
        catalog.getLogicalGraph( graphId ).addGraphAlias( graphId, alias, true );
    }


    @Override
    public void removeGraph( long graphId, boolean ifExists, Statement statement ) {
        LogicalGraph graph = catalog.getSnapshot().getGraphSnapshot( graphId ).getGraph( graphId );

        if ( graph == null ) {
            if ( !ifExists ) {
                throw new UnknownGraphException( graphId );
            }
            return;
        }

        for ( long adapterId : graph.placements ) {
            CatalogGraphPlacement placement = catalog.getAllocGraph( graphId ).getGraphPlacement( graphId, adapterId );
            AdapterManager.getInstance().getStore( adapterId ).dropGraph( statement.getPrepareContext(), placement );
        }

        catalog.getLogicalGraph( graphId ).deleteGraph( graphId );
    }


    private List<FieldInformation> getColumnInformation( List<String> projectedColumns, AlgDataType fieldList ) {
        return getColumnInformation( projectedColumns, fieldList, false, 0 );
    }


    private List<FieldInformation> getColumnInformation( List<String> projectedColumns, AlgDataType fieldList, boolean addPrimary, long tableId ) {
        List<FieldInformation> columns = new ArrayList<>();

        int position = 1;
        for ( AlgDataTypeField alg : fieldList.getFieldList() ) {
            AlgDataType type = alg.getValue();
            if ( alg.getType().getPolyType() == PolyType.ARRAY ) {
                type = alg.getValue().getComponentType();
            }
            String colName = alg.getName();
            if ( projectedColumns != null ) {
                colName = projectedColumns.get( position - 1 );
            }

            columns.add( new FieldInformation(
                    colName.toLowerCase().replaceAll( "[^A-Za-z0-9]", "_" ),
                    new ColumnTypeInformation(
                            type.getPolyType(),
                            alg.getType().getPolyType(),
                            type.getRawPrecision(),
                            type.getScale(),
                            alg.getValue().getPolyType() == PolyType.ARRAY ? (int) ((ArrayType) alg.getValue()).getDimension() : -1,
                            alg.getValue().getPolyType() == PolyType.ARRAY ? (int) ((ArrayType) alg.getValue()).getCardinality() : -1,
                            alg.getValue().isNullable() ),
                    Collation.getDefaultCollation(),
                    null,
                    position ) );
            position++;

        }

        if ( addPrimary ) {
            String primaryName = "_matid_" + tableId;
            columns.add( new FieldInformation(
                    primaryName,
                    new ColumnTypeInformation(
                            PolyType.INTEGER,
                            PolyType.INTEGER,
                            -1,
                            -1,
                            -1,
                            -1,
                            false ),
                    Collation.getDefaultCollation(),
                    null,
                    position ) );
        }

        return columns;
    }


    private Map<Long, List<Long>> findUnderlyingTablesOfView( AlgNode algNode, Map<Long, List<Long>> underlyingTables, AlgDataType fieldList ) {
        if ( algNode instanceof LogicalRelScan ) {
            List<Long> underlyingColumns = getUnderlyingColumns( algNode, fieldList );
            underlyingTables.put( algNode.getEntity().id, underlyingColumns );
        } else if ( algNode instanceof LogicalRelViewScan ) {
            List<Long> underlyingColumns = getUnderlyingColumns( algNode, fieldList );
            underlyingTables.put( algNode.getEntity().id, underlyingColumns );
        }
        if ( algNode instanceof BiAlg ) {
            findUnderlyingTablesOfView( ((BiAlg) algNode).getLeft(), underlyingTables, fieldList );
            findUnderlyingTablesOfView( ((BiAlg) algNode).getRight(), underlyingTables, fieldList );
        } else if ( algNode instanceof SingleAlg ) {
            findUnderlyingTablesOfView( ((SingleAlg) algNode).getInput(), underlyingTables, fieldList );
        }
        return underlyingTables;
    }


    private List<Long> getUnderlyingColumns( AlgNode algNode, AlgDataType fieldList ) {
        LogicalTable table = algNode.getEntity().unwrap( LogicalTable.class );
        List<LogicalColumn> columns = table.columns;
        List<String> logicalColumnNames = table.getColumnNames();
        List<Long> underlyingColumns = new ArrayList<>();
        for ( int i = 0; i < columns.size(); i++ ) {
            for ( AlgDataTypeField algDataTypeField : fieldList.getFieldList() ) {
                String name = logicalColumnNames.get( i );
                if ( algDataTypeField.getName().equals( name ) ) {
                    underlyingColumns.add( columns.get( i ).id );
                }
            }
        }
        return underlyingColumns;
    }


    @Override
    public void createTable( long namespaceId, String name, List<FieldInformation> fields, List<ConstraintInformation> constraints, boolean ifNotExists, List<DataStore> stores, PlacementType placementType, Statement statement ) throws EntityAlreadyExistsException {
        name = adjustNameIfNeeded( name, namespaceId );

        try {
            // Check if there is already an entity with this name
            if ( assertEntityExists( namespaceId, name, ifNotExists ) ) {
                return;
            }

            fields = new ArrayList<>( fields );
            constraints = new ArrayList<>( constraints );

            checkDocumentModel( namespaceId, fields, constraints );

            boolean foundPk = false;
            for ( ConstraintInformation constraintInformation : constraints ) {
                if ( constraintInformation.type == ConstraintType.PRIMARY ) {
                    if ( foundPk ) {
                        throw new RuntimeException( "More than one primary key has been provided!" );
                    } else {
                        foundPk = true;
                    }
                }
            }
            if ( !foundPk ) {
                throw new RuntimeException( "No primary key has been provided!" );
            }

            if ( stores == null ) {
                // Ask router on which store(s) the table should be placed
                stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewEntity();
            }

            long tableId = catalog.getLogicalRel( namespaceId ).addTable(
                    name,
                    EntityType.ENTITY,
                    true );

            // Initially create DataPlacement containers on every store the table should be placed.
            stores.forEach( store -> catalog.getAllocRel( namespaceId ).addDataPlacement( store.getAdapterId(), tableId ) );

            for ( FieldInformation information : fields ) {
                addColumn( namespaceId, information.name, information.typeInformation, information.collation, information.defaultValue, tableId, information.position, stores, placementType );
            }

            for ( ConstraintInformation constraint : constraints ) {
                addConstraint( namespaceId, constraint.name, constraint.type, constraint.columnNames, tableId );
            }

            LogicalTable catalogTable = catalog.getSnapshot().getRelSnapshot( namespaceId ).getTable( tableId );

            // Trigger rebuild of schema; triggers schema creation on adapters
            Catalog.getInstance().getSnapshot();

            for ( DataStore store : stores ) {
                catalog.getAllocRel( catalogTable.namespaceId ).addPartitionPlacement(
                        catalogTable.namespaceId,
                        store.getAdapterId(),
                        catalogTable.id,
                        catalogTable.partitionProperty.partitionIds.get( 0 ),
                        PlacementType.AUTOMATIC,
                        DataPlacementRole.UPTODATE );

                catalog.getPhysical( catalogTable.namespaceId ).addPhysicalEntity(
                        store.createPhysicalTable( statement.getPrepareContext(), catalogTable, null ) );
            }

        } catch ( GenericCatalogException | UnknownColumnException | UnknownCollationException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void createCollection( long namespaceId, String name, boolean ifNotExists, List<DataStore> stores, PlacementType placementType, Statement statement ) throws EntityAlreadyExistsException {
        name = adjustNameIfNeeded( name, namespaceId );

        if ( assertEntityExists( namespaceId, name, ifNotExists ) ) {
            return;
        }

        if ( stores == null ) {
            // Ask router on which store(s) the table should be placed
            stores = RoutingManager.getInstance().getCreatePlacementStrategy().getDataStoresForNewEntity();
        }

        long collectionId;
        try {
            collectionId = catalog.getAllocDoc( namespaceId ).addCollectionLogistics( namespaceId, name, stores, false );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }

        catalog.getLogicalDoc( namespaceId ).addCollection(
                collectionId,
                name,
                EntityType.ENTITY,
                true );

        // Initially create DataPlacement containers on every store the table should be placed.
        LogicalCollection catalogCollection = catalog.getSnapshot().getDocSnapshot( namespaceId ).getCollection( collectionId );

        // Trigger rebuild of schema; triggers schema creation on adapters
        Catalog.getInstance().getSnapshot();

        for ( DataStore store : stores ) {
            catalog.getAllocDoc( namespaceId ).addCollectionPlacement(
                    catalogCollection.namespaceId,
                    store.getAdapterId(),
                    catalogCollection.id,
                    PlacementType.AUTOMATIC );

            afterDocumentLogistics( store, collectionId );

            store.createCollection( statement.getPrepareContext(), catalogCollection, store.getAdapterId() );
        }

    }


    private boolean assertEntityExists( long namespaceId, String name, boolean ifNotExists ) throws EntityAlreadyExistsException {
        // Check if there is already an entity with this name
        if ( catalog.getSnapshot().getRelSnapshot( namespaceId ).checkIfExistsEntity( name ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a table with this name because "IF NOT EXISTS" was specified
                return true;
            } else {
                throw new EntityAlreadyExistsException();
            }
        }
        return false;
    }


    @Override
    public void dropCollection( LogicalCollection catalogCollection, Statement statement ) {
        AdapterManager manager = AdapterManager.getInstance();

        for ( long adapterId : catalogCollection.placements ) {
            DataStore store = (DataStore) manager.getAdapter( adapterId );

            store.dropCollection( statement.getPrepareContext(), catalogCollection );
        }
        catalog.getLogicalDoc( catalogCollection.namespaceId ).deleteCollection( catalogCollection.id );
        removeDocumentLogistics( catalogCollection, statement );
    }


    public void removeDocumentLogistics( LogicalCollection catalogCollection, Statement statement ) {
        CatalogCollectionMapping mapping = catalog.getAllocDoc( catalogCollection.namespaceId ).getCollectionMapping( catalogCollection.id );
        LogicalTable table = catalog.getSnapshot().getRelSnapshot( catalogCollection.namespaceId ).getTable( mapping.collectionId );
        catalog.getLogicalRel( catalogCollection.namespaceId ).deleteTable( table.id );
    }


    @Override
    public void addCollectionPlacement( long namespaceId, String name, List<DataStore> stores, Statement statement ) {
        long collectionId;
        collectionId = catalog.getLogicalDoc( namespaceId ).addCollectionLogistics( name, stores, true );

        // Initially create DataPlacement containers on every store the table should be placed.
        LogicalCollection catalogCollection = catalog.getSnapshot().getDocSnapshot( namespaceId ).getCollection( collectionId );

        // Trigger rebuild of schema; triggers schema creation on adapters
        Catalog.getInstance().getSnapshot();

        for ( DataStore store : stores ) {
            catalog.getAllocDoc( namespaceId ).addCollectionPlacement(
                    catalogCollection.namespaceId, store.getAdapterId(),
                    catalogCollection.id,
                    PlacementType.AUTOMATIC );

            afterDocumentLogistics( store, collectionId );

            store.createCollection( statement.getPrepareContext(), catalogCollection, store.getAdapterId() );
        }
    }


    @Override
    public void dropCollectionPlacement( long namespaceId, LogicalCollection collection, List<DataStore> dataStores, Statement statement ) {
        for ( DataStore store : dataStores ) {
            store.dropCollection( statement.getPrepareContext(), collection );

            catalog.getAllocDoc( namespaceId ).dropCollectionPlacement( collection.id, store.getAdapterId() );

            if ( !store.getSupportedSchemaType().contains( NamespaceType.DOCUMENT ) ) {
                removeDocumentPlacementLogistics( collection, store, statement );
            }
        }

    }


    private void removeDocumentPlacementLogistics( LogicalCollection collection, DataStore store, Statement statement ) {

        /*CatalogCollectionMapping mapping = catalog.getAllocDoc( collection.namespaceId ).getCollectionMapping( collection.id );
        LogicalTable table = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.collectionId );
        try {
            dropDataPlacement( table, store, statement );
        } catch ( PlacementNotExistsException | LastPlacementException e ) {
            throw new RuntimeException( e );
        }*/
    }


    private void afterDocumentLogistics( DataStore store, long collectionId ) {
        /*CatalogCollectionMapping mapping = catalog.getLogicalRel( catalogTable.namespaceId ).getCollectionMapping( collectionId );
        LogicalTable table = catalog.getLogicalRel( catalogTable.namespaceId ).getTable( mapping.collectionId );

        catalog.getLogicalRel( catalogTable.namespaceId ).addDataPlacement( store.getAdapterId(), collectionId );

        catalog.getLogicalRel( catalogTable.namespaceId ).addPartitionPlacement(
                table.namespaceId,
                store.getAdapterId(),
                table.id,
                table.partitionProperty.partitionIds.get( 0 ),
                PlacementType.AUTOMATIC,
                null,
                null,
                DataPlacementRole.UPTODATE
        );*/
    }


    private void checkDocumentModel( long namespaceId, List<FieldInformation> columns, List<ConstraintInformation> constraints ) {
        if ( catalog.getSnapshot().getNamespace( namespaceId ).namespaceType == NamespaceType.DOCUMENT ) {
            List<String> names = columns.stream().map( c -> c.name ).collect( Collectors.toList() );

            if ( names.contains( "_id" ) ) {
                int index = names.indexOf( "_id" );
                columns.remove( index );
                constraints.remove( index );
                names.remove( "_id" );
            }

            // Add _id column if necessary
            if ( !names.contains( "_id" ) ) {
                ColumnTypeInformation typeInformation = new ColumnTypeInformation( PolyType.VARCHAR, PolyType.VARCHAR, 24, null, null, null, false );
                columns.add( new FieldInformation( "_id", typeInformation, Collation.CASE_INSENSITIVE, null, 0 ) );

            }

            // Remove any primaries
            List<ConstraintInformation> primaries = constraints.stream().filter( c -> c.type == ConstraintType.PRIMARY ).collect( Collectors.toList() );
            if ( primaries.size() > 0 ) {
                primaries.forEach( constraints::remove );
            }

            // Add constraint for _id as primary if necessary
            if ( constraints.stream().noneMatch( c -> c.type == ConstraintType.PRIMARY ) ) {
                constraints.add( new ConstraintInformation( "primary", ConstraintType.PRIMARY, Collections.singletonList( "_id" ) ) );
            }

            if ( names.contains( "_data" ) ) {
                columns.remove( names.indexOf( "_data" ) );
                names.remove( "_data" );
            }

            // Add _data column if necessary
            if ( !names.contains( "_data" ) ) {
                ColumnTypeInformation typeInformation = new ColumnTypeInformation( PolyType.JSON, PolyType.JSON, 1024, null, null, null, false );//new ColumnTypeInformation( PolyType.JSON, PolyType.JSON, 1024, null, null, null, false );
                columns.add( new FieldInformation( "_data", typeInformation, Collation.CASE_INSENSITIVE, null, 1 ) );
            }
        }
    }


    @Override
    public void addPartitioning( PartitionInformation partitionInfo, List<DataStore> stores, Statement statement ) throws GenericCatalogException, UnknownPartitionTypeException, UnknownColumnException, PartitionGroupNamesNotUniqueException, UnknownTableException, TransactionException, UnknownSchemaException, UnknownUserException, UnknownKeyException {
        LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( partitionInfo.table.namespaceId ).getColumn( partitionInfo.table.id, partitionInfo.columnName );

        PartitionType actualPartitionType = PartitionType.getByName( partitionInfo.typeName );

        // Convert partition names and check whether they are unique
        List<String> sanitizedPartitionGroupNames = partitionInfo.partitionGroupNames
                .stream()
                .map( name -> name.trim().toLowerCase() )
                .collect( Collectors.toList() );
        if ( sanitizedPartitionGroupNames.size() != new HashSet<>( sanitizedPartitionGroupNames ).size() ) {
            throw new PartitionGroupNamesNotUniqueException();
        }

        // Check if specified partitionColumn is even part of the table
        if ( log.isDebugEnabled() ) {
            log.debug( "Creating partition group for table: {} with id {} on schema: {} on column: {}", partitionInfo.table.name, partitionInfo.table.id, partitionInfo.table.getNamespaceName(), logicalColumn.id );
        }

        LogicalTable unPartitionedTable = partitionInfo.table;

        // Get partition manager
        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( actualPartitionType );

        // Check whether partition function supports type of partition column
        if ( !partitionManager.supportsColumnOfType( logicalColumn.type ) ) {
            throw new RuntimeException( "The partition function " + actualPartitionType + " does not support columns of type " + logicalColumn.type );
        }

        int numberOfPartitionGroups = partitionInfo.numberOfPartitionGroups;
        // Calculate how many partitions exist if partitioning is applied.
        long partId;
        if ( partitionInfo.partitionGroupNames.size() >= 2 && partitionInfo.numberOfPartitionGroups == 0 ) {
            numberOfPartitionGroups = partitionInfo.partitionGroupNames.size();
        }

        int numberOfPartitions = partitionInfo.numberOfPartitions;
        int numberOfPartitionsPerGroup = partitionManager.getNumberOfPartitionsPerGroup( numberOfPartitions );

        if ( partitionManager.requiresUnboundPartitionGroup() ) {
            // Because of the implicit unbound partition
            numberOfPartitionGroups = partitionInfo.partitionGroupNames.size();
            numberOfPartitionGroups += 1;
        }

        // Validate partition setup
        if ( !partitionManager.validatePartitionGroupSetup( partitionInfo.qualifiers, numberOfPartitionGroups, partitionInfo.partitionGroupNames, logicalColumn ) ) {
            throw new RuntimeException( "Partitioning failed for table: " + partitionInfo.table.name );
        }

        // Loop over value to create those partitions with partitionKey to uniquelyIdentify partition
        List<Long> partitionGroupIds = new ArrayList<>();
        for ( int i = 0; i < numberOfPartitionGroups; i++ ) {
            String partitionGroupName;

            // Make last partition unbound partition
            if ( partitionManager.requiresUnboundPartitionGroup() && i == numberOfPartitionGroups - 1 ) {
                partId = catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartitionGroup(
                        partitionInfo.table.id,
                        "Unbound",
                        partitionInfo.table.namespaceId,
                        actualPartitionType,
                        numberOfPartitionsPerGroup,
                        new ArrayList<>(),
                        true );
            } else {
                // If no names have been explicitly defined
                if ( partitionInfo.partitionGroupNames.isEmpty() ) {
                    partitionGroupName = "part_" + i;
                } else {
                    partitionGroupName = partitionInfo.partitionGroupNames.get( i );
                }

                // Mainly needed for HASH
                if ( partitionInfo.qualifiers.isEmpty() ) {
                    partId = catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartitionGroup(
                            partitionInfo.table.id,
                            partitionGroupName,
                            partitionInfo.table.namespaceId,
                            actualPartitionType,
                            numberOfPartitionsPerGroup,
                            new ArrayList<>(),
                            false );
                } else {
                    partId = catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartitionGroup(
                            partitionInfo.table.id,
                            partitionGroupName,
                            partitionInfo.table.namespaceId,
                            actualPartitionType,
                            numberOfPartitionsPerGroup,
                            partitionInfo.qualifiers.get( i ),
                            false );
                }
            }
            partitionGroupIds.add( partId );
        }

        List<Long> partitionIds = new ArrayList<>();
        //get All PartitionGroups and then get all partitionIds  for each PG and add them to completeList of partitionIds
        //catalog.getLogicalRel( catalogTable.namespaceId ).getPartitionGroups( partitionInfo.table.id ).forEach( pg -> partitionIds.forEach( p -> partitionIds.add( p ) ) );
        partitionGroupIds.forEach( pg -> catalog.getSnapshot().getAllocSnapshot().getPartitions( pg ).forEach( p -> partitionIds.add( p.id ) ) );

        PartitionProperty partitionProperty;
        if ( actualPartitionType == PartitionType.TEMPERATURE ) {
            long frequencyInterval = ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getInterval();
            switch ( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getIntervalUnit().toString() ) {
                case "days":
                    frequencyInterval = frequencyInterval * 60 * 60 * 24;
                    break;

                case "hours":
                    frequencyInterval = frequencyInterval * 60 * 60;
                    break;

                case "minutes":
                    frequencyInterval = frequencyInterval * 60;
                    break;
            }

            int hotPercentageIn = Integer.parseInt( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getHotAccessPercentageIn().toString() );
            int hotPercentageOut = Integer.parseInt( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getHotAccessPercentageOut().toString() );

            //Initially distribute partitions as intended in a running system
            long numberOfPartitionsInHot = (long) numberOfPartitions * hotPercentageIn / 100;
            if ( numberOfPartitionsInHot == 0 ) {
                numberOfPartitionsInHot = 1;
            }

            long numberOfPartitionsInCold = numberOfPartitions - numberOfPartitionsInHot;

            // -1 because one partition is already created in COLD
            List<Long> partitionsForHot = new ArrayList<>();
            catalog.getSnapshot().getAllocSnapshot().getPartitions( partitionGroupIds.get( 0 ) ).forEach( p -> partitionsForHot.add( p.id ) );

            // -1 because one partition is already created in HOT
            for ( int i = 0; i < numberOfPartitionsInHot - 1; i++ ) {
                long tempId;
                tempId = catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartition( partitionInfo.table.id, partitionInfo.table.namespaceId, partitionGroupIds.get( 0 ), partitionInfo.qualifiers.get( 0 ), false );
                partitionIds.add( tempId );
                partitionsForHot.add( tempId );
            }

            catalog.getAllocRel( partitionInfo.table.namespaceId ).updatePartitionGroup( partitionGroupIds.get( 0 ), partitionsForHot );

            // -1 because one partition is already created in COLD
            List<Long> partitionsForCold = new ArrayList<>();
            catalog.getSnapshot().getAllocSnapshot().getPartitions( partitionGroupIds.get( 1 ) ).forEach( p -> partitionsForCold.add( p.id ) );

            for ( int i = 0; i < numberOfPartitionsInCold - 1; i++ ) {
                long tempId;
                tempId = catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartition( partitionInfo.table.id, partitionInfo.table.namespaceId, partitionGroupIds.get( 1 ), partitionInfo.qualifiers.get( 1 ), false );
                partitionIds.add( tempId );
                partitionsForCold.add( tempId );
            }

            catalog.getAllocRel( partitionInfo.table.namespaceId ).updatePartitionGroup( partitionGroupIds.get( 1 ), partitionsForCold );

            partitionProperty = TemperaturePartitionProperty.builder()
                    .partitionType( actualPartitionType )
                    .isPartitioned( true )
                    .internalPartitionFunction( PartitionType.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getInternalPartitionFunction().toString().toUpperCase() ) )
                    .partitionColumnId( logicalColumn.id )
                    .partitionGroupIds( ImmutableList.copyOf( partitionGroupIds ) )
                    .partitionIds( ImmutableList.copyOf( partitionIds ) )
                    .partitionCostIndication( PartitionCostIndication.valueOf( ((RawTemperaturePartitionInformation) partitionInfo.rawPartitionInformation).getAccessPattern().toString().toUpperCase() ) )
                    .frequencyInterval( frequencyInterval )
                    .hotAccessPercentageIn( hotPercentageIn )
                    .hotAccessPercentageOut( hotPercentageOut )
                    .reliesOnPeriodicChecks( true )
                    .hotPartitionGroupId( partitionGroupIds.get( 0 ) )
                    .coldPartitionGroupId( partitionGroupIds.get( 1 ) )
                    .numPartitions( partitionIds.size() )
                    .numPartitionGroups( partitionGroupIds.size() )
                    .build();
        } else {
            partitionProperty = PartitionProperty.builder()
                    .partitionType( actualPartitionType )
                    .isPartitioned( true )
                    .partitionColumnId( logicalColumn.id )
                    .partitionGroupIds( ImmutableList.copyOf( partitionGroupIds ) )
                    .partitionIds( ImmutableList.copyOf( partitionIds ) )
                    .reliesOnPeriodicChecks( false )
                    .build();
        }

        // Update catalog table
        catalog.getAllocRel( partitionInfo.table.namespaceId ).partitionTable( partitionInfo.table.id, actualPartitionType, logicalColumn.id, numberOfPartitionGroups, partitionGroupIds, partitionProperty );

        // Get primary key of table and use PK to find all DataPlacements of table
        long pkid = partitionInfo.table.primaryKey;
        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( partitionInfo.table.namespaceId );
        List<Long> pkColumnIds = snapshot.getPrimaryKey( pkid ).columnIds;
        // Basically get first part of PK even if its compound of PK it is sufficient
        LogicalColumn pkColumn = snapshot.getColumn( pkColumnIds.get( 0 ) );
        // This gets us only one ccp per store (first part of PK)

        boolean fillStores = false;
        if ( stores == null ) {
            stores = new ArrayList<>();
            fillStores = true;
        }
        List<CatalogColumnPlacement> catalogColumnPlacements = catalog.getSnapshot().getAllocSnapshot().getColumnPlacements( pkColumn.id );
        for ( CatalogColumnPlacement ccp : catalogColumnPlacements ) {
            if ( fillStores ) {
                // Ask router on which store(s) the table should be placed
                Adapter adapter = AdapterManager.getInstance().getAdapter( ccp.adapterId );
                if ( adapter instanceof DataStore ) {
                    stores.add( (DataStore) adapter );
                }
            }
        }

        // Now get the partitioned table, partitionInfo still contains the basic/unpartitioned table.
        LogicalTable partitionedTable = snapshot.getTable( partitionInfo.table.id );
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        for ( DataStore store : stores ) {
            for ( long partitionId : partitionIds ) {
                catalog.getAllocRel( partitionInfo.table.namespaceId ).addPartitionPlacement(
                        partitionedTable.namespaceId,
                        store.getAdapterId(),
                        partitionedTable.id,
                        partitionId,
                        PlacementType.AUTOMATIC,
                        DataPlacementRole.UPTODATE );
            }

            // First create new tables
            store.createPhysicalTable( statement.getPrepareContext(), partitionedTable, null );

            // Copy data from unpartitioned to partitioned
            // Get only columns that are actually on that store
            // Every store of a newly partitioned table, initially will hold all partitions
            List<LogicalColumn> necessaryColumns = new LinkedList<>();
            catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapterPerTable( store.getAdapterId(), partitionedTable.id ).forEach( cp -> necessaryColumns.add( snapshot.getColumn( cp.columnId ) ) );

            // Copy data from the old partition to new partitions
            dataMigrator.copyPartitionData(
                    statement.getTransaction(),
                    catalog.getSnapshot().getAdapter( store.getAdapterId() ),
                    unPartitionedTable,
                    partitionedTable,
                    necessaryColumns,
                    unPartitionedTable.partitionProperty.partitionIds,
                    partitionedTable.partitionProperty.partitionIds );
        }

        // Adjust indexes
        List<CatalogIndex> indexes = snapshot.getIndexes( unPartitionedTable.id, false );
        for ( CatalogIndex index : indexes ) {
            // Remove old index
            DataStore ds = ((DataStore) AdapterManager.getInstance().getAdapter( index.location ));
            ds.dropIndex( statement.getPrepareContext(), index, unPartitionedTable.partitionProperty.partitionIds );
            catalog.getLogicalRel( partitionInfo.table.namespaceId ).deleteIndex( index.id );
            // Add new index
            long newIndexId = catalog.getLogicalRel( partitionInfo.table.namespaceId ).addIndex(
                    partitionedTable.id,
                    index.key.columnIds,
                    index.unique,
                    index.method,
                    index.methodDisplayName,
                    index.location,
                    index.type,
                    index.name );
            if ( index.location == 0 ) {
                IndexManager.getInstance().addIndex( snapshot.getIndex( newIndexId ), statement );
            } else {
                ds.addIndex(
                        statement.getPrepareContext(),
                        snapshot.getIndex( newIndexId ),
                        catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( ds.getAdapterId(), unPartitionedTable.id ) );
            }
        }

        // Remove old tables
        stores.forEach( store -> store.dropTable( statement.getPrepareContext(), unPartitionedTable, unPartitionedTable.partitionProperty.partitionIds ) );
        catalog.getAllocRel( partitionInfo.table.namespaceId ).deletePartitionGroup( unPartitionedTable.id, unPartitionedTable.namespaceId, unPartitionedTable.partitionProperty.partitionGroupIds.get( 0 ) );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void removePartitioning( LogicalTable partitionedTable, Statement statement ) throws GenericCatalogException, UnknownTableException, TransactionException, UnknownSchemaException, UnknownUserException, UnknownKeyException {
        long tableId = partitionedTable.id;

        if ( log.isDebugEnabled() ) {
            log.debug( "Merging partitions for table: {} with id {} on schema: {}",
                    partitionedTable.name, partitionedTable.id, partitionedTable.getNamespaceName() );
        }

        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( partitionedTable.namespaceId );

        // Need to gather the partitionDistribution before actually merging
        // We need a columnPlacement for every partition
        Map<Long, List<CatalogColumnPlacement>> placementDistribution = new HashMap<>();
        PartitionManagerFactory partitionManagerFactory = PartitionManagerFactory.getInstance();
        PartitionManager partitionManager = partitionManagerFactory.getPartitionManager( partitionedTable.partitionProperty.partitionType );
        placementDistribution = partitionManager.getRelevantPlacements( partitionedTable, partitionedTable.partitionProperty.partitionIds, new ArrayList<>( List.of( -1L ) ) );

        // Update catalog table
        catalog.getAllocRel( partitionedTable.namespaceId ).mergeTable( tableId );

        // Now get the merged table
        LogicalTable mergedTable = snapshot.getTable( tableId );

        List<DataStore> stores = new ArrayList<>();
        // Get primary key of table and use PK to find all DataPlacements of table
        long pkid = partitionedTable.primaryKey;
        List<Long> pkColumnIds = snapshot.getPrimaryKey( pkid ).columnIds;
        // Basically get first part of PK even if its compound of PK it is sufficient
        LogicalColumn pkColumn = snapshot.getColumn( pkColumnIds.get( 0 ) );
        // This gets us only one ccp per store (first part of PK)

        List<CatalogColumnPlacement> catalogColumnPlacements = catalog.getSnapshot().getAllocSnapshot().getColumnPlacements( pkColumn.id );
        for ( CatalogColumnPlacement ccp : catalogColumnPlacements ) {
            // Ask router on which store(s) the table should be placed
            Adapter adapter = AdapterManager.getInstance().getAdapter( ccp.adapterId );
            if ( adapter instanceof DataStore ) {
                stores.add( (DataStore) adapter );
            }
        }

        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();

        // For merge create only full placements on the used stores. Otherwise partition constraints might not hold
        for ( DataStore store : stores ) {
            // Need to create partitionPlacements first in order to trigger schema creation on PolySchemaBuilder
            catalog.getAllocRel( partitionedTable.namespaceId ).addPartitionPlacement(
                    mergedTable.namespaceId,
                    store.getAdapterId(),
                    mergedTable.id,
                    mergedTable.partitionProperty.partitionIds.get( 0 ),
                    PlacementType.AUTOMATIC,
                    DataPlacementRole.UPTODATE );

            // First create new tables
            store.createPhysicalTable( statement.getPrepareContext(), mergedTable, null );

            // Get only columns that are actually on that store
            List<LogicalColumn> necessaryColumns = new LinkedList<>();
            catalog.getSnapshot().getAllocSnapshot().getColumnPlacementsOnAdapterPerTable( store.getAdapterId(), mergedTable.id ).forEach( cp -> necessaryColumns.add( snapshot.getColumn( cp.columnId ) ) );

            // TODO @HENNLO Check if this can be omitted
            catalog.getAllocRel( partitionedTable.namespaceId ).updateDataPlacement(
                    store.getAdapterId(),
                    mergedTable.id,
                    catalog.getSnapshot().getAllocSnapshot().getDataPlacement( store.getAdapterId(), mergedTable.id ).columnPlacementsOnAdapter,
                    mergedTable.partitionProperty.partitionIds );
            //

            dataMigrator.copySelectiveData(
                    statement.getTransaction(),
                    catalog.getSnapshot().getAdapter( store.getAdapterId() ),
                    partitionedTable,
                    mergedTable,
                    necessaryColumns,
                    placementDistribution,
                    mergedTable.partitionProperty.partitionIds );
        }

        // Adjust indexes
        List<CatalogIndex> indexes = snapshot.getIndexes( partitionedTable.id, false );
        for ( CatalogIndex index : indexes ) {
            // Remove old index
            DataStore ds = (DataStore) AdapterManager.getInstance().getAdapter( index.location );
            ds.dropIndex( statement.getPrepareContext(), index, partitionedTable.partitionProperty.partitionIds );
            catalog.getLogicalRel( partitionedTable.namespaceId ).deleteIndex( index.id );
            // Add new index
            long newIndexId = catalog.getLogicalRel( partitionedTable.namespaceId ).addIndex(
                    mergedTable.id,
                    index.key.columnIds,
                    index.unique,
                    index.method,
                    index.methodDisplayName,
                    index.location,
                    index.type,
                    index.name );
            if ( index.location == 0 ) {
                IndexManager.getInstance().addIndex( snapshot.getIndex( newIndexId ), statement );
            } else {
                ds.addIndex(
                        statement.getPrepareContext(),
                        snapshot.getIndex( newIndexId ),
                        catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( ds.getAdapterId(), mergedTable.id ) );
            }
        }

        // Needs to be separated from loop above. Otherwise we loose data
        for ( DataStore store : stores ) {
            List<Long> partitionIdsOnStore = new ArrayList<>();
            catalog.getSnapshot().getAllocSnapshot().getPartitionPlacementsByTableOnAdapter( store.getAdapterId(), partitionedTable.id ).forEach( p -> partitionIdsOnStore.add( p.partitionId ) );
            // Otherwise everything will be dropped again, leaving the table inaccessible
            partitionIdsOnStore.remove( mergedTable.partitionProperty.partitionIds.get( 0 ) );

            // Drop all partitionedTables (table contains old partitionIds)
            store.dropTable( statement.getPrepareContext(), partitionedTable, partitionIdsOnStore );
        }
        // Loop over **old.partitionIds** to delete all partitions which are part of table
        // Needs to be done separately because partitionPlacements will be recursively dropped in `deletePartitionGroup` but are needed in dropTable
        for ( long partitionGroupId : partitionedTable.partitionProperty.partitionGroupIds ) {
            catalog.getAllocRel( partitionedTable.namespaceId ).deletePartitionGroup( tableId, partitionedTable.namespaceId, partitionGroupId );
        }

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    private void addColumn( long namespaceId, String columnName, ColumnTypeInformation typeInformation, Collation collation, String defaultValue, long tableId, int position, List<DataStore> stores, PlacementType placementType ) throws GenericCatalogException, UnknownCollationException, UnknownColumnException {
        columnName = adjustNameIfNeeded( columnName, catalog.getSnapshot().getRelSnapshot( namespaceId ).getTable( tableId ).namespaceId );
        long addedColumnId = catalog.getLogicalRel( namespaceId ).addColumn(
                columnName,
                tableId,
                position,
                typeInformation.type,
                typeInformation.collectionType,
                typeInformation.precision,
                typeInformation.scale,
                typeInformation.dimension,
                typeInformation.cardinality,
                typeInformation.nullable,
                collation
        );

        // Add default value
        addDefaultValue( namespaceId, defaultValue, addedColumnId );

        for ( DataStore s : stores ) {
            catalog.getAllocRel( namespaceId ).addColumnPlacement( catalog.getLogicalEntity( tableId ).unwrap( LogicalTable.class ),
                    s.getAdapterId(),
                    addedColumnId,
                    placementType,
                    null,
                    null, null, position );
        }
    }


    @Override
    public void addConstraint( long namespaceId, String constraintName, ConstraintType constraintType, List<String> columnNames, long tableId ) throws UnknownColumnException, GenericCatalogException {
        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            LogicalColumn logicalColumn = catalog.getSnapshot().getRelSnapshot( namespaceId ).getColumn( tableId, columnName );
            columnIds.add( logicalColumn.id );
        }
        if ( constraintType == ConstraintType.PRIMARY ) {
            catalog.getLogicalRel( namespaceId ).addPrimaryKey( tableId, columnIds );
        } else if ( constraintType == ConstraintType.UNIQUE ) {
            if ( constraintName == null ) {
                constraintName = NameGenerator.generateConstraintName();
            }
            catalog.getLogicalRel( namespaceId ).addUniqueConstraint( tableId, constraintName, columnIds );
        }
    }


    @Override
    public void dropNamespace( String schemaName, boolean ifExists, Statement statement ) throws SchemaNotExistException, DdlOnSourceException {
        schemaName = schemaName.toLowerCase();

        // Check if there is a schema with this name
        if ( catalog.getSnapshot().checkIfExistsNamespace( schemaName ) ) {
            LogicalNamespace logicalNamespace = catalog.getSnapshot().getNamespace( schemaName );

            // Drop all collections in this namespace
            List<LogicalCollection> collections = catalog.getSnapshot().getDocSnapshot( logicalNamespace.id ).getCollections( null );
            for ( LogicalCollection collection : collections ) {
                dropCollection( collection, statement );
            }

            // Drop all tables in this schema
            List<LogicalTable> catalogEntities = catalog.getSnapshot().getRelSnapshot( logicalNamespace.id ).getTables( null );
            for ( LogicalTable catalogTable : catalogEntities ) {
                dropTable( catalogTable, statement );
            }

            // Drop schema
            catalog.deleteNamespace( logicalNamespace.id );
        } else {
            if ( ifExists ) {
                // This is ok because "IF EXISTS" was specified
                return;
            } else {
                throw new SchemaNotExistException();
            }
        }
    }


    @Override
    public void dropView( LogicalTable catalogView, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type VIEW
        if ( catalogView.entityType != EntityType.VIEW ) {
            throw new NotViewException();
        }

        // Check if views are dependent from this view
        checkViewDependencies( catalogView );

        catalog.getLogicalRel( catalogView.namespaceId ).flagTableForDeletion( catalogView.id, true );
        catalog.getLogicalRel( catalogView.namespaceId ).deleteViewDependencies( (CatalogView) catalogView );

        // Delete columns
        for ( LogicalColumn column : catalogView.columns ) {
            catalog.getLogicalRel( catalogView.namespaceId ).deleteColumn( column.id );
        }

        // Delete the view
        catalog.getLogicalRel( catalogView.namespaceId ).deleteTable( catalogView.id );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropMaterializedView( LogicalTable materializedView, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type Materialized View
        if ( materializedView.entityType == EntityType.MATERIALIZED_VIEW ) {
            // Empty on purpose
        } else {
            throw new NotMaterializedViewException();
        }
        // Check if views are dependent from this view
        checkViewDependencies( materializedView );

        catalog.getLogicalRel( materializedView.namespaceId ).flagTableForDeletion( materializedView.id, true );

        catalog.getLogicalRel( materializedView.namespaceId ).deleteViewDependencies( (CatalogView) materializedView );

        dropTable( materializedView, statement );

        // Reset query plan cache, implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void dropTable( LogicalTable catalogTable, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        //checkIfDdlPossible( catalogEntity.tableType );

        // Check if views dependent on this table
        checkViewDependencies( catalogTable );

        // Check if there are foreign keys referencing this table
        List<CatalogForeignKey> selfRefsToDelete = new LinkedList<>();
        LogicalRelSnapshot snapshot = catalog.getSnapshot().getRelSnapshot( catalogTable.namespaceId );
        List<CatalogForeignKey> exportedKeys = snapshot.getExportedKeys( catalogTable.id );
        if ( exportedKeys.size() > 0 ) {
            for ( CatalogForeignKey foreignKey : exportedKeys ) {
                if ( foreignKey.tableId == catalogTable.id ) {
                    // If this is a self-reference, drop it later.
                    selfRefsToDelete.add( foreignKey );
                } else {
                    throw new PolyphenyDbException( "Cannot drop table '" + catalogTable.getNamespaceName() + "." + catalogTable.name + "' because it is being referenced by '" + exportedKeys.get( 0 ).getSchemaName() + "." + exportedKeys.get( 0 ).getTableName() + "'." );
                }
            }
        }

        // Make sure that all adapters are of type store (and not source)
        for ( long storeId : catalogTable.dataPlacements ) {
            getDataStoreInstance( storeId );
        }

        // Delete all indexes
        for ( CatalogIndex index : snapshot.getIndexes( catalogTable.id, false ) ) {
            if ( index.location == 0 ) {
                // Delete polystore index
                IndexManager.getInstance().deleteIndex( index );
            } else {
                // Delete index on store
                AdapterManager.getInstance().getStore( index.location ).dropIndex(
                        statement.getPrepareContext(),
                        index,
                        catalog.getSnapshot().getAllocSnapshot().getPartitionsOnDataPlacement( index.location, catalogTable.id ) );
            }
            // Delete index in catalog
            catalog.getLogicalRel( catalogTable.namespaceId ).deleteIndex( index.id );
        }

        // Delete data from the stores and remove the column placement
        catalog.getLogicalRel( catalogTable.namespaceId ).flagTableForDeletion( catalogTable.id, true );
        for ( long storeId : catalogTable.dataPlacements ) {
            // Delete table on store
            List<Long> partitionIdsOnStore = new ArrayList<>();
            catalog.getSnapshot().getAllocSnapshot().getPartitionPlacementsByTableOnAdapter( storeId, catalogTable.id ).forEach( p -> partitionIdsOnStore.add( p.partitionId ) );

            AdapterManager.getInstance().getStore( storeId ).dropTable( statement.getPrepareContext(), catalogTable, partitionIdsOnStore );
            // Delete column placement in catalog
            for ( LogicalColumn column : catalogTable.columns ) {
                if ( catalog.getSnapshot().getAllocSnapshot().checkIfExistsColumnPlacement( storeId, column.id ) ) {
                    catalog.getAllocRel( catalogTable.namespaceId ).deleteColumnPlacement( storeId, column.id, false );
                }
            }
        }

        // Delete the self-referencing foreign keys
        try {
            for ( CatalogForeignKey foreignKey : selfRefsToDelete ) {
                catalog.getLogicalRel( catalogTable.namespaceId ).deleteForeignKey( foreignKey.id );
            }
        } catch ( GenericCatalogException e ) {
            catalog.getLogicalRel( catalogTable.namespaceId ).flagTableForDeletion( catalogTable.id, true );
            throw new PolyphenyDbContextException( "Exception while deleting self-referencing foreign key constraints.", e );
        }

        // Delete indexes of this table
        List<CatalogIndex> indexes = snapshot.getIndexes( catalogTable.id, false );
        for ( CatalogIndex index : indexes ) {
            catalog.getLogicalRel( catalogTable.namespaceId ).deleteIndex( index.id );
            IndexManager.getInstance().deleteIndex( index );
        }

        // Delete keys and constraints
        try {
            // Remove primary key
            catalog.getLogicalRel( catalogTable.namespaceId ).deletePrimaryKey( catalogTable.id );
            // Delete all foreign keys of the table
            List<CatalogForeignKey> foreignKeys = snapshot.getForeignKeys( catalogTable.id );
            for ( CatalogForeignKey foreignKey : foreignKeys ) {
                catalog.getLogicalRel( catalogTable.namespaceId ).deleteForeignKey( foreignKey.id );
            }
            // Delete all constraints of the table
            for ( CatalogConstraint constraint : snapshot.getConstraints( catalogTable.id ) ) {
                catalog.getLogicalRel( catalogTable.namespaceId ).deleteConstraint( constraint.id );
            }
        } catch ( GenericCatalogException e ) {
            catalog.getLogicalRel( catalogTable.namespaceId ).flagTableForDeletion( catalogTable.id, true );
            throw new PolyphenyDbContextException( "Exception while dropping keys.", e );
        }

        // Delete columns
        for ( LogicalColumn column : catalogTable.columns ) {
            catalog.getLogicalRel( catalogTable.namespaceId ).deleteColumn( column.id );
        }

        // Delete the table
        catalog.getLogicalRel( catalogTable.namespaceId ).deleteTable( catalogTable.id );

        // Monitor dropTables for statistics
        prepareMonitoring( statement, Kind.DROP_TABLE, catalogTable );

        // ON_COMMIT constraint needs no longer to be enforced if entity does no longer exist
        statement.getTransaction().getCatalogTables().remove( catalogTable );

        // Reset plan cache implementation cache & routing cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void truncate( LogicalTable catalogTable, Statement statement ) {
        // Make sure that the table can be modified
        if ( !catalogTable.modifiable ) {
            throw new RuntimeException( "Unable to modify a read-only table!" );
        }

        // Monitor truncate for rowCount
        prepareMonitoring( statement, Kind.TRUNCATE, catalogTable );

        //  Execute truncate on all placements
        catalogTable.dataPlacements.forEach( adapterId -> {
            AdapterManager.getInstance().getAdapter( adapterId ).truncate( statement.getPrepareContext(), catalogTable );
        } );
    }


    private void prepareMonitoring( Statement statement, Kind kind, LogicalTable catalogTable ) {
        prepareMonitoring( statement, kind, catalogTable, null );
    }


    private void prepareMonitoring( Statement statement, Kind kind, LogicalTable catalogTable, LogicalColumn logicalColumn ) {
        // Initialize Monitoring
        if ( statement.getMonitoringEvent() == null ) {
            StatementEvent event = new DdlEvent();
            event.setMonitoringType( kind.name() );
            event.setTableId( catalogTable.id );
            event.setSchemaId( catalogTable.namespaceId );
            if ( kind == Kind.DROP_COLUMN ) {
                event.setColumnId( logicalColumn.id );
            }
            statement.setMonitoringEvent( event );
        }
    }


    @Override
    public void dropFunction() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void setOption() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void createType() {
        throw new RuntimeException( "Not supported yet" );
    }


    @Override
    public void dropType() {
        throw new RuntimeException( "Not supported yet" );
    }

}
