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

package org.polypheny.db.languages.mql;

import java.util.Collections;
import lombok.Getter;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonValue;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.languages.mql.Mql.Type;
import org.polypheny.db.transaction.locking.IdentifierRegistry;
import org.polypheny.db.transaction.locking.IdentifierUtils;


@Getter
public class MqlInsert extends MqlCollectionStatement {

    private final BsonArray values;
    private final boolean ordered;


    public MqlInsert( ParserPos pos, String collection, String namespace, BsonValue values, BsonDocument options ) {
        super( collection, namespace, pos );
        if ( values.isDocument() ) {
            this.values = new BsonArray( Collections.singletonList( values.asDocument() ) );
        } else if ( values.isArray() ) {
            this.values = values.asArray();
        } else {
            throw new GenericRuntimeException( "Insert requires either a single document or multiple documents in an array." );
        }
        insertEntryIdentifiers();
        this.ordered = getBoolean( options, "ordered" );
    }

    private void insertEntryIdentifiers() {
        values.asArray()
                .stream()
                .map(BsonValue::asDocument)
                .forEach(doc -> doc.put(
                        IdentifierUtils.IDENTIFIER_KEY,
                        new BsonInt64( IdentifierRegistry.INSTANCE.getEntryIdentifier())
                ));

    }


    @Override
    public Type getMqlKind() {
        return Type.INSERT;
    }


    @Override
    public @Nullable String getEntity() {
        return getCollection();
    }

}
