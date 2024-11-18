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

package org.polypheny.db.workflow.engine.storage;

import java.util.Iterator;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.type.entity.PolyValue;

public class RelReader extends CheckpointReader{

    public RelReader( LogicalEntity entity ) {
        super( entity );
    }


    @Override
    public AlgNode getAlgNode() {
        throw new NotImplementedException();
    }


    @Override
    public Iterator<PolyValue[]> getIterator() {
        throw new NotImplementedException();
    }


    @Override
    public Iterator<PolyValue[]> getIteratorFromQuery( String query ) {
        throw new NotImplementedException();
    }

}
