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

package org.polypheny.db.processing;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.languages.QueryParameters;


@EqualsAndHashCode(callSuper = true)
@Value
public class ExtendedQueryParameters extends QueryParameters {

    public List<String> nodeLabels = new ArrayList<>();
    public List<String> relationshipLabels = new ArrayList<>();
    @NonFinal
    public Long namespaceId;
    public boolean fullGraph;


    public ExtendedQueryParameters( String query, NamespaceType namespaceType, Long namespaceId ) {
        super( query, namespaceType );
        this.namespaceId = namespaceId;
        this.fullGraph = false;
    }


    public ExtendedQueryParameters( Long namespaceId ) {
        super( "*", NamespaceType.GRAPH );
        this.namespaceId = namespaceId;
        this.fullGraph = true;
    }

}
