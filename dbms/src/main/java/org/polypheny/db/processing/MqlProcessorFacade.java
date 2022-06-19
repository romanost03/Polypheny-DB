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

package org.polypheny.db.processing;

import org.polypheny.db.PolyResult;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.languages.QueryParameters;
import org.polypheny.db.languages.mql.Mql;
import org.polypheny.db.languages.mql.MqlNode;
import org.polypheny.db.languages.mql.MqlQueryParameters;
import org.polypheny.db.languages.polyscript.MqlExpression;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;

public class MqlProcessorFacade {
    private final MqlProcessorImpl mqlProcessor;

    public MqlProcessorFacade() {
        mqlProcessor = new MqlProcessorImpl();
    }

    public PolyResult process(MqlExpression line, Transaction transaction) {
        Statement statement = transaction.createStatement();
        QueryParameters parameters = new MqlQueryParameters(line.getValue(), transaction.getSchema().getName(), Catalog.SchemaType.DOCUMENT);
        MqlNode parsed = (MqlNode) mqlProcessor.parse(line.getValue());
        if (isDml(parameters, parsed)) {
            mqlProcessor.autoGenerateDDL(
                    statement,
                    parsed,
                    parameters);
        }

        return getPolyResult(statement, parameters, parsed);
    }

    private PolyResult getPolyResult(Statement statement, QueryParameters parameters, MqlNode parsed) {
        PolyResult polyResult;
        if (isDdl(parsed)) {
            polyResult = mqlProcessor.prepareDdl(statement, parsed, parameters);
        } else {
            AlgRoot logicalRoot = mqlProcessor.translate(statement, parsed, parameters);
            polyResult = statement.getQueryProcessor().prepareQuery(logicalRoot, false);
        }
        return polyResult;
    }

    private boolean isDdl(MqlNode parsed) {
        return parsed.getFamily() == Mql.Family.DDL;
    }

    private boolean isDml(QueryParameters parameters, MqlNode parsed) {
        return parsed.getFamily() == Mql.Family.DML && mqlProcessor.needsDdlGeneration(parsed, parameters);
    }
}
