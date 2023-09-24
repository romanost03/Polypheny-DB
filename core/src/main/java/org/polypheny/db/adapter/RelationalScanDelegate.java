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

package org.polypheny.db.adapter;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.DocumentType;
import org.polypheny.db.algebra.type.GraphType;
import org.polypheny.db.catalog.catalogs.RelStoreCatalog;
import org.polypheny.db.catalog.entity.allocation.AllocationCollection;
import org.polypheny.db.catalog.entity.allocation.AllocationGraph;
import org.polypheny.db.catalog.entity.allocation.AllocationTableWrapper;
import org.polypheny.db.catalog.entity.logical.LogicalCollection;
import org.polypheny.db.catalog.entity.logical.LogicalGraph;
import org.polypheny.db.catalog.entity.logical.LogicalTableWrapper;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalTable;
import org.polypheny.db.prepare.Context;
import org.polypheny.db.schema.trait.ModelTrait;
import org.polypheny.db.tools.AlgBuilder;

@AllArgsConstructor
public class RelationalScanDelegate implements Scannable {

    public final Scannable scannable;

    @Getter
    public final RelStoreCatalog catalog;


    @Override
    public AlgNode getGraphScan( long allocId, AlgBuilder builder ) {
        builder.clear();
        List<PhysicalEntity> physicals = catalog.getPhysicalsFromAllocs( allocId );
        builder.scan( physicals.get( 0 ) );//node
        builder.scan( physicals.get( 1 ) );//node Props
        builder.scan( physicals.get( 2 ) );//edge
        builder.scan( physicals.get( 3 ) );//edge Props

        builder.transform( ModelTrait.GRAPH, GraphType.of(), false );

        return builder.build();
    }


    @Override
    public AlgNode getDocumentScan( long allocId, AlgBuilder builder ) {
        builder.clear();
        PhysicalTable table = catalog.fromAllocation( allocId );
        builder.scan( table );
        AlgDataType rowType = DocumentType.ofId();
        builder.transform( ModelTrait.DOCUMENT, rowType, false );
        return builder.build();
    }


    @Override
    public void createTable( Context context, LogicalTableWrapper logical, AllocationTableWrapper allocation ) {
        scannable.createTable( context, logical, allocation );
    }


    @Override
    public void refreshTable( long allocId ) {
        scannable.refreshTable( allocId );
    }


    @Override
    public void refreshGraph( long allocId ) {
        List<PhysicalEntity> physicals = catalog.getPhysicalsFromAllocs( allocId );
        scannable.refreshTable( physicals.get( 0 ).allocationId );
        scannable.refreshTable( physicals.get( 1 ).allocationId );
        scannable.refreshTable( physicals.get( 2 ).allocationId );
        scannable.refreshTable( physicals.get( 3 ).allocationId );
    }


    @Override
    public void refreshCollection( long allocId ) {
        scannable.refreshTable( catalog.fromAllocation( allocId ).allocationId );
    }


    @Override
    public void dropTable( Context context, long allocId ) {
        scannable.dropTable( context, allocId );
    }


    @Override
    public void createGraph( Context context, LogicalGraph logical, AllocationGraph allocation ) {
        scannable.createGraph( context, logical, allocation );
    }


    @Override
    public void dropGraph( Context context, AllocationGraph allocation ) {
        scannable.dropGraph( context, allocation );
    }


    @Override
    public void createCollection( Context context, LogicalCollection logical, AllocationCollection allocation ) {
        scannable.createCollection( context, logical, allocation );
    }


    @Override
    public void dropCollection( Context context, AllocationCollection allocation ) {
        scannable.dropCollection( context, allocation );
    }

}
