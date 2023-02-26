/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.monitoring.statistics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.type.PolyType;


/**
 * Boilerplate of a column to guide the handling and pattern of a column
 */
@Slf4j
class QueryResult {

    @Getter
    private final long schemaId;

    @Getter
    private final long tableId;

    @Getter
    private final long columnId;

    @Getter
    private final PolyType type;


    QueryResult( long schemaId, long tableId, Long columnId, PolyType type ) {
        this.schemaId = schemaId;
        this.tableId = tableId;
        this.columnId = columnId;
        this.type = type;

        Catalog catalog = Catalog.getInstance();
        if ( catalog.checkIfExistsEntity( tableId ) ) {
            this.schema = catalog.getSchema( schemaId ).id;
            this.tableId = catalog.getTable( tableId ).id;
            if ( columnId != null ) {
                this.column = catalog.getColumn( columnId ).id;
            }
        }
    }


    public static QueryResult fromCatalogColumn( CatalogColumn column ) {
        return new QueryResult( column.schemaId, column.tableId, column.id, column.type );
    }

}
