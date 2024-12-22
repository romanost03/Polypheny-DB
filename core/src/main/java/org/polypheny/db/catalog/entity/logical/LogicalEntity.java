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

package org.polypheny.db.catalog.entity.logical;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.transaction.mvcc.EntryIdentifierRegistry;
import org.polypheny.db.transaction.locking.CommitInstantsLog;
import org.polypheny.db.transaction.locking.EntryIdentifierRegistry;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Value
@NonFinal
public abstract class LogicalEntity extends Entity {

    public EntryIdentifierRegistry entryIdentifiers;
    public CommitInstantsLog entryCommitInstantsLog;


    public LogicalEntity(
            long id,
            String name,
            long namespaceId,
            EntityType type,
            DataModel dataModel,
            boolean modifiable ) {
        super( id, name, namespaceId, type, dataModel, modifiable );
        this.entryIdentifiers = new EntryIdentifierRegistry( this );
        this.entryCommitInstantsLog = new CommitInstantsLog();
    }


    public State getLayer() {
        return State.LOGICAL;
    }

}
